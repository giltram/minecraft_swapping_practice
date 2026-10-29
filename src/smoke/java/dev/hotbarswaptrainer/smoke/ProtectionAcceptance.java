package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/** Actual transformed vanilla handlers/callbacks, synchronously on the server thread. */
final class ProtectionAcceptance {
    private ProtectionAcceptance() { }
    private interface Action { void run() throws Exception; }

    static String run(ServerPlayerEntity player, InventorySnapshot baseline, Path output) throws Exception {
        List<Map<String, Object>> checks = new ArrayList<>();
        BlockPos ground = player.getBlockPos().down();
        Object groundState = player.world.getBlockState(ground);
        Map<String, Action> forbidden = new LinkedHashMap<>();
        forbidden.put("inventory pickup/crafting slot", () -> player.networkHandler.onClickWindow(new ClickWindowC2SPacket(
                player.playerScreenHandler.syncId, 36, 0, SlotActionType.PICKUP, player.inventory.main.get(0), (short) 1)));
        forbidden.put("container quick transfer", () -> player.networkHandler.onClickWindow(new ClickWindowC2SPacket(
                player.playerScreenHandler.syncId, 36, 0, SlotActionType.QUICK_MOVE, player.inventory.main.get(0), (short) 2)));
        forbidden.put("creative inventory insertion", () -> player.networkHandler.onCreativeInventoryAction(new CreativeInventoryActionC2SPacket(36, new ItemStack(Items.DIAMOND))));
        forbidden.put("pick inventory", () -> player.networkHandler.onPickFromInventory(new PickFromInventoryC2SPacket(0)));
        forbidden.put("craft recipe request", () -> player.networkHandler.onCraftRequest(new CraftRequestC2SPacket()));
        forbidden.put("screen button", () -> player.networkHandler.onButtonClick(new ButtonClickC2SPacket(0, 0)));
        forbidden.put("book update", () -> player.networkHandler.onBookUpdate(new BookUpdateC2SPacket(new ItemStack(Items.WRITABLE_BOOK), false, Hand.MAIN_HAND)));
        forbidden.put("use item", () -> player.networkHandler.onPlayerInteractItem(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND)));
        forbidden.put("entity attack", () -> player.networkHandler.onPlayerInteractEntity(new PlayerInteractEntityC2SPacket(player, false)));
        forbidden.put("entity interaction", () -> player.networkHandler.onPlayerInteractEntity(new PlayerInteractEntityC2SPacket(player, Hand.MAIN_HAND, false)));
        forbidden.put("block use/place", () -> player.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(ground), Direction.UP, ground, false))));
        forbidden.put("command whitespace bypass", () -> player.networkHandler.onGameMessage(new ChatMessageC2SPacket("  /give @s diamond 1")));
        for (PlayerActionC2SPacket.Action action : new PlayerActionC2SPacket.Action[] {
                PlayerActionC2SPacket.Action.DROP_ITEM, PlayerActionC2SPacket.Action.DROP_ALL_ITEMS,
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, PlayerActionC2SPacket.Action.RELEASE_USE_ITEM}) {
            forbidden.put(action.name(), () -> player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(action, ground, Direction.UP)));
        }
        for (Map.Entry<String, Action> entry : forbidden.entrySet()) {
            AcceptanceSupport.startAt(player, "RUNNING");
            InventorySnapshot before = InventorySnapshot.capture(player.inventory);
            entry.getValue().run();
            AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("INVALID"), "Forbidden handler did not invalidate: " + entry.getKey());
            AcceptanceSupport.require(before.equals(InventorySnapshot.capture(player.inventory)), "Forbidden action changed inventory: " + entry.getKey());
            AcceptanceSupport.require(player.world.getBlockState(ground).equals(groundState), "Forbidden action changed support terrain");
            Object s = AcceptanceSupport.session(player);
            AcceptanceSupport.require(((Integer) AcceptanceSupport.field(s, "completed")) == 0, "Forbidden action scored success");
            AcceptanceSupport.require(((List<?>) AcceptanceSupport.field(s, "results")).size() == 1, "Invalid attempt was not frozen once");
            TrainerMod.SESSIONS.stop(player, "Acceptance forbidden action complete");
            AcceptanceSupport.restored(player, baseline);
            checks.add(AcceptanceSupport.check(entry.getKey(), "Actual handler invalidated, preserved inventory/terrain, zero successes and exact restore"));
            AcceptanceSupport.json(output.resolve("protection-checks.json"), checks);
        }
        for (String phase : Arrays.asList("PREPARING", "COUNTDOWN", "RUNNING", "SUCCESS")) {
            AcceptanceSupport.startAt(player, phase);
            passiveProtection(player, checks, phase);
            TrainerMod.SESSIONS.stop(player, "Acceptance passive protection complete");
            AcceptanceSupport.restored(player, baseline);
            AcceptanceSupport.json(output.resolve("protection-checks.json"), checks);
        }
        for (Path directory : Arrays.asList(player.getServer().getSavePath(WorldSavePath.PLAYERDATA), player.getServer().getSavePath(WorldSavePath.ROOT))) {
            startFailure(player, baseline, directory, checks);
            stopFailure(player, baseline, directory, checks);
        }
        ordinaryBehavior(player, baseline, checks);
        AcceptanceSupport.json(output.resolve("protection-checks.json"), checks);
        return checks.size() + " protection/lifecycle checks passed; actual vanilla handlers, passive callbacks, START/stop write failures/retries and ordinary behavior";
    }

    private static void passiveProtection(ServerPlayerEntity player, List<Map<String, Object>> checks, String phase) throws Exception {
        InventorySnapshot before = InventorySnapshot.capture(player.inventory);
        ItemEntity item = new ItemEntity(player.world, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EMERALD));
        item.resetPickupDelay();
        item.onPlayerCollision(player);
        ExperienceOrbEntity orb = new ExperienceOrbEntity(player.world, player.getX(), player.getY(), player.getZ(), 7);
        int xp = player.totalExperience;
        orb.onPlayerCollision(player);
        ArrowEntity arrow = new ArrowEntity(player.world, player);
        arrow.setNoClip(true); arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        arrow.onPlayerCollision(player);
        TridentEntity trident = new TridentEntity(player.world, player, new ItemStack(Items.TRIDENT));
        trident.setNoClip(true); trident.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        trident.onPlayerCollision(player);
        AcceptanceSupport.require(!item.removed && !orb.removed && !arrow.removed && !trident.removed, "Protected pickup removed an entity");
        AcceptanceSupport.require(player.totalExperience == xp, "Protected XP changed");
        AcceptanceSupport.require(before.equals(InventorySnapshot.capture(player.inventory)), "Protected pickup changed inventory");
        float health = player.getHealth();
        AcceptanceSupport.require(!player.damage(DamageSource.OUT_OF_WORLD, 1000f) && player.isAlive(), "Protected lethal damage/death applied");
        player.setHealth(10f);
        player.heal(5f);
        AcceptanceSupport.require(player.getHealth() == 10f, "Protected heal applied");
        player.setHealth(health);
        net.minecraft.nbt.CompoundTag hungerBefore = new net.minecraft.nbt.CompoundTag();
        player.getHungerManager().toTag(hungerBefore);
        player.addExhaustion(100f);
        player.getHungerManager().update(player);
        net.minecraft.nbt.CompoundTag hungerAfter = new net.minecraft.nbt.CompoundTag();
        player.getHungerManager().toTag(hungerAfter);
        AcceptanceSupport.require(hungerBefore.equals(hungerAfter), "Protected food/saturation/exhaustion changed");
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 0));
        int duration = player.getStatusEffect(StatusEffects.POISON).getDuration();
        player.playerTick(); // ServerPlayerEntity.tick() does not call LivingEntity's status tick.
        AcceptanceSupport.require(player.getStatusEffect(StatusEffects.POISON).getDuration() == duration, "Protected effect ticked");
        // Match the normal server-tick ordering: physics may update onGround;
        // the stationary environment validates support and reasserts the anchor.
        TrainerMod.SESSIONS.tick(player.getServer());
        AcceptanceSupport.require(AcceptanceSupport.phase(player).equals(phase), "Passive tick changed the task phase");
        player.removeStatusEffect(StatusEffects.POISON);
        AcceptanceSupport.require(!player.canPickUp(new ItemStack(Items.IRON_HELMET)), "Protected dispenser equipment allowed");
        Vec3d anchor = player.getPos();
        player.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.Both(anchor.x + 3, anchor.y, anchor.z, 27f, 12f, true));
        AcceptanceSupport.require(player.getPos().squaredDistanceTo(anchor) < 0.000001 && player.yaw == 27f && player.pitch == 12f,
                "Movement did not retain anchor/allow rotation");
        AcceptanceSupport.require(before.equals(InventorySnapshot.capture(player.inventory)), "Passive checks changed inventory");
        checks.add(AcceptanceSupport.check("passive protection at " + phase,
                "Item/XP/arrow/trident, lethal damage/heal/food/effects/equipment and anchored movement with camera rotation"));
    }

    private static void stopFailure(ServerPlayerEntity player, InventorySnapshot baseline, Path directory,
                                    List<Map<String, Object>> checks) throws Exception {
        AcceptanceSupport.startAt(player, "RUNNING");
        new ConfirmedPlayerSave().save(player);
        net.minecraft.nbt.CompoundTag raw = ((RecoveryHolder) player).trainer$getRecovery();
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(directory);
        Set<PosixFilePermission> denied = EnumSet.copyOf(original);
        denied.remove(PosixFilePermission.OWNER_WRITE); denied.remove(PosixFilePermission.GROUP_WRITE); denied.remove(PosixFilePermission.OTHERS_WRITE);
        try {
            Files.setPosixFilePermissions(directory, denied);
            AcceptanceSupport.require(!Files.isWritable(directory), "Permission denial did not apply");
            TrainerMod.SESSIONS.stop(player, "Acceptance real stop write denial");
            AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("ERROR_LOCKED"), "Stop did not lock after write denial");
            AcceptanceSupport.require(raw.equals(((RecoveryHolder) player).trainer$getRecovery()), "Failed stop changed backup");
            AcceptanceSupport.require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Stop replacement differs");
            AcceptanceSupport.require(!TrainerMod.SESSIONS.beforeSwap(player), "Failed stop allowed swapping");
        } finally { Files.setPosixFilePermissions(directory, original); }
        TrainerMod.SESSIONS.stop(player, "Acceptance stop retry after restored permissions");
        AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("actual SessionManager.stop I/O failure: " + directory.getFileName(),
                "ERROR_LOCKED retained exact backup, blocked swaps, permissions reset and stop retry committed both saves"));
    }

    private static void startFailure(ServerPlayerEntity player, InventorySnapshot baseline, Path directory,
                                     List<Map<String, Object>> checks) throws Exception {
        String outcome;
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(directory);
        Set<PosixFilePermission> denied = EnumSet.copyOf(original);
        denied.remove(PosixFilePermission.OWNER_WRITE); denied.remove(PosixFilePermission.GROUP_WRITE); denied.remove(PosixFilePermission.OTHERS_WRITE);
        try {
            Files.setPosixFilePermissions(directory, denied);
            AcceptanceSupport.require(!Files.isWritable(directory), "Permission denial did not apply");
            long stopsBefore = SaveWriteFault.stopEntries();
            TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(
                    dev.hotbarswaptrainer.task.TrainingMode.SINGLE_ITEM, "default", 0));
            AcceptanceSupport.require(SaveWriteFault.stopEntries() == stopsBefore + 1,
                    "START did not reach the backup failure/automatic restore path");
            AcceptanceSupport.require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Failed backup inserted training inventory");
            if (InventoryService.isProtected(player)) {
                AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("ERROR_LOCKED"), "Failed start left an unguarded session phase");
                net.minecraft.nbt.CompoundTag raw = ((RecoveryHolder) player).trainer$getRecovery();
                RecoveryRecord.fromTag(raw, player.getUuid(), InventoryService.worldIdentity(player));
                AcceptanceSupport.require(!TrainerMod.SESSIONS.beforeSwap(player), "Failed start allowed swapping");
                outcome = "ERROR_LOCKED retained a valid backup";
            } else {
                // A failed backup may roll back without writing this path when its old
                // bytes already equal the original inventory and cleared record.
                AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("IDLE"), "Failed start retained a live session");
                AcceptanceSupport.verifyDisk(player, baseline, null);
                outcome = "Automatic rollback confirmed the original/cleared state in both saved files";
            }
        } finally { Files.setPosixFilePermissions(directory, original); }
        TrainerMod.SESSIONS.stop(player, "Acceptance failed-start retry after restored permissions");
        AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("actual START I/O failure: " + directory.getFileName(),
                outcome + "; baseline unchanged, permissions reset and final restoration confirmed"));
    }

    private static void ordinaryBehavior(ServerPlayerEntity player, InventorySnapshot baseline, List<Map<String, Object>> checks) throws Exception {
        player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        AcceptanceSupport.require(!baseline.equals(InventorySnapshot.capture(player.inventory)), "Ordinary vanilla swap remained blocked");
        baseline.restore(player.inventory);
        player.inventory.main.set(35, ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(player.world, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EMERALD));
        item.resetPickupDelay(); item.onPlayerCollision(player);
        AcceptanceSupport.require(item.removed, "Ordinary item pickup remained blocked");
        player.inventory.main.set(35, ItemStack.EMPTY);
        ArrowEntity arrow = new ArrowEntity(player.world, player);
        arrow.setNoClip(true); arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        arrow.onPlayerCollision(player);
        AcceptanceSupport.require(arrow.removed, "Ordinary arrow pickup remained blocked");
        player.inventory.main.set(35, ItemStack.EMPTY);
        TridentEntity trident = new TridentEntity(player.world, player, new ItemStack(Items.TRIDENT));
        trident.setNoClip(true); trident.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        trident.onPlayerCollision(player);
        AcceptanceSupport.require(trident.removed, "Ordinary trident pickup remained blocked");
        player.experiencePickUpDelay = 0;
        ExperienceOrbEntity orb = new ExperienceOrbEntity(player.world, player.getX(), player.getY(), player.getZ(), 7);
        orb.onPlayerCollision(player);
        AcceptanceSupport.require(orb.removed, "Ordinary XP pickup remained blocked");
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 199, 0));
        player.playerTick();
        AcceptanceSupport.require(player.getStatusEffect(StatusEffects.POISON).getDuration() == 198, "Ordinary status tick remained blocked");
        player.removeStatusEffect(StatusEffects.POISON);
        float health = player.getHealth(); player.setHealth(10f); player.heal(4f);
        AcceptanceSupport.require(player.getHealth() == 14f, "Ordinary healing remained blocked");
        player.setHealth(health);
        baseline.restore(player.inventory);
        InventoryService.sync(player);
        new ConfirmedPlayerSave().save(player);
        AcceptanceSupport.restored(player, baseline);
        checks.add(AcceptanceSupport.check("ordinary behavior after restoration", "Vanilla swap, item/XP/arrow/trident pickup, status ticks and healing work after guards release; exact baseline re-saved"));
    }
}
