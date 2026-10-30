package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.smoke.UiRestoreGate;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryService.class, remap = false)
public abstract class UiRestoreGateMixin {
    @Inject(method = "restore", at = @At("HEAD"), remap = false)
    private void awaitClient(ServerPlayerEntity player, CallbackInfo ci) { UiRestoreGate.awaitClient(); }
}
