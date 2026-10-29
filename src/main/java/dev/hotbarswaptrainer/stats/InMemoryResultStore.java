package dev.hotbarswaptrainer.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-thread confined M1 latest results; the session lifecycle clears disconnected players. */
public final class InMemoryResultStore implements ResultStore {
    private final Map<UUID, SessionResult> latest = new HashMap<>();
    @Override
    public void save(UUID playerId, SessionResult result) {
        latest.put(Objects.requireNonNull(playerId, "playerId"), Objects.requireNonNull(result, "result"));
    }
    @Override
    public Optional<SessionResult> findLatest(UUID playerId) { return Optional.ofNullable(latest.get(playerId)); }
    @Override
    public void forget(UUID playerId) { latest.remove(playerId); }
    @Override
    public void clear() { latest.clear(); }
}
