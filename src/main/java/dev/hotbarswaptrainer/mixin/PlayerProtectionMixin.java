package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerProtectionMixin {
    @Unique private boolean trainer$isProtected() {
        return (Object) this instanceof ServerPlayerEntity
                && InventoryService.isProtected((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void trainer$freezeExhaustion(float amount, CallbackInfo ci) {
        if (trainer$isProtected()) ci.cancel();
    }

    @Inject(method = "canPickUp", at = @At("HEAD"), cancellable = true)
    private void trainer$preventDispenserEquipment(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (trainer$isProtected()) cir.setReturnValue(false);
    }

    // Peaceful food regeneration bypasses HungerManager.update.
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;setFoodLevel(I)V"))
    private void trainer$freezePeacefulFood(HungerManager hunger, int level) {
        if (!trainer$isProtected()) hunger.setFoodLevel(level);
    }
}
