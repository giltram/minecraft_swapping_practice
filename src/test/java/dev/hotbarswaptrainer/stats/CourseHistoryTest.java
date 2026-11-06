package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.course.BasicCourse;
import dev.hotbarswaptrainer.environment.TrainingEnvironment;
import dev.hotbarswaptrainer.task.TrainingMode;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static dev.hotbarswaptrainer.stats.HistoryStoreTest.*;

public class CourseHistoryTest {
    private final UUID player = UUID.randomUUID();
    private CourseResult course(boolean complete, long seed) {
        return new CourseResult(BasicCourse.ID, seed, CourseResult.RULES, CourseResult.TIMING, 1000, 2,
                complete ? new BasicCourse(seed).finish().index : 2, complete);
    }
    private SessionResult result(TrainingEnvironment env, CourseResult course, SessionMetadata.EndState end, boolean solved) {
        return new SessionResult(UUID.randomUUID(), TrainingMode.SINGLE_ITEM, HASH, 1,
                Arrays.asList(task(solved ? TaskResult.Outcome.SUCCESS : TaskResult.Outcome.STOPPED, 100)),
                new SessionMetadata(player, 100, 200, "default", "Default", false, SessionMetadata.TIMING,
                        SessionMetadata.SCORING, env.historyId, end), course);
    }
    @Test public void courseRoundTripsSeparatelyFromTaskTimeInBothCompletionOrdersAndPartialResults() throws Exception {
        for (boolean completed : new boolean[] {true, false}) for (boolean solved : new boolean[] {true, false}) {
            SessionResult r = result(TrainingEnvironment.PARKOUR, course(completed, BasicCourse.SEED),
                    completed && solved ? SessionMetadata.EndState.COMPLETED : SessionMetadata.EndState.STOPPED, solved);
            SessionResult read = HistoryJson.decode(HistoryJson.encode(player, r), player, r.getSessionId());
            assertEquals(completed, read.getCourse().completed); assertEquals(1000, read.getCourse().elapsedNanos);
            assertEquals(solved ? 100 : 0, read.getBestNanos()); assertEquals(2, read.getCourse().falls);
        }
    }
    @Test public void completedSessionRequiresBothAndLegacyMetadataCannotMasqueradeAsCourse() throws Exception {
        for (SessionResult r : Arrays.asList(result(TrainingEnvironment.PARKOUR, course(false, BasicCourse.SEED), SessionMetadata.EndState.COMPLETED, true),
                result(TrainingEnvironment.PARKOUR, null, SessionMetadata.EndState.STOPPED, true),
                result(TrainingEnvironment.MOVING, course(true, BasicCourse.SEED), SessionMetadata.EndState.COMPLETED, true))) {
            try { HistoryJson.encode(player, r); fail(); } catch (java.io.IOException expected) { }
        }
    }
    @Test public void allEnvironmentInventoryPbsAreIsolatedAndCourseComparisonsRequireIdentitySeedAndRules() {
        List<SessionResult> results = new ArrayList<>();
        for (TrainingEnvironment env : TrainingEnvironment.values()) results.add(result(env,
                env == TrainingEnvironment.PARKOUR ? course(true, BasicCourse.SEED) : null, SessionMetadata.EndState.COMPLETED, true));
        HistorySnapshot history = new HistorySnapshot(player, results, Collections.emptyList());
        for (int i = 0; i < results.size(); i++) assertEquals(results.get(i).getSessionId(),
                PersonalBests.fastestTaskForMode(history, TrainingMode.SINGLE_ITEM, TrainingEnvironment.values()[i]).get().sessionId);
        CourseResult a = course(true, BasicCourse.SEED);
        assertTrue(a.comparableWith(course(true, BasicCourse.SEED)));
        assertFalse(a.comparableWith(course(true, BasicCourse.SEED + 1))); assertFalse(a.comparableWith(course(false, BasicCourse.SEED)));
        assertFalse(a.comparableWith(new CourseResult("other-v1", BasicCourse.SEED, CourseResult.RULES, CourseResult.TIMING, 1, 0, 0, true)));
        assertFalse(a.comparableWith(new CourseResult(BasicCourse.ID, BasicCourse.SEED, "other", CourseResult.TIMING, 1, 0, a.platform, true)));
    }
}
