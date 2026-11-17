package dev.theoria.hud;

/**
 * The layered bar model (docs/04 §1, decisions D1, D16, D17) — pure functions; mirror of tools/hud_model.py (change both,
 * then {@code python3 -I tools/check_hud.py --write}; {@code /theoria test hud} and the {@code hud_model} GameTest compare the two).
 *
 * <p>A <em>layer</em> ("kat") is 5 icons x 4 quarters = 20 quarters; one quarter is {@code unit} points (HP: 1). The colour is
 * the ABSOLUTE value's indicator: layer = value / 20, independent of the maximum health. The palette's last colour (white for
 * health, purple for armor) is permanent — everything above it is that colour, full; it never wraps back to red (200, 215 and
 * 235 HP are all white). A completed layer sits underneath ({@code base}); the new one fills from the left quarter by quarter
 * ({@code top}).
 */
public final class StatusModel {
    /** ceil(value / unit) float noise: 3 * (1 / 3.0) must not become 2 quarters. */
    static final double EPS = 1e-9;

    /** Which layer is filling ({@code layer}), the completed layer under it ({@code base}, -1 if none), and its quarters (0..20). */
    public record Fill(int layer, int base, int top) {
        /** Same notation as the case tables: {@code layer;base;top;iconQuarters}. */
        public String notation(HudConfig cfg) {
            return layer + ";" + base + ";" + top + ";" + icons(top, cfg);
        }
    }

    private StatusModel() {}

    /** Units (HP, armor) → quarters, rounded up like vanilla; any value > 0 shows at least one quarter (0,5 HP must not look dead). */
    public static int toQuarters(double value, double unit) {
        if (!(value > 0)) return 0;
        return (int) Math.max(1, Math.ceil(value / unit - EPS));
    }

    public static Fill layered(double value, double unit, int paletteLength, int layerQuarters) {
        int total = toQuarters(value, unit);
        int layer = Math.min(total / layerQuarters, paletteLength - 1);
        // beyond the last layer "top" stays 20: the last colour is permanent and full
        return new Fill(layer, layer - 1, Math.min(total - layer * layerQuarters, layerQuarters));
    }

    /** The maximum health does not change the bar (colour = absolute HP); it only shows in the number. */
    public static Fill health(HudConfig cfg, double current) {
        return layered(current, cfg.healthUnit(), cfg.healthPalette().length, cfg.layout().layerQuarters());
    }

    /** Armor works exactly like health (D17): 5 symbols, quarter by quarter, light-spectrum colours, purple is permanent. */
    public static Fill armor(HudConfig cfg, double armor) {
        return layered(armor, cfg.armorUnit(), cfg.armorPalette().length, cfg.layout().layerQuarters());
    }

    /** Single-layer bars (hunger, saturation, absorption): value/scaleMax at quarter resolution. */
    public static int single(double value, double scaleMax, int layerQuarters) {
        if (!(value > 0) || !(scaleMax > 0)) return 0;
        return (int) Math.min(layerQuarters, Math.max(1, Math.ceil(value / scaleMax * layerQuarters - EPS)));
    }

    /** Quarters of each icon (0..4) for a filled-quarter count: 5 → [4,1,0,0,0]. */
    public static int[] iconQuarters(int top, HudConfig cfg) {
        int[] out = new int[cfg.layout().icons()];
        int per = cfg.layout().quartersPerIcon();
        for (int i = 0; i < out.length; i++) out[i] = Math.max(0, Math.min(per, top - i * per));
        return out;
    }

    public static String icons(int top, HudConfig cfg) {
        StringBuilder sb = new StringBuilder();
        for (int q : iconQuarters(top, cfg)) sb.append(q);
        return sb.toString();
    }

    /** Pixel width of {@code quarters} quarters of one icon: 1/4 → 3, 1/2 → 5, 3/4 → 7, 4/4 → 9 (9 px icon). */
    public static int clipWidth(HudConfig cfg, int quarters) {
        int size = cfg.layout().iconSize();
        int per = cfg.layout().quartersPerIcon();
        return quarters <= 0 ? 0 : quarters >= per ? size : (int) Math.ceil(size * (double) quarters / per);
    }
}
