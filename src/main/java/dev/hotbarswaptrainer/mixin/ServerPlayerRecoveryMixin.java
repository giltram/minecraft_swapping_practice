package dev.hotbarswaptrainer.mixin;

import dev.hotbarswaptrainer.TrainerMod;
import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.RecoveryHolder;
import dev.hotbarswaptrainer.inventory.RecoveryRecord;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recovery data follows the exact vanilla serialization that owns the inventory. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerRecoveryMixin implements RecoveryHolder {
    @Unique private CompoundTag trainer$recovery;
    @Unique private Tag trainer$invalidRawRecovery;

    @Override public boolean trainer$hasRecovery() { return trainer$recovery != null; }

    @Override public CompoundTag trainer$getRecovery() {
        return trainer$recovery == null ? null : trainer$recovery.copy();
    }

    @Override public void trainer$setRecovery(CompoundTag recovery) {
        trainer$recovery = recovery == null ? null : recovery.copy();
        trainer$invalidRawRecovery = null;
    }

    @Inject(method = "writeCustomDataToTag", at = @At("TAIL"))
    private void trainer$writeRecovery(CompoundTag tag, CallbackInfo ci) {
        if (trainer$invalidRawRecovery != null) tag.put(RecoveryRecord.KEY, trainer$invalidRawRecovery.copy());
        else if (trainer$recovery == null) tag.remove(RecoveryRecord.KEY);
        else tag.put(RecoveryRecord.KEY, trainer$recovery.copy());
    }

    @Inject(method = "readCustomDataFromTag", at = @At("TAIL"))
    private void trainer$readRecovery(CompoundTag tag, CallbackInfo ci) {
        Tag invalidRaw = tag.contains(RecoveryRecord.KEY) && !tag.contains(RecoveryRecord.KEY, 10)
                ? tag.get(RecoveryRecord.KEY).copy() : null;
        TrainerMod.SESSIONS.onLoad((ServerPlayerEntity) (Object) this, tag);
        // Keep the exact corrupt tag for rescue, while SessionManager's sentinel keeps guards active.
        trainer$invalidRawRecovery = invalidRaw;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void trainer$preventDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryService.isProtected((ServerPlayerEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void trainer$preventDimensionChange(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (TrainerMod.SESSIONS.guardAction(player, "Dimension travel is not allowed during training")) cir.setReturnValue(player);
    }
}
