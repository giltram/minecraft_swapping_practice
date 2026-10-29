package dev.hotbarswaptrainer.session;

import dev.hotbarswaptrainer.environment.StationaryEnvironment;
import dev.hotbarswaptrainer.input.PlayerActionTracker;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.stats.TaskResult;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Mutable only on the owning integrated-server thread. */
final class TrainingSession {
    enum Phase { BACKING_UP, PREPARING, COUNTDOWN, GO, RUNNING, SUCCESS, INVALID, RESTORING, ERROR_LOCKED }
    final UUID id = UUID.randomUUID();
    final LoadoutProfile profile;
    final TrainingMode mode;
    final int goal;
    final StationaryEnvironment environment;
    final Random random = new Random();
    final List<TaskResult> results = new ArrayList<>();
    InventoryTask task;
    PlayerActionTracker actions = new PlayerActionTracker();
    Phase phase = Phase.BACKING_UP;
    long startedNanos, elapsedNanos, revision;
    int deadlineTick, completed;
    String message = "";

    TrainingSession(ServerPlayerEntity player, LoadoutProfile profile, TrainingMode mode, int goal) {
        this.profile = profile;
        this.mode = mode;
        this.goal = goal;
        this.environment = new StationaryEnvironment(player);
    }
}
