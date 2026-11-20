package dev.theoria.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the Status screen as a pure function of the (GUI-scaled) screen size and the number of stats, so {@code /theoria test stats} can sweep every window / GUI
 * scale without a window (the WingsLayout approach). Vanilla never scales the GUI below 320×240; the layout is designed for that floor: nothing overlaps, nothing is off screen.
 * Left: the 3D player. Right: one row per stat (bar + [−] count [+]). Under both: the XP bar, the points line, and the Apply / Clear / Done buttons.
 */
public final class StatsLayout {
    public static final int MARGIN = 8;
    public static final int TITLE_Y = 10;
    public static final int CONTENT_TOP = 26;
    public static final int GAP = 3;
    public static final int SECTION_GAP = 12;
    public static final int PREVIEW_W_MIN = 64;
    public static final int PREVIEW_W_MAX = 120;
    public static final int PREVIEW_H_MAX = 176;
    public static final int ROW_H_MIN = 14;
    public static final int ROW_H_MAX = 20;
    public static final int STEP_W = 14;     // the [−] and [+] buttons
    public static final int COUNT_W = 30;    // allocated points between them
    public static final int LIST_W_MIN = 190;
    public static final int LIST_W_MAX = 260;
    public static final int XP_BAR_H = 12;
    public static final int INFO_H = 10;
    public static final int BUTTON_H = 20;
    public static final int BOTTOM_MARGIN = 8;

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

        public boolean intersects(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }
    }

    public record Row(Rect bar, Rect minus, Rect count, Rect plus) {
        public Rect union() {
            return new Rect(bar.x(), bar.y(), plus.right() - bar.x(), bar.h());
        }
    }

    public record Screen(Rect preview, List<Row> rows, Rect xpBar, Rect info, Rect apply, Rect clear, Rect done, int left, int width) {}

    private StatsLayout() {}

    public static Screen compute(int sw, int sh, int rowCount) {
        int bottomBlock = XP_BAR_H + GAP + INFO_H + GAP + BUTTON_H + BOTTOM_MARGIN;
        int contentBottom = sh - bottomBlock - GAP;
        int avail = Math.max(0, contentBottom - CONTENT_TOP);
        int listW = Math.max(LIST_W_MIN, Math.min(LIST_W_MAX, sw - 2 * MARGIN - SECTION_GAP - PREVIEW_W_MIN));
        int previewW = Math.max(PREVIEW_W_MIN, Math.min(PREVIEW_W_MAX, sw - 2 * MARGIN - SECTION_GAP - listW));
        int width = previewW + SECTION_GAP + listW;
        int left = Math.max(MARGIN, (sw - width) / 2);
        Rect preview = new Rect(left, CONTENT_TOP, previewW, Math.min(PREVIEW_H_MAX, avail));
        int rowH = rowCount <= 0 ? ROW_H_MAX : Math.max(ROW_H_MIN, Math.min(ROW_H_MAX, (avail - (rowCount - 1) * GAP) / rowCount));
        int listX = left + previewW + SECTION_GAP;
        int controls = STEP_W + 2 + COUNT_W + 2 + STEP_W;
        List<Row> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            int y = CONTENT_TOP + i * (rowH + GAP);
            int barW = listW - controls - GAP;
            Rect bar = new Rect(listX, y, barW, rowH);
            int cx = bar.right() + GAP;
            Rect minus = new Rect(cx, y, STEP_W, rowH);
            Rect count = new Rect(minus.right() + 2, y, COUNT_W, rowH);
            Rect plus = new Rect(count.right() + 2, y, STEP_W, rowH);
            rows.add(new Row(bar, minus, count, plus));
        }
        int lastBottom = rows.isEmpty() ? CONTENT_TOP : rows.get(rows.size() - 1).bar().bottom();
        int blockTop = Math.max(contentBottom, lastBottom + GAP);
        Rect xpBar = new Rect(left, blockTop, width, XP_BAR_H);
        Rect info = new Rect(left, xpBar.bottom() + GAP, width, INFO_H);
        int buttonW = (width - 2 * GAP) / 3;
        int buttonsY = info.bottom() + GAP;
        Rect apply = new Rect(left, buttonsY, buttonW, BUTTON_H);
        Rect clear = new Rect(apply.right() + GAP, buttonsY, buttonW, BUTTON_H);
        Rect done = new Rect(clear.right() + GAP, buttonsY, width - 2 * (buttonW + GAP), BUTTON_H);
        return new Screen(preview, List.copyOf(rows), xpBar, info, apply, clear, done, left, width);
    }
}
