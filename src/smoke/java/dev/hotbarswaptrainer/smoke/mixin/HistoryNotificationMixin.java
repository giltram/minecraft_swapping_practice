package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.network.ServerPackets;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.smoke.HistoryNotificationFault;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPackets.class, remap = false)
public abstract class HistoryNotificationMixin {
    @Inject(method = "send", at = @At("HEAD"), remap = false)
    private static void failNotification(ServerPlayerEntity player, TrainerProtocol.View view, CallbackInfo ci) {
        HistoryNotificationFault.beforeSend();
    }
}
