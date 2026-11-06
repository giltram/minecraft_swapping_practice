package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.arena.*;
import dev.hotbarswaptrainer.client.ArenaUiSmokeAccess;
import dev.hotbarswaptrainer.environment.TrainingEnvironment;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual independent-process arena lifecycle. Never copies/rebinds a recovery record or an old world. */
final class ArenaAcceptance {
    private String mode;
    private Path run, output;
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private volatile boolean failed, testsDone, resetRequested, resetObserved, resetDone, cutIssued;
    private volatile InventorySnapshot baseline;
    private volatile String originalIdentity;
    private volatile int phase;
    private long started;
    private int ticksAfterReady;
    private int outsideBefore;
    private volatile boolean saveFailureObserved, permissionsRestored, retryIssued;
    private boolean exitRetryIssued;
    private java.util.Set<java.nio.file.attribute.PosixFilePermission> dataPermissions;

    void initialize(String mode) {
        this.mode = mode;
        if (mode.equals("accept-arena-safety")) ArenaExitRetryFault.enable();
        run = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();
        output = run.resolve("smoke"); started = System.nanoTime();
        try {
            AcceptanceSupport.require(run.getFileName().toString().startsWith("run-arena-") && run.toRealPath().equals(run)
                    && new String(Files.readAllBytes(run.resolve(".hst-production-owned")), StandardCharsets.UTF_8).trim()
                    .equals("Hotbar Swap Trainer isolated production smoke instance v1"), "Unowned arena test instance");
            Files.createDirectories(output);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(this::serverTick);
    }

    private void clientTick(MinecraftClient client) {
        if (failed || cutIssued) return;
        client.options.pauseOnLostFocus = false;
        try {
            if (System.nanoTime() - started > 180_000_000_000L) throw new IllegalStateException("Arena acceptance timed out, phase=" + phase + ", status=" + ArenaUiSmokeAccess.status().state);
            if (phase == 0 && client.currentScreen instanceof TitleScreen) {
                if (mode.equals("accept-arena-cut-create")) {
                    ArenaLaunch.prepare(run.resolve("saves")); halt("ownership persisted before vanilla creation"); return;
                }
                ArenaUiSmokeAccess.enterViaTitleButton(); phase = 1;
            } else if (phase == 1 && client.player != null) {
                ArenaStatus status = ArenaUiSmokeAccess.status();
                if (status.state == ArenaStatus.State.ERROR) throw new IllegalStateException("Arena error: " + status.problem);
                if (status.state == ArenaStatus.State.READY && client.currentScreen == null) {
                    phase = 2;
                }
            } else if (phase == 2 && testsDone) {
                ArenaUiSmokeAccess.reset(); resetRequested = true; phase = 3;
            } else if (phase == 3) {
                ArenaStatus status = ArenaUiSmokeAccess.status();
                if (status.state == ArenaStatus.State.ERROR) {
                    if (!mode.equals("accept-arena-safety") || status.problem != ArenaStatus.Problem.SAVE)
                        throw new IllegalStateException("Reset failed: " + status.problem);
                    saveFailureObserved = true;
                    if (permissionsRestored && !retryIssued) { retryIssued = true; ArenaUiSmokeAccess.retry(); }
                }
                if (resetObserved && status.state == ArenaStatus.State.READY && client.currentScreen == null) { resetDone = true; phase = 4; }
            } else if (phase == 5 && mode.equals("accept-arena-safety") && !exitRetryIssued
                    && ArenaExitRetryFault.observed() && ArenaUiSmokeAccess.status().state == ArenaStatus.State.ERROR) {
                AcceptanceSupport.require(ArenaUiSmokeAccess.status().problem == ArenaStatus.Problem.SAVE, "Wrong final-exit failure state");
                exitRetryIssued = true; ArenaUiSmokeAccess.retry();
            } else if (phase == 5 && client.world == null && client.getServer() == null && client.currentScreen instanceof TitleScreen) {
                checks.add(AcceptanceSupport.check("real exit", "Client cleared world/server after confirmed restore, save and server thread termination"));
                ArenaUiSmokeAccess.enterViaTitleButton(); phase = 6;
            } else if (phase == 6 && client.player != null && ArenaUiSmokeAccess.status().state == ArenaStatus.State.READY && client.currentScreen == null) {
                phase = 7;
            } else if (phase == 8) {
                ArenaUiSmokeAccess.exit(); phase = 9;
            } else if (phase == 9 && client.world == null && client.getServer() == null && client.currentScreen instanceof TitleScreen) {
                AcceptanceSupport.json(output.resolve("arena-result.json"), result("passed", "Create/recover, reset, real exit and same-path reopen completed"));
                phase = 10; client.scheduleStop();
            }
        } catch (Exception failure) { fail(failure); }
    }

    private void serverTick(MinecraftServer server) {
        if (failed || cutIssued || server.getPlayerManager().getPlayerList().isEmpty()) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
        try {
            Path world = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            AcceptanceSupport.require(world.equals(run.resolve("saves").resolve(ArenaJournal.DIRECTORY)), "Unexpected test save");
            ArenaJournal journal = new ArenaJournal(world); ArenaJournal.Record record = journal.read();
            if (mode.equals("accept-arena-cut-build") && record.phase == ArenaJournal.Phase.INITIALIZING) { halt("initialization interrupted after a server batch"); return; }
            if (resetRequested && record.phase == ArenaJournal.Phase.RESETTING) {
                resetObserved = true;
                if (mode.equals("accept-arena-cut-reset")) { halt("reset interrupted after its write-ahead journal and server batch"); return; }
            }
            if (saveFailureObserved && !permissionsRestored) {
                AcceptanceSupport.require(record.phase == ArenaJournal.Phase.VERIFYING && !InventoryService.isProtected(player), "Arena save failure contaminated inventory recovery or committed READY");
                AcceptanceSupport.require(baseline.equals(InventorySnapshot.capture(player.inventory)), "Arena failure changed restored inventory");
                Files.setPosixFilePermissions(world.resolve("data"), dataPermissions);
                checks.add(AcceptanceSupport.check("arena save failure", "Real directory write denial; journal remained VERIFYING, no inventory lock; permissions restored before idempotent retry"));
                permissionsRestored = true;
            }
            if (phase == 2 && !testsDone && player.isOnGround() && ++ticksAfterReady > 5) {
                originalIdentity = record.id;
                outsideBefore = outsideHash(server);
                Path baselineFile = output.resolve("arena-baseline.nbt");
                if (Files.exists(baselineFile)) {
                    CompoundTag saved = AcceptanceSupport.read(baselineFile);
                    baseline = InventorySnapshot.fromTag(saved);
                    AcceptanceSupport.restored(player, baseline);
                    checks.add(AcceptanceSupport.check("fresh-process recovery", "Both saved players match the same-path 41-slot baseline"));
                } else {
                    seed(player); baseline = InventorySnapshot.capture(player.inventory);
                    new ConfirmedPlayerSave().save(player);
                    CompoundTag saved = baseline.toTag(); saved.putUuid("Player", player.getUuid());
                    AcceptanceSupport.write(baselineFile, saved);
                }
                for (TrainingEnvironment environment : new TrainingEnvironment[] {TrainingEnvironment.STATIONARY, TrainingEnvironment.MOVING}) for (TrainingMode mode : TrainingMode.values()) {
                    TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(mode, "default", 0, false, environment));
                    AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("PREPARING"), "Arena START refused " + mode + "/" + environment);
                    AcceptanceSupport.signal(player, TrainerProtocol.Action.READY);
                    AcceptanceSupport.set(AcceptanceSupport.session(player), "deadlineTick", server.getTicks() - 1);
                    TrainerMod.SESSIONS.tick(server); AcceptanceSupport.signal(player, TrainerProtocol.Action.GO_ACK);
                    AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("RUNNING"), "Did not reach RUNNING");
                    AcceptanceSupport.solve(player);
                    AcceptanceSupport.require(AcceptanceSupport.phase(player).equals("SUCCESS"), "Stationary solution rejected in " + environment);
                    AcceptanceSupport.require(TrainerMod.SESSIONS.restoreBeforeWorldOperation(player), "Cannot restore before arena operation");
                    AcceptanceSupport.restored(player, baseline);
                    checks.add(AcceptanceSupport.check(mode + "/" + environment, "Actual START/READY/GO_ACK/swaps/stop; exact original inventory read back from both files"));
                }
                if (mode.equals("accept-arena-safety")) ArenaRecoveryChecks.run(player, baseline, checks);
                if (mode.equals("accept-arena-cut-training")) {
                    TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.FULL_INVENTORY, "default", 0, false, TrainingEnvironment.MOVING));
                    AcceptanceSupport.require(InventoryService.isProtected(player), "Crash needs an active same-path transaction");
                    halt("active same-path inventory transaction before world reset/exit"); return;
                }
                // Mutate only a known owned coordinate, then require reset to remove it.
                server.getOverworld().setBlockState(new BlockPos(4, 4, 4), net.minecraft.block.Blocks.CHEST.getDefaultState());
                net.minecraft.block.entity.ChestBlockEntity chest = (net.minecraft.block.entity.ChestBlockEntity) server.getOverworld().getBlockEntity(new BlockPos(4, 4, 4));
                chest.setStack(0, new ItemStack(Items.DIAMOND, 3)); chest.markDirty();
                new ConfirmedPlayerSave().save(player);
                TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.FULL_INVENTORY, "default", 0, false, TrainingEnvironment.MOVING));
                AcceptanceSupport.require(InventoryService.isProtected(player), "Reset must start with an active training transaction");
                if (mode.equals("accept-arena-safety")) {
                    Path data = world.resolve("data"); dataPermissions = Files.getPosixFilePermissions(data);
                    java.util.Set<java.nio.file.attribute.PosixFilePermission> denied = java.util.EnumSet.copyOf(dataPermissions);
                    denied.remove(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                    denied.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
                    denied.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
                    Files.setPosixFilePermissions(data, denied);
                }
                testsDone = true;
            }
            if (phase == 4 && resetDone && player.isOnGround()) {
                AcceptanceSupport.require(record.id.equals(originalIdentity) && record.phase == ArenaJournal.Phase.READY, "Reset changed identity or failed to commit");
                AcceptanceSupport.require(server.getOverworld().getBlockState(new BlockPos(4, 4, 4)).isAir(), "Reset did not clear the owned obstacle");
                AcceptanceSupport.require(server.getOverworld().getBlockEntity(new BlockPos(4, 4, 4)) == null, "Reset retained the discarded container");
                AcceptanceSupport.require(server.getOverworld().getEntities(null, entity -> entity instanceof net.minecraft.entity.ItemEntity
                        && ((net.minecraft.entity.ItemEntity) entity).getStack().getItem() == Items.DIAMOND).isEmpty(), "Reset dropped discarded container contents");
                AcceptanceSupport.restored(player, baseline);
                AcceptanceSupport.require(outsideHash(server) == outsideBefore, "Reset modified an existing chunk outside its owned bounds");
                checks.add(AcceptanceSupport.check("reset", "Same world identity, obstacle removed, inventory and both saved players unchanged"));
                resetDone = false; // Let client advance only after server-side verification.
                TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(TrainingMode.FULL_INVENTORY, "default", 0, false, TrainingEnvironment.MOVING));
                AcceptanceSupport.require(InventoryService.isProtected(player), "Exit must start with an active training transaction");
                MinecraftClient.getInstance().execute(() -> { ArenaUiSmokeAccess.exitViaPauseMenu(); phase = 5; });
            }
            if (phase == 7) {
                AcceptanceSupport.require(record.id.equals(originalIdentity), "Reopen created a different world");
                AcceptanceSupport.restored(player, baseline);
                checks.add(AcceptanceSupport.check("same-path reuse", "Persistent identity and both original inventories survived real exit/reopen"));
                if (mode.equals("accept-arena-safety")) {
                    AcceptanceSupport.require(ArenaExitRetryFault.observed() && exitRetryIssued, "Final-exit save retry was not exercised");
                    checks.add(AcceptanceSupport.check("final-exit save retry", "Excluded simulated I/O failure kept exit proof/intent; one Retry repeated exit acknowledgment, preserved inventory and really reopened"));
                }
                phase = 8;
            }
        } catch (Exception failure) { fail(failure); }
    }

    static void seed(ServerPlayerEntity player) {
        for (int i = 0; i < player.inventory.main.size(); i++) {
            ItemStack stack = new ItemStack(Items.COBBLESTONE, i + 1);
            CompoundTag tag = new CompoundTag(); tag.putInt("ArenaBaselineSlot", i); stack.setTag(tag); player.inventory.main.set(i, stack);
        }
        for (int i = 0; i < 4; i++) player.inventory.armor.set(i, new ItemStack(Items.LEATHER_BOOTS));
        player.inventory.offHand.set(0, new ItemStack(Items.SHIELD)); player.inventory.selectedSlot = 6;
        InventoryService.sync(player);
    }
    private static int outsideHash(MinecraftServer server) {
        net.minecraft.world.chunk.WorldChunk chunk = server.getOverworld().getChunkManager().getWorldChunk(3, 3);
        AcceptanceSupport.require(chunk != null, "Outside observation chunk must already be loaded; never generate unknown terrain");
        int hash = 1; BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = 0; y < 256; y++) for (int z = 48; z < 64; z++) for (int x = 48; x < 64; x++)
            hash = 31 * hash + net.minecraft.block.Block.getRawIdFromState(chunk.getBlockState(pos.set(x, y, z)));
        return hash;
    }
    private Map<String, Object> result(String status, String detail) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(); result.put("status", status); result.put("mode", mode);
        result.put("detail", detail); result.put("checks", checks); return result;
    }
    private void halt(String boundary) throws Exception {
        cutIssued = true; AcceptanceSupport.json(output.resolve("arena-cut.json"), result("ready-to-halt91", boundary));
        Runtime.getRuntime().halt(91);
    }
    private void fail(Exception failure) {
        failed = true;
        if (dataPermissions != null) {
            try { Files.setPosixFilePermissions(run.resolve("saves").resolve(ArenaJournal.DIRECTORY).resolve("data"), dataPermissions); }
            catch (Exception permissionFailure) { failure.addSuppressed(permissionFailure); }
        }
        try { AcceptanceSupport.json(output.resolve("arena-result.json"), result("failed", failure.toString())); }
        catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        org.apache.logging.log4j.LogManager.getLogger("HotbarSwapTrainer").error("Arena acceptance failed; keeping owned evidence", failure);
        MinecraftClient.getInstance().execute(MinecraftClient.getInstance()::scheduleStop);
    }
}
