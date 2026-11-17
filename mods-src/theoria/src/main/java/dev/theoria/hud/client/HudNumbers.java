package dev.theoria.hud.client;

import dev.theoria.hud.HudConfig;
import dev.theoria.hud.HudLayout;
import dev.theoria.hud.HudText;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The centre numbers (docs/04 §1): armor on row A, health on row B, each centred in the gap between the icon columns.
 * Text is never cropped (a cropped number lies: "249" vs "249,3B"): it shrinks to the data floor, then shortens — absorption
 * first, then the maximum ({@link HudText#fit}). Both rows use the smaller of the two scales so they read as one size.
 */
final class HudNumbers {
    private static HudText.Suffixes suffixes;
    private static String suffixesLanguage = "";

    private HudNumbers() {}

    /** Number suffixes come from the active language file ({@code theoria.hud.num.*}); refreshed when the language changes. */
    static HudText.Suffixes suffixes(Minecraft mc) {
        String language = mc.getLanguageManager().getSelected();
        if (suffixes == null || !language.equals(suffixesLanguage)) {
            // Literal keys (tools/check_data.py scans the source for them and fails on a key missing from a language file).
            suffixes = new HudText.Suffixes(Component.translatable("theoria.hud.num.thousand").getString(),
                    Component.translatable("theoria.hud.num.million").getString(), Component.translatable("theoria.hud.num.billion").getString(),
                    Component.translatable("theoria.hud.num.trillion").getString(), Component.translatable("theoria.hud.num.decimal").getString());
            suffixesLanguage = language;
        }
        return suffixes;
    }

    /** @param armorCandidates empty = no armor row (armor 0: vanilla shows nothing either) */
    static void draw(GuiGraphicsExtractor g, Minecraft mc, HudConfig cfg, HudLayout lay, List<String> healthCandidates, List<String> armorCandidates) {
        Font font = mc.font;
        double room = lay.textRoom();
        double min = cfg.layout().textMinScale();
        HudText.Fitted health = HudText.fit(healthCandidates, room, font::width, min);
        HudText.Fitted armor = armorCandidates.isEmpty() ? null : HudText.fit(armorCandidates, room, font::width, min);
        double scale = armor == null ? health.scale() : Math.min(health.scale(), armor.scale());
        label(g, font, cfg, lay, health.text(), scale, lay.yB(), cfg.text().health());
        if (armor != null) label(g, font, cfg, lay, armor.text(), scale, lay.yA(), cfg.text().armor());
    }

    /**
     * Centres {@code text} horizontally in the gap and vertically on the icon row. The vanilla digit block is
     * {@code glyphHeight} rows tall at the top of the 9-row line, so the block (not the line) is what gets centred.
     */
    private static void label(GuiGraphicsExtractor g, Font font, HudConfig cfg, HudLayout lay, String text, double scale, int rowY, int colour) {
        float s = (float) scale;
        float x = lay.centre() - font.width(text) * s / 2f;
        float y = rowY + (cfg.layout().iconSize() - cfg.layout().glyphHeight() * s) / 2f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.text(font, text, 0, 0, colour, true);
        g.pose().popMatrix();
    }
}
