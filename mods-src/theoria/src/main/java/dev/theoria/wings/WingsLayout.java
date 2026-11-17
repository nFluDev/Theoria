package dev.theoria.wings;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the wings screen and of the button row under the ESC menu, as pure functions of the (GUI-scaled) screen size —
 * so {@code /theoria test wings} can sweep every window / GUI scale combination without a window. Vanilla never lets the GUI be
 * smaller than 320×240 scaled pixels; everything here is designed for that floor (no overlap, nothing off screen).
 * Screen: 3D player on the left, card grid on the right (scrolls when the catalog outgrows the space), two buttons under both.
 */
public final class WingsLayout {
    public static final int CARD_W = 108;
    public static final int CARD_H = 22;
    public static final int GAP = 4;
    public static final int PREVIEW_W_MAX = 120;
    public static final int PREVIEW_W_MIN = 64;
    public static final int PREVIEW_H_MAX = 176;
    public static final int SECTION_GAP = 14;
    /** Room right of the grid for the scrollbar — reserved always, so the layout does not jump when scrolling starts. */
    public static final int SCROLLBAR_W = 8;
    public static final int MARGIN = 8;
    public static final int TITLE_Y = 14;
    public static final int CONTENT_TOP = 32;
    public static final int BUTTON_H = 20;
    public static final int BOTTOM_MARGIN = 8;
    /** Width of the vanilla ESC menu column (two 98 px buttons + gap are laid out inside 204). */
    public static final int PAUSE_COLUMN_W = 204;
    /** Widgets whose centre is further than this from the screen centre are corner / edge buttons of other mods (Essential, …). */
    public static final int PAUSE_CENTRE_TOLERANCE = 60;
    public static final int PAUSE_GAP = 8;
    public static final int PAUSE_MARGIN = 6;

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean inside(int sw, int sh) {
            return x >= 0 && y >= 0 && right() <= sw && bottom() <= sh;
        }

        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    /** {@code cards} are at scroll offset 0; the screen shifts them up by the scroll offset (0 … {@code maxScroll}). */
    public record Screen(int columns, Rect preview, Rect list, List<Rect> cards, int contentHeight, int maxScroll, Rect remove, Rect done) {}

    private WingsLayout() {}

    public static Screen compute(int sw, int sh, int count) {
        int columns = sw >= 372 ? 2 : 1;
        int gridW = columns * CARD_W + (columns - 1) * GAP;
        int previewW = Math.max(PREVIEW_W_MIN, Math.min(PREVIEW_W_MAX, sw - 2 * MARGIN - SECTION_GAP - gridW - SCROLLBAR_W));
        int total = previewW + SECTION_GAP + gridW + SCROLLBAR_W;
        int left = Math.max(MARGIN, (sw - total) / 2);
        int buttonsY = sh - BOTTOM_MARGIN - BUTTON_H;
        int avail = Math.max(0, buttonsY - GAP * 2 - CONTENT_TOP);

        Rect preview = new Rect(left, CONTENT_TOP, previewW, Math.min(PREVIEW_H_MAX, avail));
        Rect list = new Rect(left + previewW + SECTION_GAP, CONTENT_TOP, gridW + SCROLLBAR_W, avail);
        int rows = (count + columns - 1) / columns;
        int contentHeight = rows == 0 ? 0 : rows * (CARD_H + GAP) - GAP;
        List<Rect> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(new Rect(list.x() + (i % columns) * (CARD_W + GAP), CONTENT_TOP + (i / columns) * (CARD_H + GAP), CARD_W, CARD_H));
        }
        int buttonW = (total - SCROLLBAR_W - GAP) / 2;
        Rect remove = new Rect(left, buttonsY, buttonW, BUTTON_H);
        Rect done = new Rect(left + buttonW + GAP, buttonsY, buttonW, BUTTON_H);
        return new Screen(columns, preview, list, List.copyOf(cards), contentHeight, Math.max(0, contentHeight - avail), remove, done);
    }

    /**
     * The mod button row under the vanilla ESC column ({@code columnTop/Bottom} = extent of the vanilla centre column, measured by the
     * caller). Below the column when it fits, clamped into the screen; if clamping would sit it on the column, above the column instead
     * (an overlapping button is worse than one in an odd place — LucidRem lesson, 45.5). {@code count} buttons share the column width.
     */
    public static List<Rect> pauseRow(int columnTop, int columnBottom, int sw, int sh, int count) {
        int buttonW = (PAUSE_COLUMN_W - GAP * (count - 1)) / count;
        int left = (sw - PAUSE_COLUMN_W) / 2;
        int y = Math.min(columnBottom + PAUSE_GAP, sh - BUTTON_H - PAUSE_MARGIN);
        if (y < columnBottom && columnTop - BUTTON_H - PAUSE_GAP >= PAUSE_MARGIN) {
            y = columnTop - BUTTON_H - PAUSE_GAP;
        }
        List<Rect> row = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            row.add(new Rect(left + i * (buttonW + GAP), y, buttonW, BUTTON_H));
        }
        return row;
    }
}
