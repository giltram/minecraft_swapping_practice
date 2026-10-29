package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Current-session summary. Zero requested successes means unlimited; zero best means no success. */
public final class SessionResult {
    private final UUID sessionId;
    private final TrainingMode mode;
    private final String profileHash;
    private final int requestedSuccesses;
    private final List<TaskResult> tasks;

    public SessionResult(UUID sessionId, TrainingMode mode, String profileHash, int requestedSuccesses, List<TaskResult> tasks) {
        if (requestedSuccesses < 0) throw new IllegalArgumentException("Session length cannot be negative");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.profileHash = Objects.requireNonNull(profileHash, "profileHash");
        this.requestedSuccesses = requestedSuccesses;
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
    }

    public UUID getSessionId() { return sessionId; }
    public TrainingMode getMode() { return mode; }
    public String getProfileHash() { return profileHash; }
    public int getRequestedSuccesses() { return requestedSuccesses; }
    public List<TaskResult> getTasks() { return tasks; }
    public int getSuccessCount() {
        int count = 0;
        for (TaskResult task : tasks) if (task.getOutcome() == TaskResult.Outcome.SUCCESS) count++;
        return count;
    }
    public long getTotalNanos() {
        long total = 0;
        for (TaskResult task : tasks) if (task.getOutcome() == TaskResult.Outcome.SUCCESS) total += task.getElapsedNanos();
        return total;
    }
    public long getAverageNanos() { return getSuccessCount() == 0 ? 0 : getTotalNanos() / getSuccessCount(); }
    public long getBestNanos() {
        long best = Long.MAX_VALUE;
        for (TaskResult task : tasks) if (task.getOutcome() == TaskResult.Outcome.SUCCESS) best = Math.min(best, task.getElapsedNanos());
        return best == Long.MAX_VALUE ? 0 : best;
    }
}
