package dev.theoria.hud.client;

import dev.theoria.hud.HudConfig;
import dev.theoria.hud.HudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.network.chat.Component;

/**
 * The experience level, written INSIDE the XP bar (docs/04 §1). Vanilla draws it above the bar (guiHeight - 35), right where
 * the health number sits now. We keep vanilla's bar untouched — it shares the contextual slot with the locator and the
 * jump-bar (ADR-002) — and replace only the {@code experience_level} layer, which runs right after the bar's own layer.
 *
 * <p>The bar is 5 px tall: the digits are scaled to fit ({@code xp_text_scale}) and outlined thinly so they read on both the
 * dark track and the green fill. Drawn only while the contextual bar IS the XP bar; over the locator or jump bar the level is
 * skipped rather than cluttering them.
 */
final class LevelLayer {
    private static final float[][] OUTLINE = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private LevelLayer() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || mc.gui.hud.isHidden()) return;
        int level = mc.player.experienceLevel;
        if (!mc.gameMode.hasExperience() || level <= 0) return;
        // The Hud decides each frame which bar owns the slot (experience / locator / jumpable vehicle); AT makes the field readable.
        if (!(mc.gui.hud.contextualInfoBar.getSecond() instanceof ExperienceBar)) return;
        HudClient.levelDraws++;

        HudConfig cfg = HudConfig.get();
        HudLayout lay = HudLayout.of(cfg, g.guiWidth(), g.guiHeight());
        Font font = mc.font;
        Component text = Component.literal(Integer.toString(level)); // vanilla's gui.experience.level is just "%s"
        float scale = (float) cfg.layout().xpTextScale();
        float x = lay.centre() - font.width(text) * scale / 2f;
        float y = lay.xpTop() + (cfg.layout().xpBarHeight() - cfg.layout().glyphHeight() * scale) / 2f;
        float outline = (float) cfg.layout().xpTextOutline(); // GUI pixels
        for (float[] o : OUTLINE) draw(g, font, text, x + o[0] * outline, y + o[1] * outline, scale, 0xFF000000);
        draw(g, font, text, x, y, scale, cfg.text().xpLevel());
    }

    private static void draw(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, colour, false);
        g.pose().popMatrix();
    }
}
