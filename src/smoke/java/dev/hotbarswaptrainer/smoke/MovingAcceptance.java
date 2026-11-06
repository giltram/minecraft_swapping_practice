package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.environment.*;
import dev.hotbarswaptrainer.input.PlayerActionTracker;
import dev.hotbarswaptrainer.inventory.*;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.stats.*;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.nio.file.Path;
import java.util.*;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Transformed vanilla hooks in the existing owned terrain; no blocks/worlds are generated here. */
final class MovingAcceptance {
    private MovingAcceptance() { }

    static String run(ServerPlayerEntity player, InventorySnapshot baseline, Path output) throws Exception {
        Vec3d origin = player.getPos();
        Object support = player.world.getBlockState(player.getBlockPos().down());
        List<Map<String, Object>> checks = new ArrayList<>();
        for (TrainingEnvironment environment : new TrainingEnvironment[] {TrainingEnvironment.STATIONARY, TrainingEnvironment.MOVING}) for (TrainingMode mode : TrainingMode.values()) {
            resetPosition(player, origin);
            start(player, mode, environment, 2);
            TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(mode, "default", 0, false,
                    environment == TrainingEnvironment.MOVING ? TrainingEnvironment.STATIONARY : TrainingEnvironment.MOVING));
            require(field(session(player), "environmentType") == environment, "START mutated the active environment");
            require((Integer) field(session(player), "goal") == 2, "START mutated the active goal");
            for (int task = 0; task < 2; task++) {
                if (environment == TrainingEnvironment.MOVING) move(player, 0.1, 0, 0, true);
                running(player);
                if (environment == TrainingEnvironment.MOVING) {
                    move(player, 0.15, 0.2, 0, false);
                    require(moving(player).lastHorizontalDistance() > 0, "Accepted horizontal jump not observed");
                }
                solve(player); require(phase(player).equals("SUCCESS"), "Vanilla solution did not succeed");
                require(((List<?>) field(session(player), "results")).size() == task + 1, "Success did not freeze once");
                next(player);
                if (task == 0) require(phase(player).equals("PREPARING"), "Airborne replacement did not prepare");
            }
            restored(player, baseline);
            SessionResult result = latest(player);
            require(result.getSuccessCount() == 2 && result.getMetadata().endState == SessionMetadata.EndState.COMPLETED,
                    "Finite completion/history differs");
            require(result.getMetadata().environment.equals(environment.historyId), "History environment differs");
            checks.add(check(environment + "-" + mode + "-finite",
                    "Two actual vanilla solutions, frozen environment/goal, airborne replacement for Moving, exact two-file restore/history"));
        }

        resetPosition(player, origin); start(player, TrainingMode.SINGLE_ITEM, TrainingEnvironment.STATIONARY, 0);
        Vec3d anchor = player.getPos();
        move(player, 0.2, 0.1, 0, false);
        require(player.getPos().squaredDistanceTo(anchor) < 1e-10, "Stationary allowed translation");
        confirmTeleport(player);
        player.setVelocity(0.2, 0.3, 0.4); player.fallDistance = 5;
        TrainerMod.SESSIONS.tick(player.getServer());
        require(player.getVelocity().lengthSquared() == 0 && player.fallDistance == 0 && player.isOnGround(), "Stationary tick regressed");
        TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
        checks.add(check("stationary-regression", "Anchor, rotation packet, zero velocity/fall distance, grounded flag and restore"));

        resetPosition(player, origin); start(player, TrainingMode.SINGLE_ITEM, TrainingEnvironment.MOVING, 0); running(player);
        move(player, 0.2, 0, 0, true);
        require(moving(player).lastHorizontalDistance() > 0, "Walking not observed");
        player.networkHandler.onClientCommand(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        move(player, 0.28, 0, 0, true);
        require(player.isSprinting() && moving(player).lastHorizontalDistance() > 0, "Vanilla sprint not observed");
        player.networkHandler.onClientCommand(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        player.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.LookOnly(83f, 12f, true));
        require(player.yaw == 83f && moving(player).lastHorizontalDistance() == 0, "Rotation counted as movement");
        move(player, 0, 0.2, 0, false);
        require(moving(player).lastHorizontalDistance() == 0, "Vertical-only jump counted as horizontal");
        move(player, 0.15, 0, 0.1, false);
        require(moving(player).lastHorizontalDistance() > 0, "Horizontal jump not observed");
        resetPosition(player, origin);
        require(moving(player).lastHorizontalDistance() == 0, "Teleport/confirmation contributed movement");
        move(player, 0.1, -1, 0, true); // Reject penetration into the already existing support; never build a wall.
        require(moving(player).lastHorizontalDistance() == 0, "Rejected collision contributed movement");
        confirmTeleport(player);
        require(phase(player).equals("RUNNING"), "Stop/correction invalidated relaxed Moving");
        PlayerActionTracker actions = (PlayerActionTracker) field(session(player), "actions");
        require(actions.getAttempts() == 0 && actions.getSelections() == 0 && actions.getForbidden() == 0,
                "Movement/setup/correction polluted input counters");
        Vec3d velocity = new Vec3d(0.03, 0.12, 0.04); player.setVelocity(velocity); player.fallDistance = 7;
        Vec3d position = player.getPos();
        TrainerMod.SESSIONS.tick(player.getServer());
        require(player.getPos().equals(position) && player.getVelocity().equals(velocity) && player.fallDistance == 7,
                "Moving tick changed position, velocity or fall distance");
        for (int i = 0; i < 120; i++) TrainerMod.SESSIONS.tick(player.getServer());
        require(phase(player).equals("RUNNING"), "Repeated stationary checks invalidated Moving");
        solve(player); require(phase(player).equals("SUCCESS"), "Stationary solution needs first movement");
        TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
        checks.add(check("accepted-movement-and-relaxed-stops",
                "Actual accepted walking/jump, rotation/vertical zero, collision rejection, teleport reset, repeated stationary callbacks, untouched velocity and counters"));

        for (TrainingMode mode : TrainingMode.values()) {
            resetPosition(player, origin); start(player, mode, TrainingEnvironment.MOVING, 0);
            running(player); solve(player); next(player); running(player);
            move(player, 0.1, 0.3, 0, false);
            InventorySnapshot training = InventorySnapshot.capture(player.inventory);
            ItemEntity item = new ItemEntity(player.world, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.EMERALD));
            item.resetPickupDelay(); item.onPlayerCollision(player);
            require(!item.removed && training.equals(InventorySnapshot.capture(player.inventory)), "Airborne pickup changed inventory");
            require(!player.damage(DamageSource.OUT_OF_WORLD, 1000f) && player.isAlive(), "Airborne lethal damage applied");
            player.networkHandler.onPlayerInteractItem(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND));
            require(phase(player).equals("INVALID") && training.equals(InventorySnapshot.capture(player.inventory)), "Airborne use guard failed");
            next(player); running(player); // Continue unlimited after invalidation, still airborne.
            player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
            require(phase(player).equals("INVALID"), "Airborne drop not invalidated");
            next(player); running(player);
            training = InventorySnapshot.capture(player.inventory);
            player.networkHandler.onClickWindow(new ClickWindowC2SPacket(player.playerScreenHandler.syncId, 36, 0,
                    SlotActionType.QUICK_MOVE, player.inventory.main.get(0), (short) 1));
            require(phase(player).equals("INVALID") && training.equals(InventorySnapshot.capture(player.inventory)), "Airborne transfer guard failed");
            next(player); running(player);
            BlockPos ground = new BlockPos(origin).down();
            player.networkHandler.onPlayerInteractBlock(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(ground), Direction.UP, ground, false)));
            require(phase(player).equals("INVALID"), "Airborne block-use guard failed");
            position = player.getPos(); velocity = player.getVelocity(); float fall = player.fallDistance;
            TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
            require(player.getPos().equals(position) && player.getVelocity().equals(velocity) && player.fallDistance == fall,
                    "Airborne restore mutated locomotion");
            require(latest(player).getRequestedSuccesses() == 0 && latest(player).getSuccessCount() == 1, "Unlimited result differs");
            checks.add(check(mode + "-unlimited-airborne-protection-stop",
                    "Unlimited replacement/invalid retry; pickup/use/drop/transfer/block-use/lethal damage guards; immediate restore in place with velocity/fall distance intact"));
        }

        for (String at : Arrays.asList("PREPARING", "COUNTDOWN", "GO", "RUNNING", "SUCCESS")) {
            resetPosition(player, origin); start(player, TrainingMode.SINGLE_ITEM, TrainingEnvironment.MOVING, 0);
            if (!at.equals("PREPARING")) {
                signal(player, TrainerProtocol.Action.READY);
                if (!at.equals("COUNTDOWN")) {
                    next(player);
                    if (!at.equals("GO")) {
                        signal(player, TrainerProtocol.Action.GO_ACK);
                        if (at.equals("SUCCESS")) solve(player);
                    }
                }
            }
            require(phase(player).equals(at), "Interruption setup differs");
            signal(player, TrainerProtocol.Action.INTERRUPT);
            require(phase(player).equals(at.equals("PREPARING") || at.equals("SUCCESS") ? at : "INVALID"), "Interruption regression");
            TrainerMod.SESSIONS.stop(player, "Stopped"); restored(player, baseline);
            checks.add(check("interrupt-" + at, "Existing interruption protocol retained; exact inventory restore (not physical focus/pause acceptance)"));
        }
        resetPosition(player, origin);
        require(player.world.getBlockState(player.getBlockPos().down()).equals(support), "Support terrain changed");
        restored(player, baseline);
        json(output.resolve("moving-checks.json"), checks);
        return checks.size() + " M3-A programmatic production checks; physical input/focus/feel remain manual";
    }

    static void prepareDisconnect(ServerPlayerEntity player, Path output) throws Exception {
        confirmTeleport(player); // Complete any login teleport before asking vanilla to accept test movement.
        start(player, TrainingMode.FULL_INVENTORY, TrainingEnvironment.MOVING, 0);
        running(player); move(player, 0.1, 0.3, 0, false);
        require(!player.isOnGround() && moving(player).lastHorizontalDistance() > 0, "Disconnect request is not moving/airborne");
        new ConfirmedPlayerSave().save(player);
        ExitObservation.arm(player, "disconnect", "RUNNING", output);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        client.execute(() -> client.getNetworkHandler().getConnection().disconnect(new net.minecraft.text.LiteralText("M3-A airborne disconnect")));
    }

    private static void start(ServerPlayerEntity player, TrainingMode mode, TrainingEnvironment environment, int goal) throws Exception {
        TrainerMod.SESSIONS.handle(player, TrainerProtocol.Request.start(mode, "default", goal, false, environment));
        require(phase(player).equals("PREPARING"), "START failed: " + phase(player));
    }
    private static void running(ServerPlayerEntity player) throws Exception {
        signal(player, TrainerProtocol.Action.READY); require(phase(player).equals("COUNTDOWN"), "READY failed");
        next(player); require(phase(player).equals("GO"), "GO failed");
        signal(player, TrainerProtocol.Action.GO_ACK); require(phase(player).equals("RUNNING"), "GO_ACK failed");
    }
    private static void next(ServerPlayerEntity player) throws Exception {
        set(session(player), "deadlineTick", player.getServer().getTicks() - 1); TrainerMod.SESSIONS.tick(player.getServer());
    }
    private static MovingEnvironment moving(ServerPlayerEntity player) throws Exception {
        return (MovingEnvironment) field(session(player), "environment");
    }
    private static SessionResult latest(ServerPlayerEntity player) throws Exception {
        return ((ResultStore) field(TrainerMod.SESSIONS, "results")).findLatest(player.getUuid()).get();
    }
    private static void move(ServerPlayerEntity player, double dx, double dy, double dz, boolean onGround) {
        player.networkHandler.onPlayerMove(new PlayerMoveC2SPacket.Both(player.getX() + dx, player.getY() + dy,
                player.getZ() + dz, player.yaw, player.pitch, onGround));
    }
    private static void confirmTeleport(ServerPlayerEntity player) throws Exception {
        // This acceptance mode is production-only: audited 1.16.1 intermediary field names.
        if (field(player.networkHandler, "field_14119") != null) // requestedTeleportPos
            player.networkHandler.onTeleportConfirm(new TeleportConfirmC2SPacket((Integer) field(player.networkHandler, "field_14123"))); // requestedTeleportId
        player.networkHandler.syncWithPlayerPosition();
    }
    private static void resetPosition(ServerPlayerEntity player, Vec3d origin) throws Exception {
        // Excluded driver only: restore the known loaded test spot, never a production movement correction.
        player.networkHandler.requestTeleport(origin.x, origin.y, origin.z, player.yaw, player.pitch);
        confirmTeleport(player); player.setOnGround(true); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
    }
}
