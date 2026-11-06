package dev.hotbarswaptrainer.stats;

import dev.hotbarswaptrainer.course.BasicCourse;
import dev.hotbarswaptrainer.course.CourseAttempt;

/** A course time is independent of inventory single-task PBs. No combined score. */
public final class CourseResult {
    public static final String RULES = "one-task-both-complete-v1", TIMING = "first-go-ack-through-finish-v1";
    public final String courseId, rulesVersion, timingVersion;
    public final long seed, elapsedNanos;
    public final int falls, platform;
    public final boolean completed;
    public CourseResult(String courseId, long seed, String rulesVersion, String timingVersion,
                        long elapsedNanos, int falls, int platform, boolean completed) {
        if (courseId == null || courseId.isEmpty() || courseId.length() > 128 || rulesVersion == null || rulesVersion.isEmpty()
                || rulesVersion.length() > 128 || timingVersion == null || timingVersion.isEmpty() || timingVersion.length() > 128
                || seed < 0 || elapsedNanos < 0 || falls < 0 || platform < 0 || platform > 1024
                || (completed && elapsedNanos == 0)) throw new IllegalArgumentException("Invalid course result");
        if (BasicCourse.ID.equals(courseId) && (platform > new BasicCourse(seed).finish().index
                || completed != (platform == new BasicCourse(seed).finish().index))) throw new IllegalArgumentException("Inconsistent basic course result");
        this.courseId = courseId; this.seed = seed; this.rulesVersion = rulesVersion; this.timingVersion = timingVersion;
        this.elapsedNanos = elapsedNanos; this.falls = falls; this.platform = platform; this.completed = completed;
    }
    public static CourseResult freeze(CourseAttempt attempt) {
        return new CourseResult(BasicCourse.ID, BasicCourse.SEED, RULES, TIMING, attempt.elapsed(System.nanoTime()),
                attempt.falls(), attempt.platform(), attempt.phase() == CourseAttempt.Phase.FINISHED);
    }
    public boolean comparableWith(CourseResult other) {
        return BasicCourse.ID.equals(courseId) && RULES.equals(rulesVersion) && TIMING.equals(timingVersion)
                && completed && other != null && other.completed && courseId.equals(other.courseId) && seed == other.seed
                && rulesVersion.equals(other.rulesVersion) && timingVersion.equals(other.timingVersion);
    }
}
