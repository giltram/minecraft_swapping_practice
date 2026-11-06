package dev.hotbarswaptrainer.course;

import org.junit.Test;
import static org.junit.Assert.*;

public class BasicCourseTest {
    @Test public void allSeedsUseOnlyLevelOneGapTemplatesWithinBoundsAndClearance() {
        for (long seed = 0; seed < 1000; seed++) {
            BasicCourse a = new BasicCourse(seed), b = new BasicCourse(seed);
            assertEquals(a.platforms.size(), b.platforms.size());
            for (int i = 0; i < a.platforms.size(); i++) {
                BasicCourse.Platform p = a.platforms.get(i);
                assertEquals(p.x, b.platforms.get(i).x); assertEquals(p.z, b.platforms.get(i).z);
                assertTrue(Math.abs(p.x) + 2 < 32); assertTrue(Math.abs(p.z) + 2 < 32);
                assertSame(p, a.blockPlatform(p.x, BasicCourse.BLOCK_Y, p.z));
                assertNull(a.blockPlatform(p.x, BasicCourse.FEET_Y + 1, p.z));
                if (i > 0) {
                    BasicCourse.Platform prev = a.platforms.get(i - 1);
                    assertEquals(4, Math.abs(p.x - prev.x) + Math.abs(p.z - prev.z));
                    assertTrue(p.x == prev.x || p.z == prev.z);
                }
                for (int j = 0; j < i; j++) {
                    BasicCourse.Platform other = a.platforms.get(j);
                    assertTrue(Math.abs(other.x - p.x) >= 4 || Math.abs(other.z - p.z) >= 4);
                }
            }
        }
    }
    @Test public void platformRequiresFeetHeightAndCompleteFootprint() {
        BasicCourse c = new BasicCourse(0); BasicCourse.Platform p = c.start();
        assertTrue(p.supports(0.5, 9, 0.5)); assertFalse(p.supports(0.5, 4, 0.5));
        assertFalse(p.supports(1.99, 9, 0.5)); assertFalse(c.allowed(0.5, 7, 0.5));
        assertFalse(c.allowed(31, 9, 31)); assertFalse(c.allowed(Double.NaN, 9, 0));
    }
}
