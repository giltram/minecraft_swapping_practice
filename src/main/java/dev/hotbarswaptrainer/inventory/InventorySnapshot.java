package dev.hotbarswaptrainer.inventory;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** All 36 main, four armor and one offhand slots, plus selected hotbar slot. */
public final class InventorySnapshot {
    private final ListTag inventory;
    private final int selectedSlot;

    public InventorySnapshot(ListTag inventory, int selectedSlot) {
        if (selectedSlot < 0 || selectedSlot > 8) throw new IllegalArgumentException("Invalid selected slot");
        boolean[] occupied = new boolean[256];
        for (int i = 0; i < inventory.size(); i++) {
            if (!(inventory.get(i) instanceof CompoundTag)) throw new IllegalArgumentException("Snapshot entries must be compounds");
            CompoundTag stack = inventory.getCompound(i);
            int slot = stack.getByte("Slot") & 255;
            boolean valid = slot < 36 || (slot >= 100 && slot < 104) || slot == 150;
            if (!valid || occupied[slot] || !stack.contains("Slot", 1) || !stack.contains("Count", 1)
                    || !stack.contains("id", 8) || !stack.getString("id").matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || "minecraft:air".equals(stack.getString("id")) || stack.getByte("Count") <= 0
                    || (stack.contains("tag") && !stack.contains("tag", 10))) {
                throw new IllegalArgumentException("Invalid inventory snapshot at entry " + i);
            }
            occupied[slot] = true;
        }
        this.inventory = inventory.copy();
        this.selectedSlot = selectedSlot;
    }

    public static InventorySnapshot capture(PlayerInventory inventory) {
        return new InventorySnapshot(inventory.serialize(new ListTag()), inventory.selectedSlot);
    }

    public void restore(PlayerInventory target) {
        target.clear();
        target.deserialize(inventory.copy());
        target.selectedSlot = selectedSlot;
        target.setCursorStack(net.minecraft.item.ItemStack.EMPTY);
        target.markDirty();
        if (!equals(capture(target))) throw new IllegalStateException("Restored inventory differs from backup");
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", inventory.copy());
        tag.putInt("SelectedItemSlot", selectedSlot);
        return tag;
    }

    public static InventorySnapshot fromTag(CompoundTag tag) {
        if (!tag.contains("Inventory", 9) || !tag.contains("SelectedItemSlot", 3)) {
            throw new IllegalArgumentException("Incomplete inventory snapshot");
        }
        return new InventorySnapshot((ListTag) tag.get("Inventory"), tag.getInt("SelectedItemSlot"));
    }

    @Override public boolean equals(Object other) {
        return other instanceof InventorySnapshot && selectedSlot == ((InventorySnapshot) other).selectedSlot
                && inventory.equals(((InventorySnapshot) other).inventory);
    }
    @Override public int hashCode() { return inventory.hashCode() * 31 + selectedSlot; }
}
