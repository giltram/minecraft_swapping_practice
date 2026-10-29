package dev.hotbarswaptrainer.smoke.mixin;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.smoke.SaveWriteFault;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryService.class, remap = false)
public abstract class RestoreBoundaryMixin {
    @Inject(method = "restore", at = @At(value = "INVOKE",
            target = "Ldev/hotbarswaptrainer/inventory/ConfirmedPlayerSave;save(Lnet/minecraft/class_3222;)V"))
    private void acceptance$before(ServerPlayerEntity player, CallbackInfo ci) { SaveWriteFault.restoreBoundary(false); }

    @Inject(method = "restore", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Ldev/hotbarswaptrainer/inventory/ConfirmedPlayerSave;save(Lnet/minecraft/class_3222;)V"))
    private void acceptance$after(ServerPlayerEntity player, CallbackInfo ci) { SaveWriteFault.restoreBoundary(true); }
}
