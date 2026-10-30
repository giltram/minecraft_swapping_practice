package dev.hotbarswaptrainer.smoke;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Lets the actual client observe RESTORING before its server thread continues. Test-only and bounded. */
public final class UiRestoreGate {
    public static volatile boolean armed;
    public static volatile CountDownLatch release = new CountDownLatch(0);
    private UiRestoreGate() { }
    public static void arm() { release = new CountDownLatch(1); armed = true; }
    public static void awaitClient() {
        if (!armed) return;
        try {
            if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("UI restore observation timed out");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        finally { armed = false; }
    }
}
