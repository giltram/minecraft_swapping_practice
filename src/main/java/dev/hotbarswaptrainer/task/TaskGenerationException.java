package dev.hotbarswaptrainer.task;

public final class TaskGenerationException extends IllegalArgumentException {
    public enum Reason { MODE_PRECONDITION, BOUNDED_SEARCH_EXHAUSTED }
    private final Reason reason;

    public TaskGenerationException(String message) { this(Reason.MODE_PRECONDITION, message); }
    public TaskGenerationException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason getReason() { return reason; }
}
