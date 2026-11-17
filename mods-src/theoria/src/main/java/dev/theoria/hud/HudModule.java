package dev.theoria.hud;

/**
 * Phase 3 entry point (common side): the pure model self-checks ({@code /theoria test hud}). The drawing lives in
 * {@code dev.theoria.hud.client.HudClient}, wired from {@code TheoriaClient} only.
 */
public final class HudModule {
    private HudModule() {}

    public static void init() {
        HudChecks.init();
    }
}
