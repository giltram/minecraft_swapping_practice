package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.stats.*;
import net.minecraft.server.network.ServerPlayerEntity;
import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Isolated server-thread assertions after the actual options screen sends START. */
public final class UsabilityTrainingAcceptance {
    private static InventorySnapshot baseline;
    public static void capture(ServerPlayerEntity player) { baseline=InventorySnapshot.capture(player.inventory); }
    public static void readyToSolve(ServerPlayerEntity player) throws Exception {
        Object active=session(player);
        require(active!=null,"UI START did not create session");
        LoadoutProfile profile=(LoadoutProfile)field(active,"profile");
        require(profile.getId().equals("editor_fixture"),"Server reload selected profile");
        require(profile.getSlots().get(0).getAlternatives().get(0).getTemplate().getCount()==32,"Server reload saved quantity");
        if(phase(player).equals("PREPARING")) signal(player,TrainerProtocol.Action.READY);
        if(phase(player).equals("COUNTDOWN")) {
            set(active,"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer());
        }
        if(phase(player).equals("GO")) signal(player,TrainerProtocol.Action.GO_ACK);
        require(phase(player).equals("RUNNING"),"Ready to solve");
    }
    public static void complete(ServerPlayerEntity player, int goal) throws Exception {
        readyToSolve(player);
        Object active=session(player);
        solve(player); require(phase(player).equals("SUCCESS"),"Actual vanilla-handler swaps solved task");
        if(goal==0) TrainerMod.SESSIONS.stop(player,"Stopped");
        else { set(active,"deadlineTick",player.getServer().getTicks()-1); TrainerMod.SESSIONS.tick(player.getServer()); }
        restored(player,baseline);
        SessionResult result=((ResultStore)field(TrainerMod.SESSIONS,"results")).findLatest(player.getUuid()).get();
        require(result.getRequestedSuccesses()==goal && result.getSuccessCount()==1,"Frozen goal/result");
        require(result.getMetadata().endState==(goal==0?SessionMetadata.EndState.STOPPED:SessionMetadata.EndState.COMPLETED),"Correct end state");
    }
    private UsabilityTrainingAcceptance() { }
}
