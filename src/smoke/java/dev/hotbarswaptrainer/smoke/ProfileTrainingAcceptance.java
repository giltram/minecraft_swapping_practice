package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.stats.*;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.server.network.ServerPlayerEntity;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Server-thread half of the real UI saved-profile integration check. */
public final class ProfileTrainingAcceptance {
    private static InventorySnapshot baseline;
    private ProfileTrainingAcceptance() { }
    public static void start(ServerPlayerEntity player) throws Exception {
        baseline=InventorySnapshot.capture(player.inventory);
        TrainerMod.SESSIONS.handle(player,TrainerProtocol.Request.start(TrainingMode.SINGLE_ITEM,"editor_fixture",1,true));
        require(phase(player).equals("PREPARING"),"Saved profile START");
        dev.hotbarswaptrainer.profile.LoadoutProfile profile=(dev.hotbarswaptrainer.profile.LoadoutProfile)field(session(player),"profile");
        require(profile.getId().equals("editor_fixture")&&profile.getName().equals("M2-C UI saved"),"Server did not reload visually saved profile");
        dev.hotbarswaptrainer.profile.SlotRule slot=profile.getSlots().get(3);
        require(slot.getKind()==dev.hotbarswaptrainer.profile.SlotRule.Kind.OPTIONAL,"Saved rule kind");
        dev.hotbarswaptrainer.profile.StackValue stack=slot.getAlternatives().get(0).getTemplate();
        require(stack.getItemId().equals("minecraft:dirt")&&stack.getCount()==32&&stack.getNbt().getByte("M2")==1,"Saved candidate/count/typed NBT");
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
    public static void complete(ServerPlayerEntity player) throws Exception {
        signal(player,TrainerProtocol.Action.READY);
        set(session(player),"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
        signal(player,TrainerProtocol.Action.GO_ACK); solve(player);
        require(phase(player).equals("SUCCESS"),"Edited profile solution");
        set(session(player),"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
        restored(player,baseline);
        SessionResult result=((ResultStore)field(TrainerMod.SESSIONS,"results")).findLatest(player.getUuid()).get();
        require(result.getMetadata().profileId.equals("editor_fixture")&&result.getMetadata().endState==SessionMetadata.EndState.COMPLETED
                &&result.getSuccessCount()==1,"Edited profile history");
    }
}
