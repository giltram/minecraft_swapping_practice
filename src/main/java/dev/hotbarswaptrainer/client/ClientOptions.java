package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.task.TrainingMode;

import java.util.Objects;

/** Validated preferences only. Text-field drafts never enter this value. */
final class ClientOptions {
    static final int SCHEMA_VERSION = 1;
    final TrainingMode mode;
    final String profile;
    final int goal;
    final boolean showHints;
    final boolean showSwapCounts;

    ClientOptions(TrainingMode mode, String profile, int goal, boolean showHints) {
        this(mode, profile, goal, showHints, false);
    }

    ClientOptions(TrainingMode mode, String profile, int goal, boolean showHints, boolean showSwapCounts) {
        if (mode == null) throw new IllegalArgumentException("Training mode is required");
        if (profile == null || profile.length() > LoadoutProfile.MAX_ID_LENGTH
                || !profile.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid profile id");
        }
        if (goal < 0) throw new IllegalArgumentException("Successes must be 0..2147483647");
        this.mode = mode;
        this.profile = profile;
        this.goal = goal;
        this.showHints = showHints;
        this.showSwapCounts = showSwapCounts;
    }

    static ClientOptions defaults() { return new ClientOptions(TrainingMode.SINGLE_ITEM, "default", 10, false); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ClientOptions)) return false;
        ClientOptions value = (ClientOptions) other;
        return mode == value.mode && profile.equals(value.profile) && goal == value.goal && showHints == value.showHints
                && showSwapCounts == value.showSwapCounts;
    }

    @Override public int hashCode() { return Objects.hash(mode, profile, goal, showHints, showSwapCounts); }
}
