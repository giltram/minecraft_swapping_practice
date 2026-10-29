package dev.hotbarswaptrainer.profile;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** Immutable whole-stack value. Empty stacks have one canonical representation. */
public final class StackValue {
    public static final StackValue EMPTY = new StackValue("minecraft:air", 0, new CompoundTag());

    private final String itemId;
    private final int count;
    private final CompoundTag nbt;

    private StackValue(String itemId, int count, CompoundTag nbt) {
        this.itemId = itemId;
        this.count = count;
        this.nbt = nbt.copy();
    }

    public static StackValue of(String itemId, int count, CompoundTag nbt) {
        Objects.requireNonNull(itemId, "itemId");
        if (count < 0) throw new IllegalArgumentException("Stack count cannot be negative");
        if (count == 0 || "minecraft:air".equals(itemId)) return EMPTY;
        if (!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid item identifier: " + itemId);
        }
        return new StackValue(itemId, count, nbt == null ? new CompoundTag() : nbt);
    }

    public String getItemId() { return itemId; }
    public int getCount() { return count; }
    public CompoundTag getNbt() { return nbt.copy(); }
    public boolean isEmpty() { return count == 0; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof StackValue)) return false;
        StackValue other = (StackValue) value;
        return count == other.count && itemId.equals(other.itemId) && nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() { return Objects.hash(itemId, count, nbt); }

    @Override
    public String toString() { return isEmpty() ? "empty" : itemId + " x" + count + " " + nbt; }
}
