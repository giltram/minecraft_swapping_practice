package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class HungerProtectionMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void trainer$freezeFood(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InventoryService.isProtected((ServerPlayerEntity) player)) ci.cancel();
    }
}
