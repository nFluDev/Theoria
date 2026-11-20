package dev.theoria.worlds;

/**
 * Phase 9b entry point. Biomes, the TerraBlender region, ore placement, structures, chest loot and spawn overrides are all data
 * (generator/biomes.py, generator/structures.py → src/generated/{biomes,structures}); Java holds only the in-game self-checks
 * ({@code /theoria test worlds}) and the {@link TerraBlenderBridge} ordering fix, per CLAUDE.md "content is data-driven". Nothing here compiles against TerraBlender (docs/02 §7).
 * Called from the mod constructor.
 */
public final class WorldsModule {
    private WorldsModule() {}

    public static void init() {
        WorldChecks.init();
        TerraBlenderBridge.init();   // the one Java mechanic here: see its class comment (TerraBlender 26.3 loads data-driven regions too late)
    }
}
