package dev.hotbarswaptrainer.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class SlotCandidateLayoutTest {
    @Test
    public void everyCandidateCanBeInspectedInASmallWindowIncludingLongWrappedPools() {
        int[] counts = {2, 1, 4, 7, 4, 1, 1, 100, 2, 1};
        SlotCandidateLayout layout = new SlotCandidateLayout(320, 240, counts);
        for (int slot = 0; slot < counts.length; slot++) {
            for (int candidate = 0; candidate < counts[slot]; candidate++) {
                int unscrolledY = layout.candidateY(slot, candidate) + layout.scroll();
                layout.setScroll(unscrolledY - layout.top - 8);
                int x = layout.candidateX(candidate);
                int y = layout.candidateY(slot, candidate);
                assertTrue(x >= layout.left);
                assertTrue(x + 16 <= layout.left + layout.width);
                assertTrue("slot=" + slot + " candidate=" + candidate, y >= layout.top && y + 16 <= layout.bottom);
                assertEquals(slot, layout.slotAt(x + 8, y + 8));
                assertEquals(candidate, layout.candidateAt(slot, x + 8, y + 8));
            }
        }
        assertTrue(layout.maximumScroll() > 0);
    }

    @Test
    public void clippedCardsAndIconGapsCannotCaptureHeaderFooterOrTooltips() {
        SlotCandidateLayout layout = new SlotCandidateLayout(320, 240, new int[] {7, 1, 4, 2, 1});
        layout.setScroll(30);
        assertTrue(layout.rowTop(0) < layout.top);
        assertEquals(-1, layout.slotAt(layout.left + 8, layout.top - 1));
        assertEquals(-1, layout.slotAt(layout.left + 8, layout.bottom));
        assertEquals(-1, layout.candidateAt(0, layout.candidateX(0) + 8, layout.candidateY(0, 0) + 8));
        layout.setScroll(0);
        assertEquals(-1, layout.candidateAt(0, layout.candidateX(0) + 17, layout.candidateY(0, 0) + 8));
        assertEquals(-1, layout.candidateAt(0, layout.left + 8, layout.rowTop(0) + 6));
        assertEquals(-1, layout.slotAt(layout.left - 1, layout.top + 1));
        assertEquals(-1, layout.slotAt(layout.left + 8, layout.rowTop(0) + layout.rowHeight(0) + 1));
    }

    @Test
    public void reflowClampsScrollAfterResizingOrMovingALargePool() {
        SlotCandidateLayout narrow = new SlotCandidateLayout(320, 240, new int[] {200, 1, 7});
        narrow.setScroll(Integer.MAX_VALUE);
        assertEquals(narrow.maximumScroll(), narrow.scroll());
        SlotCandidateLayout wide = new SlotCandidateLayout(960, 700, new int[] {1, 7, 200});
        wide.setScroll(narrow.scroll());
        assertEquals(wide.maximumScroll(), wide.scroll());
        int lastY = wide.candidateY(2, 199);
        assertTrue(lastY >= wide.top && lastY + 16 <= wide.bottom);
        assertEquals(199, wide.candidateAt(2, wide.candidateX(199) + 8, lastY + 8));
        wide.setScroll(-100);
        assertEquals(0, wide.scroll());
    }
}
