package dev.hotbarswaptrainer.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class TrainingGoalInputTest {
    @Test
    public void customSuccessCountsIncludeOneUnlimitedAndValuesAboveTheOldCap() {
        TrainerClient trainer = new TrainerClient();
        for (int goal : new int[] {0, 1, 7, 73, 10001, Integer.MAX_VALUE}) {
            assertTrue(trainer.setGoal(Integer.toString(goal)));
            assertEquals(goal, trainer.goal());
        }
        assertTrue(trainer.setGoal("0007"));
        assertEquals(7, trainer.goal());
    }

    @Test
    public void invalidOrUnfinishedInputCannotOverwriteTheLastValidGoal() {
        TrainerClient trainer = new TrainerClient();
        assertTrue(trainer.setGoal("73"));
        for (String text : new String[] {null, "", "-1", "+1", "1.5", "1e2", " ", "1 ",
                "2147483648", "9999999999", "10000000000", "one", "１"}) {
            assertFalse(String.valueOf(text), trainer.setGoal(text));
            assertEquals(73, trainer.goal());
        }
        assertTrue(trainer.setGoal("0"));
        assertEquals(0, trainer.goal());
    }
    @Test public void parserPreservesZeroAndTheExistingRangeWithoutInventingDefaults() {
        for (int accepted : new int[] {0, 1, 73, 10001, Integer.MAX_VALUE})
            assertEquals(Integer.valueOf(accepted), TrainerClient.parseGoal(Integer.toString(accepted)));
        assertEquals(Integer.valueOf(73), TrainerClient.parseGoal("00073"));
        for (String raw : new String[] {null, "", "-1", "x", "2147483648", "１", " 7", "7 "})
            assertNull(TrainerClient.parseGoal(raw));
    }
    @Test public void optionsHideIdleReadyButRetainOtherFeedbackAndErrors() {
        assertFalse(TrainerOptionsScreen.showSessionMessage(null));
        assertFalse(TrainerOptionsScreen.showSessionMessage(new dev.hotbarswaptrainer.network.TrainerProtocol.View.Builder()
                .phase("IDLE").message("Ready").build()));
        assertTrue(TrainerOptionsScreen.showSessionMessage(new dev.hotbarswaptrainer.network.TrainerProtocol.View.Builder()
                .phase("IDLE").message("Stopped").build()));
        assertTrue(TrainerOptionsScreen.showSessionMessage(new dev.hotbarswaptrainer.network.TrainerProtocol.View.Builder()
                .phase("ERROR").message("Profile unavailable").build()));
        assertTrue(TrainerOptionsScreen.showSessionMessage(new dev.hotbarswaptrainer.network.TrainerProtocol.View.Builder()
                .phase("ERROR_LOCKED").message("Restore failed").build()));
    }
}
