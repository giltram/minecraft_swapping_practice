package dev.hotbarswaptrainer.inventory;

import net.minecraft.nbt.CompoundTag;

/** Implemented on the player; the active record travels with vanilla player NBT. */
public interface RecoveryHolder {
    boolean trainer$hasRecovery();
    CompoundTag trainer$getRecovery();
    void trainer$setRecovery(CompoundTag recovery);
}
