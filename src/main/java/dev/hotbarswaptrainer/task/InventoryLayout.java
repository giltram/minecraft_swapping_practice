package dev.hotbarswaptrainer.task;

import dev.hotbarswaptrainer.profile.StackValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Domain positions are hotbar 0..8 and offhand 9, never ScreenHandler indices. */
public final class InventoryLayout {
    public static final int HOTBAR_SIZE = 9;
    public static final int OFFHAND = 9;
    public static final int SIZE = 10;
    private final List<StackValue> stacks;

    public InventoryLayout(List<StackValue> stacks) {
        if (stacks.size() != SIZE) throw new IllegalArgumentException("Layout needs exactly ten positions");
        for (StackValue stack : stacks) Objects.requireNonNull(stack, "stack");
        this.stacks = Collections.unmodifiableList(new ArrayList<>(stacks));
    }

    public static InventoryLayout empty() { return new InventoryLayout(Collections.nCopies(SIZE, StackValue.EMPTY)); }
    public StackValue get(int position) { return stacks.get(position); }
    public List<StackValue> getStacks() { return stacks; }

    public InventoryLayout with(int position, StackValue value) {
        List<StackValue> result = new ArrayList<>(stacks);
        result.set(position, value);
        return new InventoryLayout(result);
    }

    /** One legal vanilla offhand exchange; useful as a proof, never an execution API. */
    public InventoryLayout withSwap(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE) throw new IllegalArgumentException("Swap needs hotbar 0..8");
        List<StackValue> result = new ArrayList<>(stacks);
        Collections.swap(result, hotbarSlot, OFFHAND);
        return new InventoryLayout(result);
    }

    public int countStacks() {
        int count = 0;
        for (StackValue stack : stacks) if (!stack.isEmpty()) count++;
        return count;
    }

    Map<StackValue, Integer> multiset() {
        Map<StackValue, Integer> result = new HashMap<>();
        for (StackValue stack : stacks) result.put(stack, result.getOrDefault(stack, 0) + 1);
        return result;
    }

    @Override
    public boolean equals(Object value) { return value instanceof InventoryLayout && stacks.equals(((InventoryLayout) value).stacks); }
    @Override
    public int hashCode() { return stacks.hashCode(); }
    @Override
    public String toString() { return stacks.toString(); }
}
