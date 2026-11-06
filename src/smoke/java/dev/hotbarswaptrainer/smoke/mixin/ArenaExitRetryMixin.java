package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.arena.PracticeArenaService;
import dev.hotbarswaptrainer.smoke.ArenaExitRetryFault;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PracticeArenaService.class, remap = false)
public abstract class ArenaExitRetryMixin {
    @Inject(method = "commitExit", at = @At("HEAD"))
    private void acceptance$arm(ServerPlayerEntity player, CallbackInfo ci) { ArenaExitRetryFault.enteringCommit(); }
}
