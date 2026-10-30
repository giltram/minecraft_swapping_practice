package dev.hotbarswaptrainer.stats;

import java.util.Objects;
import java.util.UUID;

/** Final server-observed result. Invalid/stopped attempts remain visible but cannot set a best time. */
public final class TaskResult {
    public enum Outcome { SUCCESS, INVALID, STOPPED }
    private final UUID taskId;
    private final Outcome outcome;
    private final long elapsedNanos;
    private final int swapAttempts;
    private final int effectiveSwaps;
    private final int slotChanges;
    private final int noOpSwaps;
    private final int forbiddenActions;
    private final String invalidReason;

    public TaskResult(UUID taskId, Outcome outcome, long elapsedNanos, int swapAttempts,
                      int effectiveSwaps, int slotChanges, int noOpSwaps, int forbiddenActions, String invalidReason) {
        if (elapsedNanos < 0 || swapAttempts < 0 || effectiveSwaps < 0 || slotChanges < 0
                || noOpSwaps < 0 || forbiddenActions < 0 || (long) swapAttempts != (long) effectiveSwaps + noOpSwaps
                || (long) noOpSwaps + forbiddenActions > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid result counters");
        }
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.elapsedNanos = elapsedNanos;
        this.swapAttempts = swapAttempts;
        this.effectiveSwaps = effectiveSwaps;
        this.slotChanges = slotChanges;
        this.noOpSwaps = noOpSwaps;
        this.forbiddenActions = forbiddenActions;
        this.invalidReason = invalidReason == null ? "" : invalidReason;
    }

    public UUID getTaskId() { return taskId; }
    public Outcome getOutcome() { return outcome; }
    public long getElapsedNanos() { return elapsedNanos; }
    public int getSwapAttempts() { return swapAttempts; }
    public int getEffectiveSwaps() { return effectiveSwaps; }
    public int getSlotChanges() { return slotChanges; }
    public int getNoOpSwaps() { return noOpSwaps; }
    public int getForbiddenActions() { return forbiddenActions; }
    public String getInvalidReason() { return invalidReason; }
    public int getMistakes() { return noOpSwaps + forbiddenActions; }
    public String getTimingQuality() { return "server-go-ack-to-action"; }
}
