package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Status ticks can directly change health or food without taking damage. */
@Mixin(LivingEntity.class)
public abstract class LivingProtectionMixin {
    @Unique private boolean trainer$isProtected() {
        return (Object) this instanceof ServerPlayerEntity
                && InventoryService.isProtected((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void trainer$freezeHealing(float amount, CallbackInfo ci) {
        if (trainer$isProtected()) ci.cancel();
    }

    @Inject(method = "tickStatusEffects", at = @At("HEAD"), cancellable = true)
    private void trainer$freezeStatusEffects(CallbackInfo ci) {
        if (trainer$isProtected()) ci.cancel();
    }
}
