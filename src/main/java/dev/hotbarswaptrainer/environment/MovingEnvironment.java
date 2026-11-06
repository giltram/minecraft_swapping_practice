package dev.hotbarswaptrainer.environment;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Vanilla owns locomotion in every phase. Observations never invalidate or delay a task. */
public final class MovingEnvironment implements SessionEnvironment {
    private final ServerWorld world;
    private final HorizontalMovementObservation movement = new HorizontalMovementObservation();

    public MovingEnvironment(ServerPlayerEntity player) { world = player.getServerWorld(); }

    @Override public void tick(ServerPlayerEntity player) {
        if (player.getServerWorld() != world) throw new IllegalStateException("Training dimension changed unexpectedly");
    }

    @Override public boolean interceptMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet) { return false; }

    @Override public void acceptedMovement(double beforeX, double beforeZ, double afterX, double afterZ) {
        movement.accepted(beforeX, beforeZ, afterX, afterZ);
    }

    @Override public void positionCorrected() { movement.reset(); }

    public double lastHorizontalDistance() { return movement.distance(); }
}
