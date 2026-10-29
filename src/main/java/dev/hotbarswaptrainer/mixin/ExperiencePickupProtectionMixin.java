package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** XP pickup may repair held stacks through Mending even when inventory is full. */
@Mixin(ExperienceOrbEntity.class)
public abstract class ExperiencePickupProtectionMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void trainer$preventPickup(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity && InventoryService.isProtected((ServerPlayerEntity) player)) ci.cancel();
    }
}
