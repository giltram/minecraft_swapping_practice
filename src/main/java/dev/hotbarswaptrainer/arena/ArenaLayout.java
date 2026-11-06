package dev.hotbarswaptrainer.arena;

import dev.hotbarswaptrainer.course.BasicCourse;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** One exact managed-volume template; the underlying vanilla generator stays flat-64-v1. */
public final class ArenaLayout {
    public static final ArenaLayout FLAT = new ArenaLayout("flat", 0);
    public static final ArenaLayout PARKOUR = new ArenaLayout(BasicCourse.ID, BasicCourse.SEED);
    public final String id;
    public final long seed;
    private final BasicCourse course;
    private ArenaLayout(String id, long seed) { this.id = id; this.seed = seed; course = "flat".equals(id) ? null : new BasicCourse(seed); }
    static ArenaLayout parse(String id, String seed) {
        if (FLAT.id.equals(id) && "0".equals(seed)) return FLAT;
        if (PARKOUR.id.equals(id) && Long.toString(PARKOUR.seed).equals(seed)) return PARKOUR;
        throw new IllegalArgumentException("Unsupported arena layout/seed");
    }
    public boolean parkour() { return course != null; }
    public double arrivalY() { return parkour() ? BasicCourse.FEET_Y : ArenaSpec.SPAWN_Y; }
    public BlockState blockAt(int x, int y, int z) {
        BasicCourse.Platform platform = course == null ? null : course.blockPlatform(x, y, z);
        if (platform == null) return ArenaSpec.blockAt(y);
        if (x != platform.x || z != platform.z) return Blocks.SMOOTH_STONE.getDefaultState();
        return (platform.index == 0 ? Blocks.EMERALD_BLOCK : platform.index == course.finish().index
                ? Blocks.DIAMOND_BLOCK : Blocks.GOLD_BLOCK).getDefaultState();
    }

    /** Bounded START preflight: 1,305 comparisons including overlap, with no writes. */
    boolean checkCourse(java.util.function.Function<net.minecraft.util.math.BlockPos, BlockState> read) {
        if (course == null) return false;
        net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();
        for (int i = 0; i < course.platforms.size(); i++) {
            BasicCourse.Platform a = course.platforms.get(Math.max(0, i - 1)), b = course.platforms.get(i);
            for (int x = Math.min(a.x, b.x) - 1; x <= Math.max(a.x, b.x) + 1; x++)
                for (int z = Math.min(a.z, b.z) - 1; z <= Math.max(a.z, b.z) + 1; z++)
                    for (int y = BasicCourse.BLOCK_Y; y <= BasicCourse.FEET_Y + 3; y++)
                        if (!blockAt(x, y, z).equals(read.apply(pos.set(x, y, z)))) return false;
        }
        return true;
    }
}
