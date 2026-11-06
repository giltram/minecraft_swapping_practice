package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.server.network.ServerPlayerEntity;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Server-thread fixture for actual UI restoration/save gates. */
public final class ProfileGateAcceptance {
    private static InventorySnapshot baseline;
    private ProfileGateAcceptance() { }
    public static void start(ServerPlayerEntity player) throws Exception {
        baseline=InventorySnapshot.capture(player.inventory);
        TrainerMod.SESSIONS.handle(player,TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM,"editor_fixture",1,true));
        require(phase(player).equals("PREPARING"),"Saved profile START");
    }
    public static void denyRestore(ServerPlayerEntity player) throws Exception {
        java.nio.file.Path directory=player.getServer().getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA);
        require(directory.toRealPath().equals(directory.toAbsolutePath().normalize()),"Refuse linked playerdata");
        java.util.Set<java.nio.file.attribute.PosixFilePermission> original=java.nio.file.Files.getPosixFilePermissions(directory);
        java.util.Set<java.nio.file.attribute.PosixFilePermission> denied=java.util.EnumSet.copyOf(original);
        denied.remove(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        denied.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
        denied.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
        try {
            java.nio.file.Files.setPosixFilePermissions(directory,denied);
            TrainerMod.SESSIONS.stop(player,"Stopped");
            require(phase(player).equals("ERROR_LOCKED"),"Actual denied restore did not lock");
        } finally { java.nio.file.Files.setPosixFilePermissions(directory,original); }
    }
    public static void retryRestore(ServerPlayerEntity player) throws Exception {
        TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
    }
}
