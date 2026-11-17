package dev.theoria.hud;

/**
 * Vanilla's HUD effects, kept (D18): the damage blink, the regeneration wave, the low-health jitter and the hunger shake.
 * Only the DECISIONS live here (pure functions of tick and state; mirror of tools/hud_model.py {@code blink_active},
 * {@code regen_lifted}, {@code is_low_health}, {@code hunger_shakes}); the pixel offsets and sprites are applied by
 * {@code StatusLayer}. Values: {@code data/hud/hud.json} "effects" (vanilla's, regeneration adapted to 5 icons).
 */
public final class HudEffects {
    private HudEffects() {}

    /** Blink while {@code blinkUntil} is ahead, in alternating {@code phase}-tick slices (vanilla: odd slices are lit). */
    public static boolean blinkActive(long blinkUntil, long tick, int phase) {
        return blinkUntil > tick && ((blinkUntil - tick) / phase) % 2 == 1;
    }

    /** The icon lifted by the regeneration wave (0 = first), or -1 while the wave pauses. */
    public static int regenLifted(HudConfig cfg, long tick) {
        HudConfig.Effects fx = cfg.effects();
        long index = (tick / fx.regenStepTicks()) % (cfg.layout().icons() + fx.regenPause());
        return index < cfg.layout().icons() ? (int) index : -1;
    }

    /** Health + absorption at or below the fraction of the maximum (vanilla: 4 of 20 HP — 20 %, so it stays meaningful at 214 HP). */
    public static boolean isLowHealth(HudConfig cfg, double currentPlusAbsorption, double maximum) {
        return currentPlusAbsorption <= maximum * cfg.effects().lowHealthFraction();
    }

    /** Vanilla: with no saturation the hunger icons shake for one frame every food*3+1 ticks. */
    public static boolean hungerShakes(long tick, int food, double saturation) {
        return saturation <= 0 && tick % (food * 3L + 1) == 0;
    }
}
