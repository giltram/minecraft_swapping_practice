package dev.hotbarswaptrainer.environment;

import dev.hotbarswaptrainer.course.BasicCourse;
import dev.hotbarswaptrainer.course.CourseAttempt;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Course runtime adapter. Terrain lifecycle and inventory mutations are deliberately absent. */
public final class ParkourEnvironment implements SessionEnvironment {
    private final ServerWorld world;
    private final BasicCourse course = new BasicCourse(BasicCourse.SEED);
    private final CourseAttempt attempt = new CourseAttempt(course);

    public ParkourEnvironment(ServerPlayerEntity player) { world = player.getServerWorld(); }
    @Override public CourseAttempt courseAttempt() { return attempt; }
    @Override public void initialize(ServerPlayerEntity player) { returnToPlatform(player); }
    @Override public boolean begin(ServerPlayerEntity player, long now) {
        return attempt.phase() != CourseAttempt.Phase.PREPARING
                || attempt.start(now, supported(player) && course.start().supports(player.getX(), player.getY(), player.getZ()));
    }
    @Override public void tick(ServerPlayerEntity player) {
        if (world != player.getServerWorld()) throw new IllegalStateException("Training dimension changed unexpectedly");
        if (attempt.phase() == CourseAttempt.Phase.RUNNING && !course.allowed(player.getX(), player.getY(), player.getZ())) {
            attempt.fall(); returnToPlatform(player);
        } else if (attempt.phase() == CourseAttempt.Phase.RETURNING && attempt.isConfirmed()
                && !attempt.safePlatform().supports(player.getX(), player.getY(), player.getZ())) {
            returnToPlatform(player); // External correction cannot place the player ahead of the checkpoint.
        } else if (attempt.phase() == CourseAttempt.Phase.FINISHED
                && !course.finish().supports(player.getX(), player.getY(), player.getZ())) {
            returnToPlatform(player);
        }
    }
    @Override public boolean interceptMovement(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        if (attempt.phase() != CourseAttempt.Phase.PREPARING && attempt.phase() != CourseAttempt.Phase.FINISHED) return false;
        BasicCourse.Platform safe = attempt.safePlatform();
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        if (Math.abs(x - safe.centerX()) < 0.001 && Math.abs(y - BasicCourse.FEET_Y) < 0.001
                && Math.abs(z - safe.centerZ()) < 0.001) return false;
        player.yaw = packet.getYaw(player.yaw); player.pitch = packet.getPitch(player.pitch);
        returnToPlatform(player); return true;
    }
    @Override public void acceptedMovement(ServerPlayerEntity player, double beforeX, double beforeZ) {
        attempt.accepted(player.getX(), player.getY(), player.getZ(), supported(player), System.nanoTime());
    }
    @Override public void positionCorrected() { attempt.corrected(); }
    @Override public void teleportConfirmed() { attempt.confirmed(); }
    @Override public void settle(ServerPlayerEntity player) { returnToPlatform(player); }
    private void returnToPlatform(ServerPlayerEntity player) {
        BasicCourse.Platform safe = attempt.safePlatform();
        player.networkHandler.requestTeleport(safe.centerX(), BasicCourse.FEET_Y, safe.centerZ(), player.yaw, player.pitch);
        player.setVelocity(0, 0, 0); player.fallDistance = 0;
    }
    private boolean supported(ServerPlayerEntity player) {
        return player.isOnGround() && !player.hasVehicle() && !player.isFallFlying() && !player.abilities.flying
                && !player.noClip && !player.isSpectator()
                && world.doesNotCollide(player, player.getBoundingBox())
                && !world.doesNotCollide(player, player.getBoundingBox().offset(0, -0.02, 0));
    }
}
