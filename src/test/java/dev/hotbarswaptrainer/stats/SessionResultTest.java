package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class SessionResultTest {
    @Test
    public void invalidAttemptsRemainVisibleButDoNotAffectSuccessTimes() {
        TaskResult first = task(TaskResult.Outcome.SUCCESS, 2000);
        TaskResult invalid = task(TaskResult.Outcome.INVALID, 1);
        TaskResult second = task(TaskResult.Outcome.SUCCESS, 4000);
        List<TaskResult> tasks = new ArrayList<>(Arrays.asList(first, invalid, second));
        SessionResult session = new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM, "profile", 2, tasks);
        tasks.clear();
        assertEquals(3, session.getTasks().size());
        assertEquals(2, session.getSuccessCount());
        assertEquals(6000, session.getTotalNanos());
        assertEquals(3000, session.getAverageNanos());
        assertEquals(2000, session.getBestNanos());
        assertEquals(2, invalid.getMistakes());
    }

    @Test
    public void resultStoreReplacesTheSameSessionInsteadOfDuplicatingIt() {
        InMemoryResultStore store = new InMemoryResultStore();
        UUID playerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SessionResult first = new SessionResult(sessionId, TrainingMode.SINGLE_ITEM, "profile", 0, new ArrayList<>());
        SessionResult completed = new SessionResult(sessionId, TrainingMode.SINGLE_ITEM, "profile", 0,
                Arrays.asList(task(TaskResult.Outcome.SUCCESS, 10)));
        store.save(playerId, first);
        store.save(playerId, completed);
        assertSame(completed, store.findLatest(playerId).get());
        assertFalse(store.findLatest(UUID.randomUUID()).isPresent());
        store.forget(playerId);
        assertFalse(store.findLatest(playerId).isPresent());
        store.save(playerId, completed);
        store.clear();
        assertFalse(store.findLatest(playerId).isPresent());
        assertEquals(0, first.getAverageNanos());
        assertEquals(0, first.getBestNanos());
    }

    private static TaskResult task(TaskResult.Outcome outcome, long elapsed) {
        return new TaskResult(UUID.randomUUID(), outcome, elapsed, 3, 2, 4, 1, outcome == TaskResult.Outcome.INVALID ? 1 : 0, "fixture");
    }
}
