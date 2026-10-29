package dev.hotbarswaptrainer.environment;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** A stationary session has one position anchor; camera rotation remains under vanilla control. */
public final class StationaryEnvironment {
    private final Vec3d anchor;
    private final ServerWorld world;
    public StationaryEnvironment(ServerPlayerEntity player) { anchor = player.getPos(); world = player.getServerWorld(); }
    public void tick(ServerPlayerEntity player) {
        if (player.getServerWorld() != world) throw new IllegalStateException("Training dimension changed unexpectedly");
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        if (player.getPos().squaredDistanceTo(anchor) > 0.000001) {
            player.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, player.yaw, player.pitch);
        }
        if (world.doesNotCollide(player, player.getBoundingBox().offset(0, -0.02, 0))) {
            throw new IllegalStateException("The stationary anchor no longer has solid support");
        }
        // Vanilla normally updates this flag in movement packets, which this environment rejects.
        player.setOnGround(true);
    }
    public void observeMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        player.yaw = packet.getYaw(player.yaw);
        player.pitch = packet.getPitch(player.pitch);
        // Teleport only on attempted translation, so normal camera packets remain smooth.
        double dx = packet.getX(anchor.x) - anchor.x;
        double dy = packet.getY(anchor.y) - anchor.y;
        double dz = packet.getZ(anchor.z) - anchor.z;
        if (dx * dx + dy * dy + dz * dz > 0.000001) {
            player.networkHandler.requestTeleport(anchor.x, anchor.y, anchor.z, player.yaw, player.pitch);
        }
    }
}
