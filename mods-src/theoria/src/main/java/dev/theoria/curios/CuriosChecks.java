package dev.theoria.curios;

import dev.theoria.core.check.Checks;
import java.util.Map;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.type.ISlotType;

/**
 * Area "curios" of {@code /theoria test}: the slot types the game really loaded (≥ 30 slots for the player, the DoD of Phase 11, and few enough for one page of the panel) and the
 * panel geometry swept over every slot count and the smallest GUI. The menu, the cosmetic row, the open path and the stats-neutrality are the {@code curios_*} and
 * {@code cosmetics_*} GameTests.
 */
public final class CuriosChecks {
    /** Phase 11's bar for "Curios Max": at least this many Curios slots on the player. */
    public static final int MIN_SLOTS = 30;

    private CuriosChecks() {}

    public static void init() {
        Checks.register("curios", "slots", source -> {
            Map<String, ISlotType> types = CuriosSlotTypes.getDefaultPlayerSlotTypes(false);
            int total = 0;
            for (ISlotType type : types.values()) total += type.getSize();
            Checks.require(total >= MIN_SLOTS, "the player has " + total + " Curios slots, Curios Max promises at least " + MIN_SLOTS);
            Checks.require(total <= CuriosMaxLayout.MAX_SLOTS, "the player has " + total + " Curios slots; the Max panel shows one page of at most " + CuriosMaxLayout.MAX_SLOTS + " — add paging before adding more");
            for (String id : java.util.List.of("ring", "necklace", "belt", "bracelet", "charm", "back", "head", "body", "hands", "feet", "curio")) {
                Checks.require(types.containsKey(id), "slot type '" + id + "' is not loaded for the player");
            }
            return types.size() + " slot types, " + total + " slots for the player (≥ " + MIN_SLOTS + ", ≤ " + CuriosMaxLayout.MAX_SLOTS + ")";
        });
        Checks.register("curios", "menu_ranges", source -> {
            Checks.require(CuriosMaxMenu.CURIO_START == 1 + 4 + 4 + 36 + 1 && CuriosMaxMenu.INVENTORY_START == 9 && CuriosMaxMenu.INVENTORY_END == 45,
                    "Curios' slot order: result, 4 crafting, 4 armor, 36 inventory, offhand, then Curios (its quick-move code hard-codes these)");
            Checks.require(CuriosMaxLayout.COSMETIC_CELLS == CuriosMaxLayout.MIN_COLUMNS && CuriosMaxLayout.WINGS_CELL == CuriosMaxLayout.COSMETIC_CELLS - 1, "the cosmetic row is 4 slots + the Wings cell = the minimum column count");
            return "46 fixed slots, Curios after them, the 4 cosmetic slots last; the cosmetic row spans the " + CuriosMaxLayout.MIN_COLUMNS + " minimum columns";
        });
        Checks.register("curios", "layout", source -> {
            int sizes = 0;
            int worstHeight = 0;
            for (int n = 0; n <= CuriosMaxLayout.MAX_SLOTS; n++) {
                int columns = CuriosMaxLayout.columns(n);
                Checks.require(columns >= CuriosMaxLayout.MIN_COLUMNS && columns <= CuriosMaxLayout.MAX_COLUMNS, n + " slots → " + columns + " columns out of range");
                Checks.require(columns * (CuriosMaxLayout.rows(n) - 1) >= n, n + " slots do not fit " + columns + " columns × " + (CuriosMaxLayout.rows(n) - 1) + " rows");
                int height = CuriosMaxLayout.panelHeight(CuriosMaxLayout.rows(n));
                worstHeight = Math.max(worstHeight, height);
                Checks.require(height <= 240 - 2 * 8, n + " slots → panel " + height + " px tall, the smallest GUI is 240");
                // classic Curios arithmetic: the panel's slot area is 14 + 18·columns wide, and cell 0 sits at 7 − that, left of the inventory image
                int width = CuriosMaxLayout.panelWidth(columns);
                Checks.require(width == 14 + 18 * columns && CuriosMaxLayout.cellX(0, width) == 7 - width, "classic panel arithmetic");
                Checks.require(CuriosMaxLayout.cellX(columns - 1, width) + 16 < 0, "the whole panel hangs left of the inventory image");
                sizes++;
            }
            // the smallest vanilla GUI is 320×240: with the panel hung off the inventory's left edge everything must still be on screen for the real slot count
            int real = CuriosMaxLayout.MAX_SLOTS;
            int fit = 0;
            for (int n : new int[] {0, 30, 36, 40, 48}) {
                int columns = CuriosMaxLayout.columns(n);
                int left = CuriosMaxLayout.leftPos(320, columns);
                Checks.require(left - CuriosMaxLayout.panelWidth(columns) >= 0 && left + CuriosMaxLayout.IMAGE_W <= 320, n + " slots do not fit a 320-wide GUI (left " + left + ")");
                fit++;
            }
            for (int w : new int[] {320, 400, 427, 480, 640, 1280}) {
                int left = CuriosMaxLayout.leftPos(w, 5);
                Checks.require(left >= CuriosMaxLayout.panelWidth(5) + 6 && left + CuriosMaxLayout.IMAGE_W <= w, "5 columns do not fit a " + w + "-wide GUI");
            }
            return sizes + " slot counts (0.." + real + "): columns 5-8, panel ≤ " + worstHeight + " px tall; up to 48 slots fit a 320-wide GUI; 6 GUI widths checked with the real 5-column panel";
        });
    }
}
