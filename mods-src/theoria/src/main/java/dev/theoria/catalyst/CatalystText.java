package dev.theoria.catalyst;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Small text helpers shared by tooltips, the forge preview and the checks. Language-neutral (numerals, number formats); wording lives in lang files. */
public final class CatalystText {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private CatalystText() {}

    public static String roman(int level) {
        return level >= 1 && level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
    }

    /** A catalyst value with the precision a player needs: 0.32 / 1.3 / 14.9 / 2400. Always a dot (Locale.ROOT); the game's own number formatting is not worth the surprise. */
    public static String number(double value) {
        double abs = Math.abs(value);
        String s = abs >= 100 ? String.format(Locale.ROOT, "%.0f", value) : abs >= 10 ? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%.2f", value);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    public static Component signed(double value) {
        return Component.literal((value >= 0 ? "+" : "−") + number(Math.abs(value)));
    }
}
