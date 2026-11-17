package dev.theoria.hud;

/**
 * HUD geometry in GUI pixels (docs/04 §1). Rows sit at {@code guiHeight - bottom} (health / hunger, row B) and one row
 * above (armor / vehicle, row A); the number room is the gap between the left and right icon columns. Pure — mirror of
 * {@code geometry()} in tools/hud_model.py.
 *
 * <pre>
 * [armor x5]   armor number     [vehicle x5]    row A  y = guiHeight - bottom - rowHeight
 * [heart x5]   health number    [hunger  x5]    row B  y = guiHeight - bottom
 * ──────────── xp bar (level inside) ─────────
 * </pre>
 */
public record HudLayout(int centre, int textLeft, int textRight, int yA, int yB, int xpTop) {
    public static HudLayout of(HudConfig cfg, int guiWidth, int guiHeight) {
        HudConfig.Layout lo = cfg.layout();
        int centre = guiWidth / 2;
        int yB = guiHeight - lo.bottom();
        return new HudLayout(centre, centre - lo.barEdge() + lo.barWidth() + lo.textPad(), centre + lo.barEdge() - lo.barWidth() - lo.textPad(),
                yB - lo.rowHeight(), yB, guiHeight - 24 - lo.xpBarHeight());
    }

    public int textRoom() {
        return textRight - textLeft;
    }

    /** x of icon {@code i} in a left-aligned row (health, armor): 0 = leftmost. */
    public int leftIconX(HudConfig cfg, int i) {
        return centre - cfg.layout().barEdge() + i * cfg.layout().iconStep();
    }

    /** x of icon {@code i} in a right-aligned row (hunger, vehicle): 0 = rightmost, like vanilla. */
    public int rightIconX(HudConfig cfg, int i) {
        return centre + cfg.layout().barEdge() - i * cfg.layout().iconStep() - cfg.layout().iconSize();
    }

    public String notation() {
        return textLeft + ";" + textRight + ";" + yA + ";" + yB + ";" + xpTop;
    }
}
