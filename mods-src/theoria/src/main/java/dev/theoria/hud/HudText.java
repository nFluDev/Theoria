package dev.theoria.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Number text beside the bars (docs/04 §1): compact large numbers, and fit a string into a room by shrinking first
 * (down to the data floor), then shortening (absorption first, then the maximum). Pure — the font width is a parameter;
 * mirror of tools/hud_model.py ({@code compact}, {@code fit}, {@code health_candidates}, {@code armor_candidates}).
 */
public final class HudText {
    /** Number suffixes and decimal separator; the client reads them from the language files ({@code theoria.hud.num.*}). */
    public record Suffixes(String thousand, String million, String billion, String trillion, String decimal) {}

    /** The chosen string and the scale (1.0 = natural size) to draw it at. */
    public record Fitted(String text, double scale) {}

    private HudText() {}

    /** Up to 9999 whole; then one decimal and a suffix: 249295 → "249,3B" (tr) / "249.3K" (en). 999 950 promotes to "1M". */
    public static String compact(double value, Suffixes s) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "?";
        if (value < 0) return "0";
        if (value < 10_000) return String.valueOf((long) value);
        double[][] steps = {{1e6, 1e3}, {1e9, 1e6}, {1e12, 1e9}, {Double.POSITIVE_INFINITY, 1e12}};
        String[] suffix = {s.thousand(), s.million(), s.billion(), s.trillion()};
        for (int i = 0; i < steps.length; i++) {
            if (value < steps[i][0]) {
                long tenths = (long) Math.floor(value / steps[i][1] * 10 + 0.5);
                if (tenths >= 10_000 && i < steps.length - 1) continue; // 999,96B is "1M", not "1000B"
                long whole = tenths / 10, frac = tenths % 10;
                return (frac == 0 ? String.valueOf(whole) : whole + s.decimal() + frac) + suffix[i];
            }
        }
        return "?";
    }

    /** Tries the candidates (most informative first), shrinking each down to {@code minScale}; the last one is the floor. */
    public static Fitted fit(List<String> candidates, double room, ToIntFunction<String> width, double minScale) {
        for (String text : candidates) {
            int w = width.applyAsInt(text);
            double scale = w <= 0 ? 1.0 : Math.min(1.0, room / w);
            if (scale >= minScale) return new Fitted(text, scale);
        }
        return new Fitted(candidates.get(candidates.size() - 1), minScale);
    }

    /** "cur/max (+absorption)" → "cur/max" → "cur". Current health rounds up (0,5 HP reads 1), like the hearts. */
    public static List<String> healthCandidates(double current, double maximum, double absorption, Suffixes s) {
        String c = compact(Math.ceil(current - StatusModel.EPS), s);
        String full = c + "/" + compact(Math.floor(maximum + 0.5), s);
        List<String> out = new ArrayList<>();
        if (absorption > 0) out.add(full + " (+" + compact(Math.ceil(absorption - StatusModel.EPS), s) + ")");
        out.add(full);
        out.add(c);
        return out;
    }

    /** "armor (+toughness)" → "armor". */
    public static List<String> armorCandidates(double armor, double toughness, Suffixes s) {
        String a = compact((int) armor, s);
        List<String> out = new ArrayList<>();
        if (toughness > 0) out.add(a + " (+" + compact(Math.floor(toughness + 0.5), s) + ")");
        out.add(a);
        return out;
    }

    /** Test stand-in for the font: 6 px per character (Python mirror: {@code width6}). */
    public static int width6(String text) {
        return 6 * text.length();
    }
}
