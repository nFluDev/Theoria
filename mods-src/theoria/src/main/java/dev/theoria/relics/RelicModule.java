package dev.theoria.relics;

/**
 * Phase 12 entry point: the relic table, the items and tab, the worn-relic effects and the reactive hooks, and the self-checks ({@code /theoria test relic [id]}). Called from the mod
 * constructor after the Curios module (relics only ever talk to Curios through {@code dev.theoria.curios.WornCurios}). Slots, tags, loot, icons and lang are generated data.
 */
public final class RelicModule {
    private RelicModule() {}

    public static void init() {
        RelicItems.init();
        RelicEffects.init();
        RelicDamage.init();
        RelicChecks.init();
    }
}
