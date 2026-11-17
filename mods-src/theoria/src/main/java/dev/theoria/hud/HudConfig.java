package dev.theoria.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Phase 3 HUD constants: layout, palettes, tints. Single source {@code data/hud/hud.json} (copied into the jar as
 * {@code /theoria/hud.json} by Gradle); tools/hud_model.py reads the same file. Pure — no Minecraft classes, so the
 * model checks run on a dedicated server too.
 */
public record HudConfig(Layout layout, double healthUnit, int[] healthPalette, double armorUnit, int[] armorPalette, Tints tints, Effects effects,
        TextColors text) {
    /** Palette entry for "draw the vanilla sprite untinted" (layer 0). Real colours always carry alpha 0xFF. */
    public static final int VANILLA = 0;
    private static final String RESOURCE = "/theoria/hud.json";
    private static HudConfig loaded;

    public record Layout(int icons, int quartersPerIcon, int iconSize, int iconStep, int rowHeight, int bottom, int reservedRows,
            int barEdge, int textPad, double textMinScale, int glyphHeight, int xpBarHeight, double xpTextScale, double xpTextOutline) {
        /** Quarters in one layer (a "kat"): 5 icons x 4 = 20 (decision D1). */
        public int layerQuarters() {
            return icons * quartersPerIcon;
        }

        /** Width in GUI pixels of one icon row. */
        public int barWidth() {
            return (icons - 1) * iconStep + iconSize;
        }
    }

    public record Tints(int poison, int wither, int frozen, int saturation, int blink) {}

    /** Vanilla's own HUD effects (Hud.java), D18: damage blink, regeneration wave, low-health jitter, hunger shake. */
    public record Effects(int blinkDamageTicks, int blinkHealTicks, int blinkPhaseTicks, int displayHoldTicks, double lowHealthFraction,
            int lowHealthJitter, int regenLift, int regenStepTicks, int regenPause, int hungerShake) {}

    public record TextColors(int health, int armor, int xpLevel) {}

    public static synchronized HudConfig get() {
        if (loaded == null) loaded = load();
        return loaded;
    }

    public static HudConfig load() {
        try (InputStream in = HudConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + RESOURCE);
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static HudConfig parse(JsonObject json) {
        JsonObject lo = json.getAsJsonObject("layout");
        Layout layout = new Layout(lo.get("icons").getAsInt(), lo.get("quarters_per_icon").getAsInt(), lo.get("icon_size").getAsInt(),
                lo.get("icon_step").getAsInt(), lo.get("row_height").getAsInt(), lo.get("bottom").getAsInt(), lo.get("reserved_rows").getAsInt(),
                lo.get("bar_edge").getAsInt(), lo.get("text_pad").getAsInt(), lo.get("text_min_scale").getAsDouble(),
                lo.get("glyph_height").getAsInt(), lo.get("xp_bar_height").getAsInt(), lo.get("xp_text_scale").getAsDouble(),
                lo.get("xp_text_outline").getAsDouble());
        JsonObject tints = json.getAsJsonObject("tints");
        JsonObject fx = json.getAsJsonObject("effects");
        JsonObject text = json.getAsJsonObject("text");
        JsonObject health = json.getAsJsonObject("health");
        JsonObject armor = json.getAsJsonObject("armor");
        return new HudConfig(layout, health.get("unit").getAsDouble(), palette(health.getAsJsonArray("palette")), armor.get("unit").getAsDouble(),
                palette(armor.getAsJsonArray("palette")),
                new Tints(color(tints.get("effect_poison").getAsString()), color(tints.get("effect_wither").getAsString()),
                        color(tints.get("effect_frozen").getAsString()), color(tints.get("saturation").getAsString()),
                        color(tints.get("blink").getAsString())),
                new Effects(fx.get("blink_damage_ticks").getAsInt(), fx.get("blink_heal_ticks").getAsInt(), fx.get("blink_phase_ticks").getAsInt(),
                        fx.get("display_hold_ticks").getAsInt(), fx.get("low_health_fraction").getAsDouble(), fx.get("low_health_jitter").getAsInt(),
                        fx.get("regen_lift").getAsInt(), fx.get("regen_step_ticks").getAsInt(), fx.get("regen_pause").getAsInt(),
                        fx.get("hunger_shake").getAsInt()),
                new TextColors(color(text.get("health").getAsString()), color(text.get("armor").getAsString()),
                        color(text.get("xp_level").getAsString())));
    }

    private static int[] palette(JsonArray array) {
        int[] colours = new int[array.size()];
        for (int i = 0; i < colours.length; i++) colours[i] = color(array.get(i).getAsString());
        return colours;
    }

    /** "vanilla" → {@link #VANILLA}; "RRGGBB" → opaque ARGB; "AARRGGBB" as is. */
    static int color(String text) {
        if (text.equals("vanilla")) return VANILLA;
        int value = (int) Long.parseLong(text, 16);
        return text.length() == 8 ? value : 0xFF000000 | value;
    }
}
