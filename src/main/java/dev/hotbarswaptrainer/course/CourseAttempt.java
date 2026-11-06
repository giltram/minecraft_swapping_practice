package dev.hotbarswaptrainer.course;

/** Server-owned, disposable attempt. Time is supplied by the caller's monotonic clock. */
public final class CourseAttempt {
    public enum Phase { PREPARING, RUNNING, RETURNING, FINISHED, STOPPED }
    private final BasicCourse course;
    private Phase phase = Phase.PREPARING;
    private int platform, falls;
    private long started, elapsed;
    private boolean confirmed;
    private boolean airborne;
    private double lastX, lastY, lastZ;

    public CourseAttempt(BasicCourse course) { this.course = course; }
    public Phase phase() { return phase; }
    public int platform() { return platform; }
    public int falls() { return falls; }
    public BasicCourse.Platform safePlatform() { return course.platforms.get(platform); }
    public long elapsed(long now) { return phase == Phase.RUNNING || phase == Phase.RETURNING ? Math.max(0, now - started) : elapsed; }
    public boolean start(long now, boolean supportedAtStart) {
        if (phase != Phase.PREPARING || !supportedAtStart || !confirmed) return false;
        started = now; phase = Phase.RUNNING;
        baseline(safePlatform().centerX(), BasicCourse.FEET_Y, safePlatform().centerZ()); return true;
    }
    /** All corrections revoke arrival evidence, including external teleports. */
    public void corrected() { confirmed = false; airborne = false; if (phase == Phase.RUNNING) phase = Phase.RETURNING; }
    public void confirmed() { confirmed = true; }
    public boolean isConfirmed() { return confirmed; }
    public void fall() {
        if (phase != Phase.RUNNING) return;
        falls++; phase = Phase.RETURNING; confirmed = false;
    }
    /** Only fresh vanilla-accepted, collision-supported observations may call this. */
    public void accepted(double x, double y, double z, boolean supported, long now) {
        if (!confirmed) return;
        if (phase == Phase.RETURNING) {
            if (supported && safePlatform().supports(x, y, z)) { phase = Phase.RUNNING; baseline(x, y, z); }
            return; // System arrival cannot also claim a checkpoint.
        }
        if (phase != Phase.RUNNING) return;
        // A jump cannot be replaced with one position discontinuity, even for the vanilla local host.
        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        if (!Double.isFinite(dx + dy + dz) || dx * dx + dy * dy + dz * dz > 4) { phase = Phase.RETURNING; airborne = false; return; }
        baseline(x, y, z);
        if (!supported && y > BasicCourse.FEET_Y + 0.05) airborne = true;
        if (supported && airborne && platform + 1 < course.platforms.size() && course.platforms.get(platform + 1).supports(x, y, z)) {
            platform++;
            airborne = false;
            if (platform == course.finish().index) { elapsed = Math.max(0, now - started); phase = Phase.FINISHED; }
        }
        if (supported) airborne = false;
    }
    private void baseline(double x, double y, double z) { lastX = x; lastY = y; lastZ = z; }
    public void stop(long now) {
        if (phase == Phase.FINISHED || phase == Phase.STOPPED) return;
        elapsed = elapsed(now); phase = Phase.STOPPED;
    }
}
