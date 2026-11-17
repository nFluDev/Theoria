package dev.theoria.core.check;

import dev.theoria.Theoria;
import dev.theoria.core.Net;
import dev.theoria.core.TheoriaConfig;
import dev.theoria.core.TheoriaRegistries;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;

/** Area "core": the skeleton itself is wired. */
public final class CoreChecks {
    private CoreChecks() {}

    public static void init() {
        Checks.register("core", "versions", source -> {
            Checks.require(!"?".equals(Theoria.version()), "mod version unresolved");
            return "theoria " + Theoria.version() + ", minecraft " + minecraftVersion() + ", neoforge " + neoForgeVersion();
        });
        Checks.register("core", "config", source -> {
            Checks.require(TheoriaConfig.LOCAL.isLoaded(), "local config not loaded");
            return "verbose=" + TheoriaConfig.VERBOSE.get();
        });
        Checks.register("core", "registries", source -> TheoriaRegistries.summary() + ", payloads=" + Net.payloadCount());
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static String neoForgeVersion() {
        return ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }
}
