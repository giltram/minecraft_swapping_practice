package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.TrainerMod;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla saves at the start of remove, so restoration must precede that call. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void trainer$restoreBeforeRemoval(ServerPlayerEntity player, CallbackInfo ci) {
        TrainerMod.SESSIONS.disconnect(player);
    }
}
