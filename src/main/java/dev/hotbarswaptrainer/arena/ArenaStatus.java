package dev.hotbarswaptrainer.arena;

/** Detached client projection; no server, session, inventory or UI objects cross threads. */
public final class ArenaStatus {
    public enum State { NONE, CHECKING, BUILDING, SAVING, READY, ERROR, EXIT_RESTORED, EXIT_READY }
    public enum Problem { NONE, OWNERSHIP, RECOVERY, SAVE, RESET_REQUIRED, LOAD, LOCAL_ONLY }
    public final State state;
    public final Problem problem;
    public final int percent;
    public final boolean resetAllowed;
    public final boolean parkour;

    public ArenaStatus(State state, Problem problem, int percent, boolean resetAllowed) {
        this(state, problem, percent, resetAllowed, false);
    }
    public ArenaStatus(State state, Problem problem, int percent, boolean resetAllowed, boolean parkour) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Invalid arena progress");
        this.state = state; this.problem = problem; this.percent = percent; this.resetAllowed = resetAllowed;
        this.parkour = parkour;
    }

    public static ArenaStatus of(State state, int percent) { return new ArenaStatus(state, Problem.NONE, percent, false); }
    public static ArenaStatus error(Problem problem, boolean resetAllowed) { return new ArenaStatus(State.ERROR, problem, 0, resetAllowed); }
}
