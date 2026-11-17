package dev.theoria.hud;

/**
 * The state behind vanilla's heart blink (Hud#extractHealthLevel), pure so it can be tested on a server: when health changes
 * while the player is invulnerable (just hurt / healed) the hearts blink; the LOST part stays visible at the old value
 * ({@link #display()}) for {@code display_hold_ticks}. Mirror of {@code HealthTracker} in tools/hud_model.py.
 * Ticks come from the player entity, so a respawn (counter restarts) resets the tracker.
 */
public final class HealthTracker {
    private final HudConfig.Effects fx;
    private int last;
    private int display = -1;
    private long lastChange;
    private long blinkUntil;

    public HealthTracker(HudConfig.Effects fx) {
        this.fx = fx;
    }

    public void update(int current, boolean invulnerable, long tick) {
        if (tick < lastChange) { // the entity's tick counter restarted: new life
            display = -1;
            lastChange = tick;
            blinkUntil = 0;
        }
        if (current < last && invulnerable) {
            lastChange = tick;
            blinkUntil = tick + fx.blinkDamageTicks();
        } else if (current > last && invulnerable) {
            lastChange = tick;
            blinkUntil = tick + fx.blinkHealTicks();
        }
        if (display < 0 || tick - lastChange > fx.displayHoldTicks()) {
            display = current;
            lastChange = tick;
        }
        last = current;
    }

    /** The health the blink "ghost" shows: the value before the recent change, held a moment. */
    public int display() {
        return display;
    }

    public boolean blinking(long tick) {
        return HudEffects.blinkActive(blinkUntil, tick, fx.blinkPhaseTicks());
    }
}
