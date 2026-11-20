package dev.theoria.devtest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;

/**
 * Unattended REAL world generation ({@code ./gradlew runDevClient -PdevClient=worldgen -PwithTerraBlender}, plus the pack's Terralith + Lithostitched jars in
 * {@code run-dev/mods}): opens the fixed-seed normal world, force-loads a square of chunks through the real chunk pipeline (TerraBlender, Terralith, our biomes,
 * ore features, structures) and runs {@code /theoria test worlds} in that world. Written after Phase 9b's Chunky gate: the region never generated, then the first
 * generation crashed on a feature-order cycle — neither is visible in the flat GameTest world. The generated chunks are then read offline:
 * {@code python3 -I tools/scan_world.py mods-src/theoria/run-dev/saves/theoria-dev}.
 */
final class WorldgenScenario {
    /** Force-load tiles are 15×15 chunks (225 ≤ the command's 256 limit); 3×3 tiles = 2 025 chunks around the origin. */
    private static final int TILE_BLOCKS = 240;
    /** Tiles per side (gradle -PdevTiles=…): 3 → 2 025 chunks, 5 → 5 625 chunks (1 200 × 1 200 blocks). */
    private static final int TILES = Integer.getInteger("theoria.dev.tiles", 3);
    /** getLoadedChunksCount() counts chunk HOLDERS (empty ones too: 95 % "loaded" after 3 s while the generator was just starting), so completion is time-based: 2 025 chunks take ≈ 45-100 s. */
    private static final int WAIT_SECONDS_MIN = 150;
    private static final int FLUSH_TICKS = 20 * 20;
    private static int requested;
    private static int startTick = -1;
    private static int checksAt = -1;

    private WorldgenScenario() {}

    static void tick(Minecraft mc, int t) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (t == 20) {
            for (int i = 0; i < TILES; i++) {
                for (int j = 0; j < TILES; j++) {
                    int x1 = (i - TILES / 2) * TILE_BLOCKS, z1 = (j - TILES / 2) * TILE_BLOCKS;
                    mc.player.connection.sendCommand("forceload add " + x1 + " " + z1 + " " + (x1 + TILE_BLOCKS - 1) + " " + (z1 + TILE_BLOCKS - 1));
                }
            }
            requested = TILES * TILES * 15 * 15;
            startTick = t;
            DevClient.REPORT.line("force-loading %d chunks (%d×%d blocks) with the pack's real worldgen", requested, TILES * TILE_BLOCKS, TILES * TILE_BLOCKS);
            return;
        }
        if (startTick < 0) return;
        if (checksAt < 0) {
            // getChunkNow() answers null for force-loaded chunks in 26.3 (the first version of this counter stayed at 0 while the world filled up): count the loaded holders instead
            int loaded = level.getChunkSource().getLoadedChunksCount();
            if (t % 200 == 0) DevClient.REPORT.line("t=%ds loaded chunks: %d / %d", t / 20, loaded, requested);
            if (t - startTick >= 20 * Math.max(WAIT_SECONDS_MIN, requested / 25)) {   // ≈ 25-45 chunks/s on the dev machine, plus margin
                DevClient.REPORT.line("waited %.0f s: %d chunk holders for %d requested; flushing to disk", (t - startTick) / 20.0, loaded, requested);
                mc.player.connection.sendCommand("save-all flush");
                mc.player.connection.sendCommand("theoria test worlds");
                checksAt = t;
            }
            return;
        }
        if (t - checksAt >= FLUSH_TICKS) {
            DevClient.chat().stream().filter(line -> line.contains("worlds/") || line.startsWith("✘") || line.contains("✔")).forEach(line -> DevClient.REPORT.line("chat> %s", line));
            boolean failed = DevClient.chat().stream().anyMatch(line -> line.startsWith("✘"));
            DevClient.REPORT.line("RESULT %s (read the world with tools/scan_world.py)", failed ? "FAIL" : "PASS");
            DevClient.REPORT.write();
            mc.stop();
        }
    }
}
