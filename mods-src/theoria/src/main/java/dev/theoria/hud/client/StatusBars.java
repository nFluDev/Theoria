package dev.theoria.hud.client;

import dev.theoria.hud.HudConfig;
import dev.theoria.hud.StatusModel;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws the icon rows. Everything goes through {@link #blit}: a sprite clipped to a number of quarters (partial
 * {@code blitSprite}, the overload vanilla's own XP bar uses), optionally tinted. Only vanilla GUI render types — shader
 * packs (Vitrail) treat the HUD like any other (CLAUDE.md render rules).
 *
 * <p>Colour: layer 0 is the untouched vanilla sprite; layers above are the neutral (white-ish) sprite multiplied by the
 * palette colour — you cannot paint a red heart green, tint multiplies (docs/02 §7.3).
 *
 * <p>Every row takes a per-icon {@code ys[]}: the regeneration wave, the low-health jitter and the hunger shake move single icons.
 */
final class StatusBars {
    /** One layered row's sprites: the empty slot, the vanilla fill for layer 0 and the neutral fill for tinted layers. */
    record Sprites(Identifier container, Identifier layer0, Identifier neutral) {}

    private StatusBars() {}

    static int[] flat(int icons, int y) {
        int[] ys = new int[icons];
        Arrays.fill(ys, y);
        return ys;
    }

    /** Container, then the layers. */
    static void layered(GuiGraphicsExtractor g, HudConfig cfg, int[] xs, int[] ys, StatusModel.Fill fill, int[] palette, Sprites sprites,
            int tintOverride, boolean fromRight) {
        container(g, cfg, xs, ys, sprites.container());
        layers(g, cfg, xs, ys, fill, palette, sprites.layer0(), sprites.neutral(), tintOverride, fromRight);
    }

    static void container(GuiGraphicsExtractor g, HudConfig cfg, int[] xs, int[] ys, Identifier container) {
        int size = cfg.layout().iconSize();
        for (int i = 0; i < xs.length; i++) blit(g, container, xs[i], ys[i], size, size, false, -1);
    }

    /**
     * The completed layer below ({@code base}: all quarters), then the filling layer ({@code top}) from the first icon, quarter by
     * quarter. {@code xs[i]} is the x of the i-th icon to fill; {@code tintOverride != 0} replaces the palette above layer 0
     * (poison / wither / freezing, and the white blink "ghost").
     */
    static void layers(GuiGraphicsExtractor g, HudConfig cfg, int[] xs, int[] ys, StatusModel.Fill fill, int[] palette, Identifier layer0,
            Identifier neutral, int tintOverride, boolean fromRight) {
        int per = cfg.layout().quartersPerIcon();
        if (fill.base() >= 0) {
            for (int i = 0; i < xs.length; i++) icon(g, cfg, xs[i], ys[i], fill.base(), per, palette, layer0, neutral, tintOverride, fromRight);
        }
        if (fill.top() > 0) {
            int[] quarters = StatusModel.iconQuarters(fill.top(), cfg);
            for (int i = 0; i < xs.length; i++) {
                if (quarters[i] > 0) icon(g, cfg, xs[i], ys[i], fill.layer(), quarters[i], palette, layer0, neutral, tintOverride, fromRight);
            }
        }
    }

    private static void icon(GuiGraphicsExtractor g, HudConfig cfg, int x, int y, int layer, int quarters, int[] palette, Identifier layer0,
            Identifier neutral, int tintOverride, boolean fromRight) {
        int size = cfg.layout().iconSize();
        int clip = StatusModel.clipWidth(cfg, quarters);
        if (layer == 0) {
            blit(g, layer0, x, y, size, clip, fromRight, -1);
        } else {
            blit(g, neutral, x, y, size, clip, fromRight, tintOverride != 0 ? tintOverride : palette[layer]);
        }
    }

    /**
     * Single-layer bar (hunger, absorption, saturation sheen): {@code container} may be null (sheen), {@code argb} -1 = untinted.
     * {@code xs[i]} is the x of the i-th icon to fill.
     */
    static void single(GuiGraphicsExtractor g, HudConfig cfg, int[] xs, int[] ys, int quarters, Identifier container, Identifier fill, int argb,
            boolean fromRight) {
        int size = cfg.layout().iconSize();
        if (container != null) container(g, cfg, xs, ys, container);
        int[] perIcon = StatusModel.iconQuarters(quarters, cfg);
        for (int i = 0; i < xs.length; i++) {
            blit(g, fill, xs[i], ys[i], size, StatusModel.clipWidth(cfg, perIcon[i]), fromRight, argb);
        }
    }

    /** {@code clip} px of the sprite: from the left edge, or (fromRight) from the right edge — the piece stays where it belongs. */
    static void blit(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int size, int clip, boolean fromRight, int argb) {
        if (clip <= 0) return;
        if (clip >= size) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size, argb);
            return;
        }
        int u = fromRight ? size - clip : 0;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, size, size, u, 0, x + u, y, clip, size, argb);
    }
}
