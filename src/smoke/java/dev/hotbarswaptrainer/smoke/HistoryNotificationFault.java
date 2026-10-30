package dev.hotbarswaptrainer.smoke;

/** Armed only by the owned history acceptance case. Absent from production. */
public final class HistoryNotificationFault {
    public static boolean armed;
    public static int attempts;
    private HistoryNotificationFault() { }
    public static void beforeSend() {
        if (armed) { attempts++; throw new IllegalStateException("Injected history notification failure"); }
    }
}
