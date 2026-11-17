package dev.theoria.catalyst;

/**
 * Geometry of the Catalyst Forge GUI, shared by the menu (slot positions) and the client screen (panels, texts). Pixels, relative to the screen's top-left.
 * Two columns: the left one is the vanilla container (target, five inputs, the player's inventory), the right one lists the catalyst slots, the preview and "Uygula".
 */
public final class CatalystLayout {
    public static final int IMAGE_W = 356;
    public static final int IMAGE_H = 166;
    public static final int TARGET_X = 8;
    public static final int TARGET_Y = 20;
    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 20;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    /** Right column. */
    public static final int RIGHT_X = 184;
    public static final int RIGHT_W = 164;
    public static final int SLOT_LIST_Y = 20;
    public static final int SLOT_LINE_H = 12;
    public static final int PREVIEW_Y = 90;
    public static final int PREVIEW_LINE_H = 10;
    public static final int APPLY_Y = 142;
    public static final int APPLY_H = 18;
    /** Remove button ("×") at the right end of each filled slot line. */
    public static final int REMOVE_W = 12;

    private CatalystLayout() {}
}
