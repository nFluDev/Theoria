package dev.theoria.stats.client;

import dev.theoria.stats.StatsData;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** How the Status screen words a stat and its effects. All strings are lang keys: {@code theoria.stat.<id>[.desc]}, {@code theoria.stat.effect.<effect>}. */
public final class StatsText {
    private static final NumberFormat GROUPED = NumberFormat.getIntegerInstance(Locale.ROOT);

    private StatsText() {}

    public static Component name(String stat) {
        return Component.translatable("theoria.stat." + stat);
    }

    public static Component description(String stat) {
        return Component.translatable("theoria.stat." + stat + ".desc");
    }

    /** Percent-shaped effects are stored as fractions (0,30 = +30 %); the rest as plain numbers. */
    public static boolean isFraction(StatsData.Effect effect) {
        return switch (effect.id()) {
            case "movement_speed", "crit_chance", "crit_damage", "exhaustion", "knockback_resistance" -> true;
            default -> false;
        };
    }

    /** The number as shown: fractions ×100, one decimal ("12.3"), thin groupings left out (values stay small). */
    public static String number(StatsData.Effect effect, double value) {
        double shown = isFraction(effect) ? value * 100.0 : value;
        return String.format(Locale.ROOT, shown >= 100 ? "%.0f" : "%.1f", shown);
    }

    public static Component line(StatsData.Effect effect, double value) {
        return Component.translatable("theoria.stat.effect." + effect.id(), number(effect, value));
    }

    /** Big integers with separators for XP ("17,400,152,176"). */
    public static String grouped(long value) {
        return GROUPED.format(value);
    }
}
