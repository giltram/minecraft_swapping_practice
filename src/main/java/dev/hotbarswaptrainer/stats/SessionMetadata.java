package dev.hotbarswaptrainer.stats;

import java.util.Objects;
import java.util.UUID;

/** Frozen at START, completed once after confirmed restoration. Hints are a client declaration. */
public final class SessionMetadata {
    public static final String TIMING = "server-go-ack-to-action-v1";
    public static final String SCORING = "success-only-v1";
    public static final String ENVIRONMENT = "stationary-v1";
    public enum EndState { COMPLETED, STOPPED, DISCONNECTED, SHUTDOWN, ABORTED }
    public final UUID playerId;
    public final long startedAtMillis, endedAtMillis;
    public final String profileId, profileName, timingVersion, scoringVersion, environment;
    public final boolean targetHints;
    public final EndState endState;

    public SessionMetadata(UUID playerId, long started, long ended, String profileId, String profileName,
                           boolean hints, String timing, String scoring, String environment, EndState endState) {
        this.playerId = Objects.requireNonNull(playerId);
        if (started < 0 || ended < started) throw new IllegalArgumentException("Invalid session dates");
        this.startedAtMillis = started; this.endedAtMillis = ended;
        this.profileId = text(profileId, 128); this.profileName = text(profileName, 256);
        this.timingVersion = text(timing, 128); this.scoringVersion = text(scoring, 128);
        this.environment = text(environment, 128); this.targetHints = hints;
        this.endState = Objects.requireNonNull(endState);
    }
    private static String text(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum) throw new IllegalArgumentException("Invalid metadata text");
        return value;
    }
    public boolean comparable() {
        return TIMING.equals(timingVersion) && SCORING.equals(scoringVersion) && ENVIRONMENT.equals(environment);
    }
}
