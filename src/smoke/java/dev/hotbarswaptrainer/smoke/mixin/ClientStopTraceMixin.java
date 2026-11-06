package dev.hotbarswaptrainer.smoke.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive trace in the isolated Moving regression only; excluded from the production jar. */
@Mixin(MinecraftClient.class)
public abstract class ClientStopTraceMixin {
    @Inject(method = "method_1592", at = @At("HEAD"), remap = false)
    private void acceptance$traceStop(CallbackInfo ci) {
        new Exception("Owned Moving acceptance client stop request").printStackTrace();
    }
}
