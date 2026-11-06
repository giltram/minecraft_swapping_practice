package dev.hotbarswaptrainer.environment;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-thread environment boundary; task outcomes and inventory recovery belong to session. */
public interface SessionEnvironment {
    void tick(ServerPlayerEntity player);
    boolean interceptMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet);
    default void acceptedMovement(double beforeX, double beforeZ, double afterX, double afterZ) { }
    default void positionCorrected() { }
    default void teleportConfirmed() { }
    default void initialize(ServerPlayerEntity player) { }
    default boolean begin(ServerPlayerEntity player, long now) { return true; }
    default void settle(ServerPlayerEntity player) { }
    default dev.hotbarswaptrainer.course.CourseAttempt courseAttempt() { return null; }
    default void acceptedMovement(ServerPlayerEntity player, double beforeX, double beforeZ) {
        acceptedMovement(beforeX, beforeZ, player.getX(), player.getZ());
    }

    static SessionEnvironment create(TrainingEnvironment type, ServerPlayerEntity player) {
        if (type == TrainingEnvironment.PARKOUR) return new ParkourEnvironment(player);
        return type == TrainingEnvironment.MOVING ? new MovingEnvironment(player) : new StationaryEnvironment(player);
    }
}
