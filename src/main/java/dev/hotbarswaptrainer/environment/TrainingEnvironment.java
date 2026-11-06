package dev.hotbarswaptrainer.environment;

/** Independent of inventory task mode. Versioned IDs are persisted in history. */
public enum TrainingEnvironment {
    STATIONARY("stationary-v1", "Stationary"),
    MOVING("moving-v1", "Moving");

    public final String historyId;
    public final String displayName;

    TrainingEnvironment(String historyId, String displayName) {
        this.historyId = historyId;
        this.displayName = displayName;
    }

    public static boolean supported(String historyId) {
        for (TrainingEnvironment environment : values()) if (environment.historyId.equals(historyId)) return true;
        return false;
    }

}
