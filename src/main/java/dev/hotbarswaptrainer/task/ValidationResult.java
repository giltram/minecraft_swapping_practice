package dev.hotbarswaptrainer.task;

public final class ValidationResult {
    public enum Status { COMPLETE, INCOMPLETE, INVALID }
    private final Status status;
    private final String reason;

    private ValidationResult(Status status, String reason) { this.status = status; this.reason = reason; }
    public static ValidationResult complete() { return new ValidationResult(Status.COMPLETE, ""); }
    public static ValidationResult incomplete(String reason) { return new ValidationResult(Status.INCOMPLETE, reason); }
    public static ValidationResult invalid(String reason) { return new ValidationResult(Status.INVALID, reason); }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public boolean isComplete() { return status == Status.COMPLETE; }
    public boolean isInvalid() { return status == Status.INVALID; }
}
