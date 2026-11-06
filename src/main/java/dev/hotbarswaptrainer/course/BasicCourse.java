package dev.hotbarswaptrainer.course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Immutable geometry shared by terrain and runtime. No world/player/file dependencies. */
public final class BasicCourse {
    public static final String ID = "basic-parkour-v1";
    public static final long SEED = 163161L;
    public static final int BLOCK_Y = 8, FEET_Y = 9;
    public static final double FALL_Y = 7.5;
    public final long seed;
    public final List<Platform> platforms;

    public BasicCourse(long seed) {
        this.seed = seed;
        // Each edge is a cardinal, level jump between 3x3 platforms with one air block.
        // No sprint, effects or training-item use is part of the geometry contract.
        int[][] centers = {{0,0},{0,4},{0,8},{0,12},{0,16},{0,20},
                {4,20},{8,20},{12,20},{16,20},{20,20},{20,16},{20,12}};
        int turns = new Random(seed).nextInt(4);
        List<Platform> result = new ArrayList<>();
        for (int i = 0; i < centers.length; i++) {
            int x = centers[i][0], z = centers[i][1];
            for (int turn = 0; turn < turns; turn++) { int oldX = x; x = -z; z = oldX; }
            result.add(new Platform(i, x, z));
        }
        platforms = Collections.unmodifiableList(result);
        validate();
    }

    public Platform start() { return platforms.get(0); }
    public Platform finish() { return platforms.get(platforms.size() - 1); }
    public int checkpoints() { return platforms.size() - 2; }
    public Platform blockPlatform(int x, int y, int z) {
        if (y != BLOCK_Y) return null;
        for (Platform platform : platforms) if (platform.containsBlock(x, z)) return platform;
        return null;
    }
    public boolean allowed(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || y < FALL_Y || y > FEET_Y + 3) return false;
        for (Platform platform : platforms)
            if (Math.abs(x - platform.centerX()) <= 3.1 && Math.abs(z - platform.centerZ()) <= 3.1) return true;
        return false;
    }
    private void validate() {
        for (Platform platform : platforms) {
            if (Math.abs(platform.x) + 2 > 31 || Math.abs(platform.z) + 2 > 31)
                throw new IllegalStateException("Course clearance leaves owned bounds");
            if (platform.index > 0) {
                Platform previous = platforms.get(platform.index - 1);
                if (Math.abs(previous.x - platform.x) + Math.abs(previous.z - platform.z) != 4
                        || (previous.x != platform.x && previous.z != platform.z))
                    throw new IllegalStateException("Course edge is not the level one-gap template");
            }
        }
    }

    public static final class Platform {
        public final int index, x, z;
        private Platform(int index, int x, int z) { this.index = index; this.x = x; this.z = z; }
        public double centerX() { return x + 0.5; }
        public double centerZ() { return z + 0.5; }
        public boolean containsBlock(int blockX, int blockZ) { return Math.abs(blockX - x) <= 1 && Math.abs(blockZ - z) <= 1; }
        /** Require the entire normal player footprint on the landing surface. */
        public boolean supports(double feetX, double feetY, double feetZ) {
            return Math.abs(feetY - FEET_Y) < 0.025
                    && feetX >= x - 0.7 && feetX <= x + 1.7 && feetZ >= z - 0.7 && feetZ <= z + 1.7;
        }
    }
}
