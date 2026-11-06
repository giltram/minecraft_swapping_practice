package dev.hotbarswaptrainer.environment;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-thread environment boundary; task outcomes and inventory recovery belong to session. */
public interface SessionEnvironment {
    void tick(ServerPlayerEntity player);
    boolean interceptMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet);
    default void acceptedMovement(double beforeX, double beforeZ, double afterX, double afterZ) { }
    default void positionCorrected() { }

    static SessionEnvironment create(TrainingEnvironment type, ServerPlayerEntity player) {
        return type == TrainingEnvironment.MOVING ? new MovingEnvironment(player) : new StationaryEnvironment(player);
    }
}
