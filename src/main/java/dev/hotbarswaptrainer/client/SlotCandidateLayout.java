package dev.hotbarswaptrainer.client;

/** Screen-local geometry for wrapped candidate icons, scrolling and clipped hit testing. */
final class SlotCandidateLayout {
    static final int ICON_STEP = 20;
    static final int PADDING = 8;
    static final int HEADER_HEIGHT = 20;
    final int left;
    final int width;
    final int top = 48;
    final int bottom;
    private final int columns;
    private final int[] candidateCounts;
    private final int[] offsets;
    private final int[] heights;
    private final int contentHeight;
    private int scroll;

    SlotCandidateLayout(int screenWidth, int screenHeight, int[] candidateCounts) {
        width = Math.max(36, Math.min(440, screenWidth - 32));
        left = (screenWidth - width) / 2;
        bottom = Math.max(top + 1, screenHeight - 76);
        columns = Math.max(1, (width - PADDING * 2) / ICON_STEP);
        this.candidateCounts = candidateCounts.clone();
        offsets = new int[candidateCounts.length];
        heights = new int[candidateCounts.length];
        int offset = 0;
        for (int slot = 0; slot < candidateCounts.length; slot++) {
            offsets[slot] = offset;
            int rows = Math.max(1, (candidateCounts[slot] + columns - 1) / columns);
            heights[slot] = HEADER_HEIGHT + rows * ICON_STEP + PADDING;
            offset += heights[slot] + 4;
        }
        contentHeight = Math.max(0, offset - 4);
    }

    int rowTop(int slot) { return top + offsets[slot] - scroll; }
    int rowHeight(int slot) { return heights[slot]; }
    int candidateX(int candidate) { return left + PADDING + (candidate % columns) * ICON_STEP; }
    int candidateY(int slot, int candidate) { return rowTop(slot) + HEADER_HEIGHT + (candidate / columns) * ICON_STEP; }
    int scroll() { return scroll; }
    int maximumScroll() { return Math.max(0, contentHeight - (bottom - top)); }
    int contentHeight() { return contentHeight; }
    void setScroll(int value) { scroll = Math.max(0, Math.min(maximumScroll(), value)); }

    boolean contains(double x, double y) {
        return x >= left && x < left + width && y >= top && y < bottom;
    }

    boolean visible(int slot) { return rowTop(slot) < bottom && rowTop(slot) + heights[slot] > top; }

    int slotAt(double x, double y) {
        if (!contains(x, y)) return -1;
        for (int slot = 0; slot < offsets.length; slot++) {
            if (y >= rowTop(slot) && y < rowTop(slot) + heights[slot]) return slot;
        }
        return -1;
    }

    int candidateAt(int slot, double x, double y) {
        if (slot < 0 || slotAt(x, y) != slot) return -1;
        int relativeX = (int) x - left - PADDING;
        int relativeY = (int) y - rowTop(slot) - HEADER_HEIGHT;
        if (relativeX < 0 || relativeY < 0 || relativeX / ICON_STEP >= columns
                || relativeX % ICON_STEP >= 16 || relativeY % ICON_STEP >= 16) return -1;
        int candidate = (relativeY / ICON_STEP) * columns + relativeX / ICON_STEP;
        return candidate < candidateCounts[slot] ? candidate : -1;
    }
}
