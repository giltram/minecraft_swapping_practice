package dev.hotbarswaptrainer.stats;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Statistics are outside inventory recovery. Implementations must save the same session idempotently. */
public interface ResultStore {
    void save(UUID playerId, SessionResult result) throws IOException;
    Optional<SessionResult> findLatest(UUID playerId);
    void forget(UUID playerId);
    void clear();
}
