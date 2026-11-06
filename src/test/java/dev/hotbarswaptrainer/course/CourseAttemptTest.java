package dev.hotbarswaptrainer.course;

import org.junit.Test;
import static org.junit.Assert.*;

public class CourseAttemptTest {
    private final BasicCourse course = new BasicCourse(BasicCourse.SEED);
    private CourseAttempt running() {
        CourseAttempt attempt = new CourseAttempt(course);
        assertFalse(attempt.start(100, true)); attempt.confirmed(); assertFalse(attempt.start(100, false));
        assertTrue(attempt.start(100, true)); return attempt;
    }
    private void jump(CourseAttempt attempt, BasicCourse.Platform from, BasicCourse.Platform to, long now) {
        for (int i = 1; i <= 5; i++) {
            double t = i / 5.0;
            attempt.accepted(from.centerX() + (to.x - from.x) * t,
                    BasicCourse.FEET_Y + (i == 5 ? 0 : 0.8),
                    from.centerZ() + (to.z - from.z) * t, i == 5, now + i);
        }
    }
    @Test public void supportedSequentialJumpsFinishOnceAndFreezeClock() {
        CourseAttempt attempt = running();
        for (int i = 1; i < course.platforms.size(); i++) jump(attempt, course.platforms.get(i - 1), course.platforms.get(i), 100 + i * 10);
        assertEquals(CourseAttempt.Phase.FINISHED, attempt.phase()); assertEquals(course.finish().index, attempt.platform());
        long elapsed = attempt.elapsed(1000); attempt.stop(2000); attempt.fall(); attempt.corrected(); attempt.confirmed();
        assertEquals(elapsed, attempt.elapsed(3000)); assertEquals(0, attempt.falls());
    }
    @Test public void skipAndBelowPlatformCannotAdvance() {
        CourseAttempt attempt = running();
        BasicCourse.Platform next = course.platforms.get(1);
        attempt.accepted(next.centerX(), 4, next.centerZ(), true, 200); assertEquals(0, attempt.platform());
        attempt = running(); jump(attempt, course.start(), course.platforms.get(2), 200); assertEquals(0, attempt.platform());
    }
    @Test public void teleportAndSupportedPositionWithoutJumpCannotClaimCheckpoint() {
        CourseAttempt attempt = running(); BasicCourse.Platform next = course.platforms.get(1);
        attempt.accepted(next.centerX(), BasicCourse.FEET_Y, next.centerZ(), true, 200);
        assertEquals(0, attempt.platform()); assertEquals(CourseAttempt.Phase.RETURNING, attempt.phase());
        attempt = running(); attempt.corrected(); attempt.confirmed();
        attempt.accepted(course.finish().centerX(), BasicCourse.FEET_Y, course.finish().centerZ(), true, 200);
        assertEquals(0, attempt.platform()); assertEquals(CourseAttempt.Phase.RETURNING, attempt.phase());
    }
    @Test public void fallsAreIdempotentRequireConfirmationAndFreshArrivalAndKeepClock() {
        CourseAttempt attempt = running(); jump(attempt, course.start(), course.platforms.get(1), 110);
        attempt.fall(); attempt.fall(); assertEquals(1, attempt.falls()); assertEquals(1, attempt.platform());
        BasicCourse.Platform safe = attempt.safePlatform();
        attempt.accepted(safe.centerX(), BasicCourse.FEET_Y, safe.centerZ(), true, 200);
        assertEquals(CourseAttempt.Phase.RETURNING, attempt.phase()); attempt.confirmed();
        attempt.accepted(safe.centerX(), BasicCourse.FEET_Y, safe.centerZ(), true, 300);
        assertEquals(CourseAttempt.Phase.RUNNING, attempt.phase()); assertEquals(200, attempt.elapsed(300));
        assertEquals(1, attempt.platform());
        jump(attempt, safe, course.platforms.get(2), 310); assertEquals(2, attempt.platform());
    }
    @Test public void interruptedAttemptDoesNotAccumulateOfflineTimeOrBecomeSuccess() {
        CourseAttempt attempt = running(); attempt.stop(200);
        assertEquals(100, attempt.elapsed(999999)); assertEquals(CourseAttempt.Phase.STOPPED, attempt.phase());
        CourseAttempt reopened = new CourseAttempt(course); assertEquals(0, reopened.elapsed(999999)); assertEquals(0, reopened.platform());
    }
}
