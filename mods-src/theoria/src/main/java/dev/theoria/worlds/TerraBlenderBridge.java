package dev.theoria.worlds;

import dev.theoria.Theoria;
import java.lang.reflect.Method;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Makes Theoria's data-driven TerraBlender region ({@code data/theoria/terrablender/region/overworld.json}) actually take part in world generation.
 *
 * <p><b>Why this exists.</b> TerraBlender 26.3.0.0.9's {@code ServerAboutToStart} handler first builds every biome search tree
 * ({@code LevelUtils.initializeOnServerStart}) and only THEN loads the data-driven regions ({@code Regions.loadDataDrivenRegions}). A region from a datapack is
 * therefore registered ("Registered region theoria:overworld to index 1" in the log) but never enters the trees, and the world has zero Theoria biomes (found in
 * Phase 9b's Chunky gate: 6 039 generated chunks, 0 Theoria biomes). Loading the regions one step earlier fixes the order; TerraBlender's own later call is
 * harmless because {@code Regions.register} replaces an existing name in place and keeps its index.
 *
 * <p>No compile-time link to TerraBlender (CLAUDE.md, docs/02 §7): one reflective call to a public static method, pinned to the pack's version and guarded
 * by {@code /theoria test worlds}, which fails if Theoria biomes do not come out of the real overworld biome source.
 */
public final class TerraBlenderBridge {
    private static volatile String status = "TerraBlender not loaded";
    private static volatile boolean ok;

    private TerraBlenderBridge() {}

    static void init() {
        // HIGHEST: before TerraBlender's own (NORMAL) handler builds the biome trees.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TerraBlenderBridge::onServerAboutToStart);
    }

    /** Result of the last early load, for {@code /theoria test worlds}. */
    public static boolean ok() {
        return ok || !ModList.get().isLoaded("terrablender");
    }

    public static String status() {
        return status;
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!ModList.get().isLoaded("terrablender")) return;
        MinecraftServer server = event.getServer();
        try {
            Class<?> regions = Class.forName("terrablender.api.Regions");
            Method load = regions.getMethod("loadDataDrivenRegions", RegistryAccess.class);
            load.invoke(null, server.registryAccess());
            ok = true;
            status = "data-driven TerraBlender regions loaded before TerraBlender built its biome trees";
            Theoria.LOGGER.info("Theoria: {}", status);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ok = false;
            status = "early TerraBlender region load FAILED: " + e;
            Theoria.LOGGER.error("Theoria: {} (Theoria biomes will not generate)", status, e);
        }
    }
}
