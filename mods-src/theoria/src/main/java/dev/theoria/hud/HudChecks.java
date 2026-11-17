package dev.theoria.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.theoria.balance.BalanceProbe;
import dev.theoria.core.check.Checks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Area "hud" of {@code /theoria test}, and the {@code hud_model} GameTest: the Java model against the reference model.
 * The expected values are tables written by tools/hud_model.py ({@code data/hud/hud_cases.csv}, {@code hud_text_cases.csv},
 * shipped in the jar) — so Python and Java cannot drift apart silently. Pure (no client classes): runs on a dedicated server too.
 * What cannot be checked here — how it looks — is the user's approval (CLAUDE.md: no screenshots).
 */
public final class HudChecks {
    private static final String BARS = "/theoria/hud_cases.csv";
    private static final String TEXTS = "/theoria/hud_text_cases.csv";
    private static final String EFFECTS = "/theoria/hud_fx_cases.csv";

    private HudChecks() {}

    public static void init() {
        Checks.register("hud", "config", source -> {
            HudConfig cfg = HudConfig.get();
            HudConfig.Layout lo = cfg.layout();
            Checks.require(lo.layerQuarters() == 20, "a layer must be 5 icons x 4 quarters = 20 (D1), got " + lo.layerQuarters());
            Checks.require(cfg.healthPalette()[0] == HudConfig.VANILLA && cfg.armorPalette()[0] == HudConfig.VANILLA, "layer 0 must be vanilla");
            return String.format(Locale.ROOT, "%d health colours, %d armor colours (%s armor per quarter), layer %d quarters, row %d px",
                    cfg.healthPalette().length, cfg.armorPalette().length, cfg.armorUnit(), lo.layerQuarters(), lo.rowHeight());
        });
        Checks.register("hud", "bar_cases", source -> {
            List<String> bad = new ArrayList<>();
            int n = verifyBars(bad);
            Checks.require(bad.isEmpty(), first(bad, n, "bar"));
            return n + " bar/geometry cases match tools/hud_model.py";
        });
        Checks.register("hud", "fx_cases", source -> {
            List<String> bad = new ArrayList<>();
            int n = verifyEffects(bad);
            Checks.require(bad.isEmpty(), first(bad, n, "effect"));
            return n + " blink/regeneration/low-health/hunger/tracker cases match tools/hud_model.py";
        });
        Checks.register("hud", "text_cases", source -> {
            List<String> bad = new ArrayList<>();
            int n = verifyTexts(bad);
            Checks.require(bad.isEmpty(), first(bad, n, "text"));
            return n + " number/fit cases match tools/hud_model.py";
        });
    }

    /** The failure message — built only on failure ({@code require} evaluates its message eagerly, so it must not index an empty list). */
    private static String first(List<String> mismatches, int total, String what) {
        return mismatches.isEmpty() ? "" : mismatches.size() + " of " + total + " " + what + " cases differ, first: " + mismatches.get(0);
    }

    /** Compares every bar/geometry case; appends one line per mismatch; returns the case count. */
    public static int verifyBars(List<String> mismatches) {
        HudConfig cfg = HudConfig.get();
        int layerQuarters = cfg.layout().layerQuarters();
        List<List<String>> rows = table(BARS);
        for (List<String> row : rows) {
            String id = row.get(0), kind = row.get(1), expect = row.get(4);
            double a = Double.parseDouble(row.get(2)), b = Double.parseDouble(row.get(3));
            String got = switch (kind) {
                case "health", "vehicle" -> StatusModel.health(cfg, a).notation(cfg); // b (the maximum) must not matter
                case "armor" -> StatusModel.armor(cfg, a).notation(cfg);
                case "food", "saturation" -> new StatusModel.Fill(0, -1, StatusModel.single(a, 20, layerQuarters)).notation(cfg);
                case "absorption" -> new StatusModel.Fill(0, -1, StatusModel.single(a, b, layerQuarters)).notation(cfg);
                case "geom" -> HudLayout.of(cfg, (int) a, (int) b).notation();
                default -> "unknown kind " + kind;
            };
            if (!got.equals(expect)) mismatches.add(id + ": expected " + expect + ", got " + got);
        }
        return rows.size();
    }

    /** Compares every vanilla-effect decision (blink phase, regeneration wave, low health, hunger shake, blink tracker). */
    public static int verifyEffects(List<String> mismatches) {
        HudConfig cfg = HudConfig.get();
        List<List<String>> rows = table(EFFECTS);
        for (List<String> row : rows) {
            String id = row.get(0), kind = row.get(1), expect = row.get(3);
            String[] p = row.get(2).split("\\|");
            String got = switch (kind) {
                case "blink" -> HudEffects.blinkActive(Long.parseLong(p[0]), Long.parseLong(p[1]), cfg.effects().blinkPhaseTicks()) ? "1" : "0";
                case "regen" -> String.valueOf(HudEffects.regenLifted(cfg, Long.parseLong(p[0])));
                case "low" -> HudEffects.isLowHealth(cfg, Double.parseDouble(p[0]), Double.parseDouble(p[1])) ? "1" : "0";
                case "hunger" -> HudEffects.hungerShakes(Long.parseLong(p[0]), Integer.parseInt(p[1]), Double.parseDouble(p[2])) ? "1" : "0";
                case "tracker" -> tracker(cfg, p[0]);
                default -> "unknown kind " + kind;
            };
            if (!got.equals(expect)) mismatches.add(id + ": expected '" + expect + "', got '" + got + "'");
        }
        return rows.size();
    }

    /** "tick:health:invulnerable;…" → "display|blinking" after the last step. */
    private static String tracker(HudConfig cfg, String script) {
        HealthTracker tracker = new HealthTracker(cfg.effects());
        long tick = 0;
        for (String step : script.split(";")) {
            String[] v = step.split(":");
            tick = Long.parseLong(v[0]);
            tracker.update(Integer.parseInt(v[1]), v[2].equals("1"), tick);
        }
        return tracker.display() + "|" + (tracker.blinking(tick) ? 1 : 0);
    }

    /** Compares every number-format / text-fit case with the real language files. */
    public static int verifyTexts(List<String> mismatches) {
        HudConfig cfg = HudConfig.get();
        List<List<String>> rows = table(TEXTS);
        for (List<String> row : rows) {
            String id = row.get(0), kind = row.get(1), args = row.get(3), expect = row.get(4);
            HudText.Suffixes suffixes = suffixes(row.get(2));
            String[] p = args.split("\\|");
            double minScale = cfg.layout().textMinScale();
            String got;
            switch (kind) {
                case "compact" -> got = HudText.compact(Double.parseDouble(p[0]), suffixes);
                case "health_text" -> got = format(HudText.fit(HudText.healthCandidates(Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]), suffixes), Double.parseDouble(p[3]), HudText::width6, minScale));
                case "armor_text" -> got = format(HudText.fit(HudText.armorCandidates(Double.parseDouble(p[0]), Double.parseDouble(p[1]), suffixes),
                        Double.parseDouble(p[2]), HudText::width6, minScale));
                default -> got = "unknown kind " + kind;
            }
            if (!got.equals(expect)) mismatches.add(id + ": expected '" + expect + "', got '" + got + "'");
        }
        return rows.size();
    }

    private static String format(HudText.Fitted fitted) {
        return fitted.text() + "|" + String.format(Locale.ROOT, "%.3f", fitted.scale());
    }

    /** Suffixes straight from the language file in the jar — the same strings the client resolves through the language system. */
    static HudText.Suffixes suffixes(String lang) {
        String path = "/assets/theoria/lang/" + lang + ".json";
        try (InputStream in = HudChecks.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new HudText.Suffixes(json.get("theoria.hud.num.thousand").getAsString(), json.get("theoria.hud.num.million").getAsString(),
                    json.get("theoria.hud.num.billion").getAsString(), json.get("theoria.hud.num.trillion").getAsString(),
                    json.get("theoria.hud.num.decimal").getAsString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<List<String>> table(String resource) {
        List<List<String>> rows = new ArrayList<>();
        try (InputStream in = HudChecks.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.readLine(); // header
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) rows.add(BalanceProbe.splitCsv(line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
