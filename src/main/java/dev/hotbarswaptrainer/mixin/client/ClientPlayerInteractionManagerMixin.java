package dev.hotbarswaptrainer.mixin.client;

import dev.hotbarswaptrainer.client.TrainerClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Buckets and block placement can change the client world before the server rejects a packet. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = {"interactItem", "interactBlock"}, at = @At("HEAD"), cancellable = true)
    private void trainer$preventUsePrediction(CallbackInfoReturnable<ActionResult> cir) {
        if (TrainerClient.preventItemPrediction()) cir.setReturnValue(ActionResult.FAIL);
    }
}
