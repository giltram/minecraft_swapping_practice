package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.inventory.ConfirmedPlayerSave;
import dev.hotbarswaptrainer.smoke.ArenaExitRetryFault;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfirmedPlayerSave.class, remap = false)
public abstract class ArenaExitSaveFailureMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void acceptance$failOnce(ServerPlayerEntity player, CallbackInfo ci) throws java.io.IOException { ArenaExitRetryFault.beforeSave(); }
}
