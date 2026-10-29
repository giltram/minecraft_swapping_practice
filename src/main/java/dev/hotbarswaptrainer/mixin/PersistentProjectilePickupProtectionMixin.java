package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrows and returning tridents insert directly into inventory instead of becoming ItemEntity. */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectilePickupProtectionMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void trainer$preventPickup(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InventoryService.isProtected((ServerPlayerEntity) player)) ci.cancel();
    }
}
