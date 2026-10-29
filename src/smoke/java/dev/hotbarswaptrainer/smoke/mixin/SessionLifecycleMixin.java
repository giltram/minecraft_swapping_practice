package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.session.SessionManager;
import dev.hotbarswaptrainer.smoke.ExitObservation;
import dev.hotbarswaptrainer.smoke.SaveWriteFault;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SessionManager.class, remap = false)
public abstract class SessionLifecycleMixin {
    @Inject(method = "stop", at = @At("HEAD"))
    private void acceptance$stop(ServerPlayerEntity player, String reason, CallbackInfo ci) { SaveWriteFault.enteringStop(); }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void acceptance$disconnect(ServerPlayerEntity player, CallbackInfo ci) { ExitObservation.observe(player, "disconnect"); }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void acceptance$shutdown(MinecraftServer server, CallbackInfo ci) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) ExitObservation.observe(player, "shutdown");
    }
}
