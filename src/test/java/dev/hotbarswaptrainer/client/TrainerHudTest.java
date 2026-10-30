package dev.hotbarswaptrainer.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrainerHudTest {
    @Test public void hiddenCountersLeaveOnlyTheTimer() {
        assertEquals("3.904 s", TrainerHud.timingLine(3_904_000_000L, 7, 5, false));
    }
    @Test public void enabledCountersShowAttemptsAndEffectiveSwaps() {
        assertEquals("3.904 s  |  swaps 7 (effective 5)", TrainerHud.timingLine(3_904_000_000L, 7, 5, true));
    }
}
