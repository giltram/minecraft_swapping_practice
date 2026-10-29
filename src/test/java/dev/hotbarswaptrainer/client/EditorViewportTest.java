package dev.hotbarswaptrainer.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorViewportTest {
    @Test public void longFormsKeepAllControlsReachableAtDifferentScaledWindowSizes() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            EditorViewport viewport = new EditorViewport(size[0], size[1]);
            viewport.contentHeight(12000);
            for (int offset = 0; offset < 12000 - 20; offset += 24) {
                viewport.reveal(offset, 20);
                assertTrue(viewport.y(offset) >= viewport.top);
                assertTrue(viewport.y(offset) + 20 <= viewport.bottom);
                assertTrue(viewport.contains(viewport.left + 8, viewport.y(offset) + 10));
            }
        }
    }
    @Test public void partialRowsNeverExtendPointerBoundsIntoHeadersOrActionButtons() {
        EditorViewport viewport = new EditorViewport(320, 240);
        viewport.contentHeight(1000); viewport.setScroll(10);
        assertTrue(viewport.visible(0, 20));
        assertFalse(viewport.contains(viewport.left + 8, viewport.y(0)));
        assertTrue(viewport.contains(viewport.left, viewport.top));
        assertFalse(viewport.contains(viewport.left - 1, viewport.top));
        assertFalse(viewport.contains(viewport.left + viewport.width, viewport.top));
        assertFalse(viewport.contains(viewport.left, viewport.bottom));
        assertFalse(viewport.contains(viewport.left + 8, 212));
    }
    @Test public void reflowClampsRetainedScrollAndCanRevealFocusInBothDirections() {
        EditorViewport small = new EditorViewport(320, 240); small.contentHeight(700); small.setScroll(10000);
        EditorViewport resized = new EditorViewport(960, 540); resized.contentHeight(700); resized.setScroll(small.scroll());
        assertEquals(resized.maximumScroll(), resized.scroll());
        resized.reveal(0, 20); assertEquals(0, resized.scroll());
        resized.reveal(680, 20); assertEquals(resized.bottom, resized.y(680) + 20);
        resized.contentHeight(40); assertEquals(0, resized.scroll());
    }
}
