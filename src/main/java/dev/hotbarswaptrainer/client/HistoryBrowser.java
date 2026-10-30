package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.stats.*;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Client-owned query state. Explicit synchronous refresh means no network or stale asynchronous responses. */
final class HistoryBrowser {
    private final HistoryStore store;
    private final UUID player;
    private final BooleanSupplier allowed;
    HistorySnapshot snapshot;
    HistorySnapshot.Filter filter = HistorySnapshot.Filter.all();
    int page;
    HistoryBrowser(HistoryStore store, UUID player, BooleanSupplier allowed) {
        this.store = store; this.player = player; this.allowed = allowed;
    }
    boolean refresh() {
        if (!allowed.getAsBoolean()) return false;
        snapshot = store.read(player); page = current().index; return true;
    }
    boolean allowed() { return allowed.getAsBoolean(); }
    HistorySnapshot.Page current() { return snapshot.query(filter, page); }
    void filter(HistorySnapshot.Filter value) { filter = value; page = 0; }
    void turn(int delta) { page = Math.max(0, Math.min(current().pages - 1, page + delta)); }
}
