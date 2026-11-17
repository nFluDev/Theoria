package dev.theoria.mobs;

import net.neoforged.bus.api.IEventBus;

/**
 * Phase 8 entry point: the species (12 archetypes, ~35 species, 3 bosses), spawn rules, zone tiers, XP scale, weapon normalization, Heart Crystals.
 * Everything is driven by {@code theoria/mobs.json} (generator/mobs.py ← data/mobs.csv) and {@code theoria/mob_balance.json} (tools/balance/mobs.py);
 * Java holds only mechanics. Called from the mod constructor, before any registry event. Client side: {@code dev.theoria.mobs.client}.
 */
public final class MobModule {
    private MobModule() {}

    public static void init(IEventBus modBus) {
        MobRegistry.init(modBus);
        HeartCrystals.init();
        MobHooks.init();
        MobChecks.init();
    }
}
