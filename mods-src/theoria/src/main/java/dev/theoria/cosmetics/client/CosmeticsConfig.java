package dev.theoria.cosmetics.client;

import dev.theoria.cosmetics.CosmeticLook;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The two player-facing look switches (client config {@code theoria-client.toml}, Mods ▸ Theoria ▸ Config). They only decide what THIS player sees; nothing is sent to the server
 * and nobody else's view changes. Both off = every avatar is drawn exactly like vanilla (cosmetic pieces AND hide switches ignored). The per-slot hide dots are NOT here: they belong
 * to the player and everybody sees them (the Curios render toggle's model).
 */
public final class CosmeticsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_COSMETICS = BUILDER
            .comment("Draw my own cosmetic armor pieces on my character.")
            .translation("theoria.configuration.show_cosmetics")
            .define("show_cosmetics", true);
    public static final ModConfigSpec.BooleanValue SHOW_OTHERS = BUILDER
            .comment("Draw other players' cosmetic armor pieces. Off: other players look exactly like vanilla.")
            .translation("theoria.configuration.show_others_cosmetics")
            .define("show_others_cosmetics", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CosmeticsConfig() {}

    public static void init(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /** The viewer's switches for an avatar; before the config has loaded (or if it cannot be read) the defaults, which are everything on. */
    public static CosmeticLook.Viewer viewer(boolean self) {
        if (!SPEC.isLoaded()) return new CosmeticLook.Viewer(self, true, true);
        return new CosmeticLook.Viewer(self, SHOW_COSMETICS.get(), SHOW_OTHERS.get());
    }

    public static void toggle(ModConfigSpec.BooleanValue value) {
        if (SPEC.isLoaded()) value.set(!value.get());
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }
}
