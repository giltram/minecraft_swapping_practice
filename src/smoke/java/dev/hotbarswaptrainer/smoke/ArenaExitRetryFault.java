package dev.hotbarswaptrainer.smoke;

import java.io.IOException;

/** One explicit simulated final-exit save failure; independent of the real POSIX failure cases. */
public final class ArenaExitRetryFault {
    private static volatile boolean enabled, nextSave, observed;
    private ArenaExitRetryFault() { }
    static void enable() { enabled = true; }
    public static void enteringCommit() { if (enabled && !observed) nextSave = true; }
    public static void beforeSave() throws IOException {
        if (!nextSave) return;
        nextSave = false; observed = true;
        throw new IOException("Excluded arena acceptance: simulated final-exit save failure");
    }
    static boolean observed() { return observed; }
}
