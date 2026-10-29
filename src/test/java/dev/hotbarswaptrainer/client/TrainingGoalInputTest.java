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
}
