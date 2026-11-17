package dev.theoria.devtest;

import dev.theoria.hud.HudChecks;
import dev.theoria.hud.HudConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Phase 3 GameTests. The HUD's looks are the user's approval (no screenshots); what a headless test can prove is that the Java
 * model, geometry, number formatting and the vanilla-effect decisions agree with the reference model in tools/hud_model.py on every case in
 * {@code data/hud/hud_cases.csv} / {@code hud_text_cases.csv} / {@code hud_fx_cases.csv} (shipped in the jar). The drawing itself runs in the unattended
 * client scenario ({@code ./gradlew runDevClient -PdevClient=hud}).
 */
final class HudTests {
    private HudTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("hud_model", HudTests::model);
    }

    private static void model(GameTestHelper h) {
        Dev.Report r = new Dev.Report("hud_model");
        HudConfig cfg = HudConfig.get();
        List<String> mismatches = new ArrayList<>();
        int bars = HudChecks.verifyBars(mismatches);
        int texts = HudChecks.verifyTexts(mismatches);
        int effects = HudChecks.verifyEffects(mismatches);
        r.line("hud.json: %d health colours, %d armor colours (%s armor per quarter), layer = %d quarters", cfg.healthPalette().length,
                cfg.armorPalette().length, cfg.armorUnit(), cfg.layout().layerQuarters());
        r.line("%d bar/geometry + %d number/fit + %d effect cases compared with tools/hud_model.py: %d mismatches", bars, texts, effects,
                mismatches.size());
        mismatches.forEach(line -> r.line("MISMATCH %s", line));
        r.write();
        h.assertTrue(bars >= 50 && texts >= 20 && effects >= 15,
                "case tables unexpectedly small: " + bars + " bar cases, " + texts + " text cases, " + effects + " effect cases");
        h.assertTrue(mismatches.isEmpty(), mismatches.size() + " HUD model mismatches, first: " + (mismatches.isEmpty() ? "-" : mismatches.get(0)));
        h.succeed();
    }
}
