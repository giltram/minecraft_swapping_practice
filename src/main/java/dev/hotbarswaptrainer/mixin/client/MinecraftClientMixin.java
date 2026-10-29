package dev.hotbarswaptrainer.mixin.client;

import dev.hotbarswaptrainer.client.TrainerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppress premature transmission after vanilla consumes its own bound-key queue. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void trainer$gatePrematureSwap(ClientPlayNetworkHandler handler, Packet<?> packet) {
        if (packet instanceof PlayerActionC2SPacket
                && ((PlayerActionC2SPacket) packet).getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && !TrainerClient.allowVanillaSwap()) return;
        handler.sendPacket(packet);
    }

    @Inject(method = "openScreen", at = @At("HEAD"))
    private void trainer$invalidateForScreen(Screen screen, CallbackInfo ci) {
        TrainerClient.onOpenScreen(screen);
    }
}
