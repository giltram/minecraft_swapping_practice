package dev.hotbarswaptrainer.profile;

/** Registry boundary. Return zero for unknown items and air. */
@FunctionalInterface
public interface ItemCatalog {
    int maximumStackSize(String itemId);

    /** Freeze defaults added by the runtime item constructor (for example tool Damage:0). */
    default StackValue normalizeTemplate(StackValue value) { return value; }
}
