package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Game-thread-only projection; never reads the integrated server's mutable state. */
final class ClientSessionView {
    private TrainerProtocol.View value;
    private long receivedAt;
    private boolean readySent;
    private boolean goSent;
    private boolean interruptionSent;
    private final Set<UUID> closedSessions = new LinkedHashSet<>();

    TrainerProtocol.View get() { return value; }

    void accept(TrainerProtocol.View next) {
        if (closedSessions.contains(next.sessionId)) return;
        if (value != null && value.sessionId.equals(next.sessionId) && next.revision <= value.revision) return;
        if (value == null || !value.sessionId.equals(next.sessionId) || !value.taskId.equals(next.taskId)) {
            if (value != null && !value.sessionId.equals(next.sessionId) && !value.sessionId.equals(TrainerProtocol.NONE)) {
                closedSessions.add(value.sessionId);
                if (closedSessions.size() > 64) closedSessions.remove(closedSessions.iterator().next());
            }
            readySent = false; goSent = false; interruptionSent = false;
        }
        value = next;
        receivedAt = System.nanoTime();
    }

    void clear() {
        value = null;
        closedSessions.clear();
        readySent = false; goSent = false; interruptionSent = false;
    }

    boolean active() { return value != null && value.isActive(); }
    boolean readySent() { return readySent; }
    boolean goSent() { return goSent; }
    boolean interruptionSent() { return interruptionSent; }
    void markReady() { readySent = true; interruptionSent = false; }
    void markGo() { goSent = true; }
    void markInterrupted() { interruptionSent = true; }

    long displayElapsedNanos() {
        if (value == null) return 0;
        return value.elapsedNanos + ("RUNNING".equals(value.phase) ? Math.max(0, System.nanoTime() - receivedAt) : 0);
    }
}
