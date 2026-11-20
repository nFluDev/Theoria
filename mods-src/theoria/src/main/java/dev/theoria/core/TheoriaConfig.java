package dev.theoria.core;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Player-facing settings only. Balance numbers never live here: they come from data/balance (CLAUDE.md).
 * Read values after configs have loaded (not from the mod constructor).
 */
public final class TheoriaConfig {
    private static final ModConfigSpec.Builder LOCAL_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue VERBOSE = LOCAL_BUILDER
            .comment("Print passing checks too in /theoria test, and extra diagnostics in the log.")
            .translation("theoria.configuration.verbose")
            .define("verbose", false);

    /** Phase 11 (ADR-007 K5): off = the classic Curios screen opens as Curios made it. */
    public static final ModConfigSpec.BooleanValue CURIOS_MAX = LOCAL_BUILDER
            .comment("Open the Curios Max screen (slot-type tabs, search, cosmetic armor) from the Curios key and button. Off: Curios' own screen.")
            .translation("theoria.configuration.curios_max_screen")
            .define("curios_max_screen", true);

    public static final ModConfigSpec LOCAL = LOCAL_BUILDER.build();

    private TheoriaConfig() {}

    public static void init(ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, LOCAL);
    }

    public static boolean curiosMax() {
        return !LOCAL.isLoaded() || CURIOS_MAX.get();
    }

    public static boolean verbose() {
        return LOCAL.isLoaded() && VERBOSE.get();
    }
}
