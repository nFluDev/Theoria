package dev.theoria.devtest.client;

import dev.theoria.Theoria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Unattended Haven spawn test ({@code ./gradlew runDevClient -PdevClient=haven -PwithTerraBlender}, Terralith + Lithostitched in {@code run-dev/mods}): replaces the
 * "stand in the Haven at night for three minutes and count" step of the Phase 9b gate. It generates a patch of the real world, finds the nearest Haven of every size, kills
 * everything around it, sets midnight on Hard, speeds the server up and counts the MOBS that natural spawning puts into (a) the structure box, (b) the box + 12 blocks
 * (the {@code beard_thin} margin of the spawn override) and (c) 40 blocks around, every 40 ticks for three game minutes. Then the same at a CONTROL spot 150 blocks from
 * the Haven: without hostile mobs there the whole test proves nothing (spawning might simply not run). Mobs that WALK into the Haven from outside are reported separately
 * (the override stops spawning, not walking). Results: build/dev-out/client_haven.txt.
 */
final class HavenScenario {
    private static final String[] HAVENS = {"haven_s", "haven_m", "haven_l"};
    private static final int SAMPLE_EVERY = 40, SAMPLE_TICKS = 20 * 60 * 3, TICK_RATE = 120;
    private static final long GEN_WAIT_NANOS = 100_000_000_000L;
    private static final int TILE_BLOCKS = 240, TILES = 3;

    private static volatile boolean started, done;
    private static final List<String> LINES = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> SUMMARY = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean failed;

    // server-thread state machine
    private static int phase = -1;                 // -1 setup/generation wait, 0..2 Havens, 3 control, 4 finished
    private static long genStart;
    private static int phaseStartTick, samples;
    private static AABB inner, margin;
    private static BlockPos spot;
    private static net.minecraft.server.level.ServerPlayer playerRef;
    private static final List<BlockPos> havenSpots = new ArrayList<>();
    private static String label;
    private static boolean control;
    private static int maxHostileInner, maxHostileMargin, maxHostile40, walkedIn, spawnedAnywhere;
    private static final TreeSet<String> typesInner = new TreeSet<>(), typesMargin = new TreeSet<>(), types40 = new TreeSet<>();
    // NATURAL spawns seen by FinalizeSpawnEvent, counted by where they happened: this separates "spawned inside the protected area" from "walked in from outside"
    private static int naturalHostileInner, naturalHostileMargin, naturalHostileOuter, naturalAnyMargin, naturalAnyInner;
    private static final EnumMap<MobCategory, Integer> maxByCategory40 = new EnumMap<>(MobCategory.class);

    private HavenScenario() {}

    static void tick(Minecraft mc, int t) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (!started && t == 20) {
            started = true;
            NeoForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onTick(ServerTickEvent.Post event) {
                    HavenScenario.serverTick(event.getServer());
                }

                @SubscribeEvent
                public void onSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
                    HavenScenario.naturalSpawn(event);
                }
            });
            DevClient.REPORT.line("Haven spawn scenario: generating a patch, then %d Havens + 1 control, %d game seconds each at %d tps", HAVENS.length, SAMPLE_TICKS / 20, TICK_RATE);
        }
        if (done && t > 40) {
            LINES.forEach(l -> DevClient.REPORT.line("%s", l));
            SUMMARY.forEach(l -> DevClient.REPORT.line("%s", l));
            DevClient.REPORT.line("RESULT %s", failed ? "FAIL" : "PASS");
            DevClient.REPORT.write();
            mc.stop();
        }
    }

    // ------------------------------------------------------------------------------------------------ server side
    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    static void naturalSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (inner == null || phase < 0 || phase > 3 || event.getSpawnType() != net.minecraft.world.entity.EntitySpawnReason.NATURAL) return;
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(event.getX(), event.getY(), event.getZ());
        boolean hostile = event.getEntity().getType().getCategory() == MobCategory.MONSTER;
        boolean inMargin = margin.contains(at);
        if (inMargin) naturalAnyMargin++;
        if (inner.contains(at)) naturalAnyInner++;
        if (!hostile) return;
        if (inner.contains(at)) naturalHostileInner++;
        if (inMargin) naturalHostileMargin++;
        else naturalHostileOuter++;
    }

    private static void serverTick(MinecraftServer server) {
        if (done || server.getPlayerList().getPlayers().isEmpty()) return;
        var player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = server.overworld();
        int now = server.getTickCount();
        if (phase == -1) {
            if (genStart == 0) {
                genStart = System.nanoTime();
                cmd(server, "difficulty hard");
                for (int i = 0; i < TILES; i++) {
                    for (int j = 0; j < TILES; j++) {
                        int x1 = (i - TILES / 2) * TILE_BLOCKS, z1 = (j - TILES / 2) * TILE_BLOCKS;
                        cmd(server, "forceload add " + x1 + " " + z1 + " " + (x1 + TILE_BLOCKS - 1) + " " + (z1 + TILE_BLOCKS - 1));
                    }
                }
                LINES.add("force-loading " + TILES * TILES * 225 + " chunks, waiting " + GEN_WAIT_NANOS / 1_000_000_000L + " s of wall time for generation");
            } else if (System.nanoTime() - genStart > GEN_WAIT_NANOS) {
                phase = 0;
                beginPhase(server, level, player, now);
            }
            return;
        }
        if (phase > 3) return;
        if (now - phaseStartTick < 20) return;                                           // let the teleport settle
        if ((now - phaseStartTick) % SAMPLE_EVERY == 0) {
            cmd(server, "time set midnight");                                          // 26.3 has no getDayTime(); keep it night by command
            sample(level);
        }
        if (now - phaseStartTick >= SAMPLE_TICKS) {
            finishPhase();
            phase++;
            if (phase > 3) {
                cmd(server, "tick rate 20");
                summarise();
                done = true;
            } else {
                beginPhase(server, level, player, now);
            }
        }
    }

    private static void beginPhase(MinecraftServer server, ServerLevel level, net.minecraft.server.level.ServerPlayer player, int now) {
        control = phase == 3;
        maxHostileInner = maxHostileMargin = maxHostile40 = walkedIn = spawnedAnywhere = 0;
        samples = 0;
        typesInner.clear();
        typesMargin.clear();
        types40.clear();
        maxByCategory40.clear();
        naturalHostileInner = naturalHostileMargin = naturalHostileOuter = naturalAnyMargin = naturalAnyInner = 0;
        if (!control) {
            label = HAVENS[phase];
            var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var holder = structures.get(ResourceKey.create(Registries.STRUCTURE, Theoria.id(label))).orElse(null);
            if (holder == null) {
                LINES.add("FAIL " + label + ": structure not registered");
                failed = true;
                inner = margin = null;
                spot = BlockPos.ZERO;
            } else {
                var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 60, false);
                if (found == null) {
                    LINES.add("FAIL " + label + ": none within 60 chunks of the origin");
                    failed = true;
                    inner = margin = null;
                    spot = BlockPos.ZERO;
                } else {
                    BlockPos at = found.getFirst();
                    ChunkPos cp = new ChunkPos(at.getX() >> 4, at.getZ() >> 4);
                    level.getChunk(cp.x(), cp.z());                                          // make sure the chunk (and its start) is loaded
                    StructureStart start = level.structureManager().startsForStructure(cp.x(), cp.z(), holder.value()).stream().filter(StructureStart::isValid).findFirst().orElse(null);
                    if (start == null) {
                        LINES.add("FAIL " + label + ": located at " + at + " but no valid start in its chunk");
                        failed = true;
                        inner = margin = null;
                        spot = at;
                    } else {
                        BoundingBox box = start.getBoundingBox();
                        inner = AABB.of(box);
                        margin = inner.inflate(12);
                        BlockPos c = box.getCenter();
                        spot = new BlockPos(c.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ()) + 1, c.getZ());
                        havenSpots.add(spot);
                        LINES.add(String.format("%s: start chunk %s, box %dx%dx%d at %s", label, cp, box.getXSpan(), box.getYSpan(), box.getZSpan(), box.getCenter()));
                    }
                }
            }
        } else {
            // A control spot that can really spawn: dry land inside the force-loaded patch, no structure, ≥ 140 blocks from every Haven (the first version used "150 blocks east of
            // the last Haven" and got no mob at all in three game minutes — water or another structure's override, unknown).
            BlockPos pick = null;
            double bestDist = -1;
            for (int x = -300; x <= 300; x += 60) {
                for (int z = -300; z <= 300; z += 60) {
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    BlockPos p = new BlockPos(x, h + 1, z);
                    if (h < 64 || !level.getFluidState(p.below()).isEmpty() || level.getBiome(p).is(net.minecraft.tags.BiomeTags.IS_OCEAN) || level.getBiome(p).is(net.minecraft.tags.BiomeTags.IS_RIVER)) continue;
                    if (!level.structureManager().getAllStructuresAt(p).isEmpty()) continue;
                    double d = havenSpots.stream().mapToDouble(s -> Math.sqrt(s.distSqr(p))).min().orElse(1e9);
                    if (d >= 140 && d > bestDist) {
                        bestDist = d;
                        pick = p;
                    }
                }
            }
            if (pick == null) {
                LINES.add("FAIL control: no dry, structure-free spot ≥ 140 blocks from every Haven in the generated patch");
                failed = true;
                pick = spot;
            }
            spot = pick;
            label = "CONTROL (" + spot.getX() + ", " + spot.getZ() + ", " + level.getBiome(spot).unwrapKey().map(k -> k.identifier().toString()).orElse("?") + ", " + (int) bestDist + " from nearest Haven)";
            inner = margin = new AABB(spot).inflate(12);
        }
        // "@s" needs an entity source: the first three runs used the server source here, the player never moved, and every number was measured at the world spawn
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), "tp @s " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        playerRef = player;
        cmd(server, "kill @e[type=!player]");
        cmd(server, "time set midnight");
        cmd(server, "tick rate " + TICK_RATE);
        phaseStartTick = now;
    }

    private static void sample(ServerLevel level) {
        if (inner == null) return;
        samples++;
        if (samples == 1 || samples == 45 || samples == 90) {
            double dist = Math.sqrt(playerRef.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
            if (dist > 8) {
                LINES.add(String.format("FAIL %s: player is %.0f blocks from the intended spot at sample %d (teleport failed)", label, dist, samples));
                failed = true;
            }
            if (samples == 1) LINES.add(String.format("  player at %.0f, %.0f, %.0f (%.1f blocks from the spot)", playerRef.getX(), playerRef.getY(), playerRef.getZ(), dist));
        }
        AABB wide = new AABB(spot).inflate(128);                                           // natural spawning works 24-128 blocks around the PLAYER: the same fair window for every phase
        var mobs = level.getEntitiesOfClass(Mob.class, wide);
        int in = 0, mg = 0, w40 = 0;
        EnumMap<MobCategory, Integer> byCat = new EnumMap<>(MobCategory.class);
        for (Mob m : mobs) {
            String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
            boolean hostile = m.getType().getCategory() == MobCategory.MONSTER;
            byCat.merge(m.getType().getCategory(), 1, Integer::sum);
            if (hostile && !margin.contains(m.position())) {
                w40++;
                types40.add(type);
            }
            if (hostile && margin.contains(m.position())) {
                mg++;
                typesMargin.add(type);
            }
            if (hostile && inner.contains(m.position())) {
                in++;
                typesInner.add(type);
            }
        }
        byCat.forEach((k, v) -> maxByCategory40.merge(k, v, Math::max));
        maxHostileInner = Math.max(maxHostileInner, in);
        maxHostileMargin = Math.max(maxHostileMargin, mg);
        maxHostile40 = Math.max(maxHostile40, w40);
        spawnedAnywhere = Math.max(spawnedAnywhere, mobs.size());
    }

    private static void finishPhase() {
        String msg = String.format("%-44s samples %d | hostile max present: inside box %d %s · box+12 %d %s · outside %d | NATURAL hostile spawns: inside box %d · box+12 %d · outside %d (any natural spawn in box+12: %d) | all mobs max %d | %s",
                label, samples, maxHostileInner, typesInner, maxHostileMargin, typesMargin, maxHostile40, naturalHostileInner, naturalHostileMargin, naturalHostileOuter, naturalAnyMargin,
                spawnedAnywhere, maxByCategory40);
        LINES.add(msg);
        if (inner == null) return;
        if (control) {
            if (naturalHostileOuter == 0) {
                LINES.add("FAIL control: no natural hostile spawn within three game minutes — the Haven result above proves nothing");
                failed = true;
            }
            return;
        }
        // The spawn override covers the structure's OWN bounding box ("full"), not a margin (a first assumption of "box + 12 blocks" was wrong: 23 natural hostile spawns in the ring).
        if (naturalHostileInner > 0 || naturalAnyInner > 0) {
            LINES.add("FAIL " + label + ": " + naturalHostileInner + " hostile / " + naturalAnyInner + " total NATURAL spawns INSIDE the structure box — the spawn override does not hold");
            failed = true;
        } else if (naturalHostileOuter == 0) {
            LINES.add("INCONCLUSIVE " + label + ": no natural hostile spawn anywhere in range (mob cap full or no dark spots)");
        }
        if (maxHostileInner > 0 && naturalHostileInner == 0) {
            LINES.add("INFO " + label + ": up to " + maxHostileInner + " hostile mob(s) were inside the box but NONE spawned there: they walked in from outside (the override stops spawning, not walking)");
        }
        if (naturalHostileMargin > 0) {
            LINES.add("INFO " + label + ": " + naturalHostileMargin + " natural hostile spawn(s) in the 12-block ring around the box (not protected)");
        }
    }

    private static void summarise() {
        SUMMARY.add("Haven spawn test: " + (failed ? "see FAIL lines" : "no natural spawn ever INSIDE a Haven structure box (S, M, L); the control spot had natural hostile spawns"));
    }
}
