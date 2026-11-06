package dev.hotbarswaptrainer.inventory;

/** Vanilla interaction-manager adapter: discard mining before an inventory transaction. */
public interface PendingBlockBreaking {
    void trainer$discardPendingMining();
}
