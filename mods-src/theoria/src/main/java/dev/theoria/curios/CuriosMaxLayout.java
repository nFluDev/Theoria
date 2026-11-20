package dev.theoria.curios;

/**
 * Geometry of the Curios Max panel as constants and pure functions, so {@code /theoria test curios} can sweep slot counts and GUI sizes without a window. The panel IS Curios'
 * classic left panel (same texture, same columns rule — {@code columns = ceil(slots / 8)}, at least 5 here — same slot origin), with ONE extra row on top: the cosmetic row
 * (4 cosmetic armor slots + the Wings cell, 5 cells = the minimum column count), followed by the Curios slots. Slot x/y are final in 26.3, so the menu creates every slot at the
 * cell it is drawn in; positions are relative to the inventory image's top-left (the panel hangs off its left edge, hence the negative x).
 *
 * <pre>
 *   row 0   [head][chest][legs][feet][Wings]       ← cosmetic row (a separator line is drawn under it)
 *   row 1…  Curios slots, left to right, top to bottom
 * </pre>
 */
public final class CuriosMaxLayout {
    public static final int SLOT = 18;
    /** The cosmetic row has 5 cells (4 armor + the Wings cell): the panel is never narrower. */
    public static final int MIN_COLUMNS = 5;
    public static final int MAX_COLUMNS = 8;
    /** Classic Curios fills 8 rows per column before it adds a column. */
    public static final int ROWS_PER_COLUMN = 8;
    public static final int COSMETIC_CELLS = 5;
    /** Index of the cell in the cosmetic row that is a button (opens the Wings screen), not a slot. */
    public static final int WINGS_CELL = 4;
    public static final int FIRST_ROW_Y = 8;
    /** One page only (no paging in Max): this many Curios slots fit the panel at the smallest GUI height. */
    public static final int MAX_SLOTS = MAX_COLUMNS * ROWS_PER_COLUMN;
    /** The inventory image the panel hangs off (vanilla 176 wide). */
    public static final int IMAGE_W = 176;

    private CuriosMaxLayout() {}

    public static int columns(int curioSlots) {
        int calculated = (int) Math.ceil(curioSlots / (double) ROWS_PER_COLUMN);
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, calculated));
    }

    /** Width of the panel's slot area incl. its frame: classic {@code 14 + 18 * columns}. */
    public static int panelWidth(int columns) {
        return 14 + SLOT * columns;
    }

    /** x of cell {@code column} relative to the inventory image's left edge (negative: the panel is to its left). */
    public static int cellX(int column, int panelWidth) {
        return column * SLOT + 7 - panelWidth;
    }

    /** y of grid row {@code row} (0 = the cosmetic row) relative to the inventory image's top edge. */
    public static int rowY(int row) {
        return FIRST_ROW_Y + row * SLOT;
    }

    /** Rows of the panel including the cosmetic row. */
    public static int rows(int curioSlots) {
        return 1 + (int) Math.ceil(curioSlots / (double) columns(curioSlots));
    }

    /** Height of the panel's frame: classic {@code 7 + rows * 18 + 7}. */
    public static int panelHeight(int rows) {
        return 7 + rows * SLOT + 7;
    }

    /** Where the inventory image's left edge sits on a GUI of {@code guiWidth}: centred, but far enough right for the panel hanging off its left edge to stay on screen. */
    public static int leftPos(int guiWidth, int columns) {
        return Math.max((guiWidth - IMAGE_W) / 2, panelWidth(columns) + 6);
    }
}
