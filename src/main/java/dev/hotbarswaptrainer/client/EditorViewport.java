package dev.hotbarswaptrainer.client;

/** Shared half-open render/input bounds for the editor's scrollable forms. */
final class EditorViewport {
    final int left, width, top, bottom;
    private int contentHeight;
    private int scroll;
    EditorViewport(int screenWidth, int screenHeight) {
        width = Math.max(40, Math.min(440, screenWidth - 32));
        left = (screenWidth - width) / 2;
        top = 30;
        bottom = Math.max(top + 24, screenHeight - 36);
    }
    void contentHeight(int height) { contentHeight = height; setScroll(scroll); }
    int scroll() { return scroll; }
    int maximumScroll() { return Math.max(0, contentHeight - (bottom - top)); }
    void setScroll(int value) { scroll = Math.max(0, Math.min(maximumScroll(), value)); }
    int y(int offset) { return top + offset - scroll; }
    boolean contains(double x, double y) {
        return x >= left && x < left + width && y >= top && y < bottom;
    }
    boolean visible(int offset, int height) { return y(offset) < bottom && y(offset) + height > top; }
    void reveal(int offset, int height) {
        if (y(offset) < top) setScroll(offset);
        else if (y(offset) + height > bottom) setScroll(offset + height - (bottom - top));
    }
}
