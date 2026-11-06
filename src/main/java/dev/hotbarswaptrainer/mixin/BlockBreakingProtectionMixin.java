package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.PendingBlockBreaking;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla can finish mining from update() without receiving another action packet. */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class BlockBreakingProtectionMixin implements PendingBlockBreaking {
    @Shadow public ServerPlayerEntity player;
    @Shadow public ServerWorld world;
    @Shadow private boolean mining;
    @Shadow private BlockPos miningPos;
    @Shadow private boolean failedToMine;
    @Shadow private BlockPos failedMiningPos;
    @Shadow private int blockBreakingProgress;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void trainer$discardPendingMining(CallbackInfo ci) {
        if (!InventoryService.isProtected(player)) return;
        trainer$discardPendingMining();
        ci.cancel();
    }

    @Override public void trainer$discardPendingMining() {
        // Discard pre-START work, rather than letting it resume with the restored tool.
        if (mining) world.setBlockBreakingInfo(player.getEntityId(), miningPos, -1);
        if (failedToMine) world.setBlockBreakingInfo(player.getEntityId(), failedMiningPos, -1);
        mining = false;
        failedToMine = false;
        blockBreakingProgress = -1;
    }

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void trainer$guardActualBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!InventoryService.isProtected(player)) return;
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, pos));
        cir.setReturnValue(false);
    }
}
