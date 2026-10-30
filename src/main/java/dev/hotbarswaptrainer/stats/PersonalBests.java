package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;

import java.util.*;

/** Pure derivation from a history snapshot. A random-practice personal record, not a same-task ranking. */
public final class PersonalBests {
    public final Optional<Best> task, total, average;
    public final boolean incomplete;
    public final boolean comparable;
    private PersonalBests(Best task, Best total, Best average, boolean incomplete, boolean comparable) {
        this.task = Optional.ofNullable(task); this.total = Optional.ofNullable(total); this.average = Optional.ofNullable(average);
        this.incomplete = incomplete; this.comparable = comparable;
    }

    /** Fastest successful task for one mode, across profiles, hints and session lengths. */
    public static Optional<Best> fastestTaskForMode(HistorySnapshot history, TrainingMode mode) {
        Objects.requireNonNull(mode, "Training mode is required");
        Best best = null;
        for (SessionResult result : chronological(history)) {
            SessionMetadata metadata = result.getMetadata();
            if (metadata == null || !metadata.comparable() || !history.playerId.equals(metadata.playerId)
                    || result.getMode() != mode) continue;
            for (TaskResult task : result.getTasks()) if (task.getOutcome() == TaskResult.Outcome.SUCCESS)
                best = better(best, task.getElapsedNanos(), result, task.getTaskId());
        }
        return Optional.ofNullable(best);
    }

    public static PersonalBests forConditions(HistorySnapshot history, SessionResult conditions) {
        SessionMetadata m = conditions.getMetadata();
        if (m == null || !m.comparable() || !m.playerId.equals(history.playerId))
            return new PersonalBests(null, null, null, !history.errors.isEmpty(), false);
        Best task = null, total = null, average = null;
        for (SessionResult result : chronological(history)) {
            SessionMetadata other = result.getMetadata();
            if (other == null || !other.comparable() || !m.playerId.equals(other.playerId)
                    || result.getMode() != conditions.getMode() || !result.getProfileHash().equals(conditions.getProfileHash())
                    || m.targetHints != other.targetHints || !m.timingVersion.equals(other.timingVersion)
                    || !m.scoringVersion.equals(other.scoringVersion) || !m.environment.equals(other.environment)) continue;
            for (TaskResult attempt : result.getTasks()) if (attempt.getOutcome() == TaskResult.Outcome.SUCCESS && attempt.getElapsedNanos() > 0)
                task = better(task, attempt.getElapsedNanos(), result, attempt.getTaskId());
            if (result.getRequestedSuccesses() > 0 && result.getRequestedSuccesses() == conditions.getRequestedSuccesses()
                    && other.endState == SessionMetadata.EndState.COMPLETED && result.getSuccessCount() == result.getRequestedSuccesses()) {
                total = better(total, result.getTotalNanos(), result, null);
                average = better(average, result.getAverageNanos(), result, null);
            }
        }
        return new PersonalBests(task, total, average, !history.errors.isEmpty(), true);
    }
    private static List<SessionResult> chronological(HistorySnapshot history) {
        List<SessionResult> records = new ArrayList<>(history.records);
        records.sort(Comparator.comparingLong((SessionResult r) -> r.getMetadata() == null ? 0 : r.getMetadata().endedAtMillis)
                .thenComparing(r -> r.getSessionId().toString()));
        return records;
    }
    private static Best better(Best old, long nanos, SessionResult session, UUID task) {
        return nanos > 0 && (old == null || nanos < old.nanos) ? new Best(nanos, session.getSessionId(), task) : old;
    }
    public static final class Best {
        public final long nanos;
        public final UUID sessionId, taskId;
        private Best(long nanos, UUID session, UUID task) { this.nanos = nanos; this.sessionId = session; this.taskId = task; }
    }
}
