package dev.hotbarswaptrainer.smoke;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.environment.TrainingEnvironment;
import dev.hotbarswaptrainer.inventory.InventorySnapshot;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.hotbarswaptrainer.smoke.AcceptanceSupport.*;

/** Real vanilla mining queue, production Mixins and confirmed restores in an owned world. */
final class PendingMiningAcceptance {
    static String run(ServerPlayerEntity player, InventorySnapshot baseline, Path output) throws Exception {
        BlockPos target = player.getBlockPos().east().up();
        require(player.world.isChunkLoaded(target), "Mining fixture must use an already loaded chunk");
        BlockState original = player.world.getBlockState(target);
        require(original.isAir(), "Refuse to replace existing terrain for mining fixture");
        List<String> checks = new ArrayList<>();
        try {
            for (TrainingEnvironment environment : new TrainingEnvironment[] {TrainingEnvironment.STATIONARY, TrainingEnvironment.MOVING}) {
                for (TrainingMode mode : TrainingMode.values()) {
                    for (boolean delayed : new boolean[]{false, true}) {
                        player.world.setBlockState(target, Blocks.STONE.getDefaultState());
                        dig(player, target, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
                        require((Boolean)field(player.interactionManager,"field_14003"), "Vanilla mining did not start");
                        if (delayed) {
                            dig(player,target,PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
                            require((Boolean)field(player.interactionManager,"field_20328"), "Vanilla did not queue delayed mining");
                        }
                        TrainerMod.SESSIONS.handle(player,TrainerProtocol.Request.start(mode,"default",0,false,environment));
                        require(phase(player).equals("PREPARING"),"Mining fixture START failed");
                        require(!(Boolean)field(player.interactionManager,"field_14003")
                                &&!(Boolean)field(player.interactionManager,"field_20328"),"START did not discard pending mining immediately");
                        InventorySnapshot training=InventorySnapshot.capture(player.inventory);
                        // The actual removal boundary is protected even before the first protected update.
                        require(!player.interactionManager.tryBreakBlock(target),"Direct protected break succeeded");
                        for (int tick=0;tick<400;tick++) player.interactionManager.update();
                        require(player.world.getBlockState(target).isOf(Blocks.STONE),"Queued mining changed terrain");
                        require(training.equals(InventorySnapshot.capture(player.inventory)),"Queued mining changed training stack NBT");
                        require(!(Boolean)field(player.interactionManager,"field_14003")
                                &&!(Boolean)field(player.interactionManager,"field_20328"),"Pending mining was paused instead of discarded");
                        TrainerMod.SESSIONS.stop(player,"Stopped"); restored(player,baseline);
                        for (int tick=0;tick<400;tick++) player.interactionManager.update();
                        require(player.world.getBlockState(target).isOf(Blocks.STONE),"Pre-START work replayed after restore");
                        // A fresh ordinary vanilla action still completes through the delayed update path.
                        dig(player,target,PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
                        dig(player,target,PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
                        for (int tick=0;tick<400;tick++) player.interactionManager.update();
                        require(player.world.getBlockState(target).isAir(),"Ordinary mining remained blocked after restore");
                        require(baseline.equals(InventorySnapshot.capture(player.inventory)),"Ordinary hand mining changed baseline");
                        checks.add(environment+" / "+mode+" / "+(delayed?"delayed":"active")+": block/NBT intact, queue discarded, fresh ordinary mining works");
                    }
                }
            }
        } finally {
            // Only this explicitly created fixture is removed; no unknown terrain is cleaned.
            player.world.setBlockState(target,original);
        }
        restored(player,baseline);
        json(output.resolve("pending-mining-checks.json"),checks);
        return checks.size()+" pending-mining protection checks across both environments/modes";
    }
    private static void dig(ServerPlayerEntity player, BlockPos pos, PlayerActionC2SPacket.Action action) {
        player.networkHandler.onPlayerAction(new PlayerActionC2SPacket(action,pos,Direction.UP));
    }
    private PendingMiningAcceptance() { }
}
