package dev.hotbarswaptrainer.task;

/** Sole owner of conservation and target predicates, shared by generation and live validation. */
public final class TaskValidator {
    public ValidationResult validate(InventoryTask task, InventoryLayout actual) {
        if (!task.getInitial().multiset().equals(actual.multiset())) {
            return ValidationResult.invalid("Whole-stack inventory changed (item, count, or complete NBT)");
        }
        for (int position = 0; position < InventoryLayout.SIZE; position++) {
            if (!task.getSlotRules().get(position).matches(actual.get(position))) {
                String name = position == InventoryLayout.OFFHAND ? "offhand" : "hotbar" + (position + 1);
                return ValidationResult.incomplete(name + " does not match");
            }
        }
        return ValidationResult.complete();
    }
}
