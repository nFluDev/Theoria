package dev.theoria.devtest;

import dev.theoria.Theoria;
import dev.theoria.core.check.Checks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.RegistryAccess;
import net.neoforged.fml.ModList;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 9b GameTests. Expectations come from the single-source CSVs ({@code data/biomes.csv}, {@code data/structures.csv}, {@code data/ores.csv},
 * {@code data/worldgen.json}), never from the generated JSON: the point is that the CSV, the generated files and the loaded game agree.
 * The Haven test runs the real generator pipeline ({@code Structure#generate} → {@code placeInChunk}) and asks the engine's own spawn gate
 * ({@code ChunkGenerator#getMobsAt}) — the same call natural spawning uses — for every mob category inside the placed structure.
 */
final class WorldTests {
    private WorldTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("worlds_checks", WorldTests::checks);
        add.accept("worlds_biomes", WorldTests::biomes);
        add.accept("worlds_ores", WorldTests::ores);
        add.accept("worlds_structures", WorldTests::structures);
        add.accept("worlds_haven", WorldTests::haven);
        add.accept("worlds_loot", WorldTests::loot);
        add.accept("worlds_places", WorldTests::places);
        add.accept("worlds_terrablender_generation", WorldTests::terrablenderGeneration);
        add.accept("worlds_region_share", WorldTests::regionShare);
    }

    private static List<Map<String, String>> csv(String name) {
        return GearTests.csv(name);
    }

    private static Identifier id(String path) {
        return Theoria.id(path);
    }

    private static Set<String> names(Iterable<Holder<Biome>> biomes) {
        Set<String> out = new HashSet<>();
        for (Holder<Biome> b : biomes) out.add(b.getRegisteredName());
        return out;
    }

    // ------------------------------------------------------------------------------------------------ /theoria test worlds
    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_checks");
        var results = Checks.run("worlds", h.getLevel().getServer().createCommandSourceStack());
        for (var res : results) r.line("%s %s — %s", res.ok() ? "PASS" : "FAIL", res.name(), res.detail());
        r.write();
        h.assertTrue(results.size() >= 5, "worlds checks registered: " + results.size());
        for (var res : results) h.assertTrue(res.ok(), res.name() + ": " + res.detail());
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ biomes.csv ↔ registry
    private static void biomes(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_biomes");
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        List<Map<String, String>> rows = csv("biomes.csv");
        h.assertTrue(rows.size() >= 20, "biomes.csv rows " + rows.size());
        Set<String> theoria = names(registry.get(TagKey.create(Registries.BIOME, id("is_theoria"))).orElseThrow());
        Set<String> land = names(registry.get(TagKey.create(Registries.BIOME, id("land"))).orElseThrow());
        int joins = 0;
        for (Map<String, String> row : rows) {
            String bid = row.get("id");
            var holder = registry.get(ResourceKey.create(Registries.BIOME, id(bid)));
            h.assertTrue(holder.isPresent(), "biome theoria:" + bid + " not registered");
            Biome biome = holder.get().value();
            String name = "theoria:" + bid;
            h.assertTrue(theoria.contains(name), name + " not in #theoria:is_theoria");
            h.assertTrue(land.contains(name) == row.get("kind").equals("surface"), name + ": land tag vs kind " + row.get("kind"));
            h.assertTrue(holder.get().is(net.minecraft.tags.BiomeTags.IS_OVERWORLD), name + " not in #minecraft:is_overworld");
            h.assertTrue(Math.abs(biome.getBaseTemperature() - Float.parseFloat(row.get("temperature"))) < 1.0e-4, name + ": temperature");
            h.assertTrue(biome.hasPrecipitation() == !row.get("precip").equals("none"), name + ": precipitation");
            h.assertTrue(biome.getGenerationSettings().features().size() == 11, name + ": feature steps");
            String band = "band/t" + row.get("tier_lo") + "_" + row.get("tier_hi");
            h.assertTrue(holder.get().is(TagKey.create(Registries.BIOME, id(band))), name + " not in #theoria:" + band);
            for (String join : row.get("joins").split("\\|")) {
                if (join.isEmpty()) continue;
                h.assertTrue(holder.get().is(TagKey.create(Registries.BIOME, Identifier.parse(join))), name + " not in #" + join);
                joins++;
            }
            r.line("%-18s T%s-%s %-7s temp %.2f joins %s", bid, row.get("tier_lo"), row.get("tier_hi"), row.get("kind"), biome.getBaseTemperature(), row.get("joins"));
        }
        h.assertTrue(theoria.size() == rows.size(), "#theoria:is_theoria has " + theoria.size() + " members, biomes.csv " + rows.size());
        r.line("%d biomes registered, tagged and joined (%d external tag memberships)", rows.size(), joins);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ ores.csv `biomes` ↔ has_ore ↔ feature
    private static void ores(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_ores");
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var tags = new HashMap<String, Set<String>>();
        for (Map<String, String> ore : csv("ores.csv")) {
            String oid = ore.get("id");
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, id("has_ore/" + oid));
            Set<String> members = names(biomes.get(tag).orElseThrow(() -> new IllegalStateException("tag missing: " + tag)));
            tags.put(oid, members);
            int listed = 0;
            for (String spec : ore.get("biomes").split("\\|")) {
                if (spec.startsWith("#")) {
                    var set = biomes.get(TagKey.create(Registries.BIOME, Identifier.parse(spec.substring(1))));
                    h.assertTrue(set.isPresent(), "ores.csv " + oid + ": tag " + spec + " does not exist");
                    for (String n : names(set.get())) h.assertTrue(members.contains(n), n + " (" + spec + ") missing from has_ore/" + oid);
                } else {
                    h.assertTrue(members.contains("theoria:" + spec), "theoria:" + spec + " missing from has_ore/" + oid);
                    listed++;
                }
            }
            r.line("%-11s %3d biomes (%d Theoria-listed), y %s…%s", oid, members.size(), listed, ore.get("min_y"), ore.get("max_y"));
        }
        // tier ladder sanity: the deep-metal ores never reach vanilla biomes (orichalcum/titanium also use the vanilla mountain tag: y >= 140 rock exists only on peaks)
        for (String deep : List.of("platinum", "mithril", "lonsdaleit")) {
            h.assertTrue(tags.get(deep).stream().allMatch(n -> n.startsWith("theoria:")), deep + " must only be placed in Theoria biomes");
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ structures.csv ↔ registries
    private static void structures(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_structures");
        ServerLevel level = h.getLevel();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var manager = level.getServer().getStructureTemplateManager();
        var lootTables = level.getServer().reloadableRegistries();
        Set<String> chestsSeen = new HashSet<>();
        for (Map<String, String> row : csv("structures.csv")) {
            String sid = row.get("id");
            Structure structure = structures.getValue(id(sid));
            h.assertTrue(structure != null, "structure theoria:" + sid + " not registered");
            h.assertTrue(structure.step() == GenerationStep.Decoration.valueOf(row.get("step").toUpperCase()), sid + ": step");
            h.assertTrue(structure.terrainAdaptation().name().equalsIgnoreCase(row.get("adapt")), sid + ": terrain adaptation");
            var biomeSet = biomes.get(TagKey.create(Registries.BIOME, id("has_structure/" + sid)));
            h.assertTrue(biomeSet.isPresent() && biomeSet.get().size() > 0, sid + ": has_structure tag empty");
            StructureSet set = sets.getValue(id(row.get("set")));
            h.assertTrue(set != null, "structure_set theoria:" + row.get("set") + " missing");
            boolean inSet = set.structures().stream().anyMatch(e -> e.structure().is(id(sid)));
            h.assertTrue(inSet, sid + " not in structure_set " + row.get("set"));
            h.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement, "placement type");
            RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) set.placement();
            h.assertTrue(spread.spacing() > spread.separation(), sid + ": spacing/separation");
            StructureTemplatePool pool = pools.getValue(id(sid));
            h.assertTrue(pool != null && pool.getTemplates().size() == row.get("variants").split("\\|").length, sid + ": pool variants");
            for (var pair : pool.getTemplates()) {
                StructurePoolElement element = pair.getFirst();
                var size = element.getSize(manager, Rotation.NONE);
                if (row.get("kind").equals("haven")) {
                    h.assertTrue(size.getX() == Integer.parseInt(row.get("w")) && size.getZ() == Integer.parseInt(row.get("d")), sid + ": template footprint " + size);
                }
                h.assertTrue(size.getY() > 0, sid + ": empty template");
            }
            // every chest/barrel in every template points at an existing loot table
            for (String variant : row.get("variants").split("\\|")) {
                String key = row.get("kind").equals("haven") ? "haven_" + sid.substring(sid.lastIndexOf('_') + 1) + "/" + variant : sid + "/" + variant;
                StructureTemplate template = manager.get(id(key)).orElseThrow(() -> new IllegalStateException("template " + key));
                if (!chestsSeen.add(key)) continue;
                int containers = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).size() + template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.BARREL).size();
                // 26.3 palette entries are {id, properties}: with the 1.21 keys {Name, Properties} every block silently loads as AIR (size still loads) — so assert real blocks
                h.assertTrue(containers > 0, key + ": the loaded template has no chest/barrel (blocks did not load as real blocks: " + template.getSize() + ")");
                for (var block : List.of(Blocks.CHEST, Blocks.BARREL)) {
                    for (var info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block)) {
                        if (info.nbt() == null || info.nbt().getString("LootTable").isEmpty()) continue;
                        Identifier table = Identifier.parse(info.nbt().getString("LootTable").orElseThrow());
                        h.assertTrue(lootTables.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table)) != LootTable.EMPTY, key + ": loot table " + table + " missing");
                    }
                }
            }
            r.line("%-16s %-5s set %-13s spacing %d/%d, %d variants, %d biomes eligible, step %s", sid, row.get("kind"), row.get("set"), spread.spacing(), spread.separation(),
                    pool.getTemplates().size(), biomeSet.get().size(), structure.step().getName());
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ TerraBlender: the region really generates
    /**
     * Registry membership proved nothing in 9b's first version (the region was registered and never generated). The GameTest level is flat, so this builds the REAL
     * overworld generator from the normal world preset, runs the initialization TerraBlender does for every real LevelStem at server start (reflection, test-only) with
     * the regions loaded at that point (TerraBlenderBridge makes sure ours is among them), and samples its biome source: Theoria biomes must come out. The ordering fix
     * itself (bridge before TerraBlender's trees) can only be seen on a real server start: /theoria test worlds in a normal world + the world scan in docs/journal/f09-kapi.md.
     */
    private static void terrablenderGeneration(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_terrablender_generation");
        if (!ModList.get().isLoaded("terrablender")) {
            r.line("TerraBlender not loaded (opt-in: -PwithTerraBlender): skipped");
            r.write();
            h.succeed();
            return;
        }
        RegistryAccess access = h.getLevel().registryAccess();
        LevelStem stem = access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow();
        h.assertTrue(stem.generator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator, "normal preset overworld is " + stem.generator().getClass().getSimpleName());
        try {
            Class.forName("terrablender.util.LevelUtils")
                    .getMethod("initializeBiomes", RegistryAccess.class, Holder.class, ResourceKey.class, net.minecraft.world.level.chunk.ChunkGenerator.class, long.class)
                    .invoke(null, access, stem.type(), LevelStem.OVERWORLD, stem.generator(), 4242L);
        } catch (ReflectiveOperationException e) {
            h.fail("TerraBlender initializeBiomes reflection failed (API changed?): " + e);
            return;
        }
        var p = dev.theoria.worlds.WorldChecks.probe(access, stem.generator());
        r.line("normal-preset overworld after TerraBlender init: %s", p.summary());
        r.line("seen: %s", p.seen());
        r.write();
        h.assertTrue(p.hits() * 100 >= p.total() * 5, "Theoria biomes " + p.summary() + " — the region does not generate");
        h.assertTrue(p.seen().size() >= 6, "only " + p.seen().size() + " distinct Theoria biomes: " + p.seen());
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ region share (measurement aid, no generation)
    /**
     * TerraBlender picks the region at a position from a noise that does not depend on climate, and our region covers every climate (strict surface partition + caves + deep),
     * so the fraction of {@code theoria:} biomes over a LARGE grid is the region's area share. Written after the Phase 9b sweep: 1 200-block worlds hold 1-3 region cells
     * (Theoria surface share 19-75 %), far too noisy to tune the region weight. This samples eight 15 × 15 km areas tens of kilometres apart without generating a chunk
     * (opt-in: -PwithTerraBlender -PgameTestOnly=worlds_region_share; writes build/dev-out/world/region_share.txt). One noise realization only: TerraBlender 26.3 does not re-seed on a
     * second initialization (a first version looped over seeds and got the same 36.4 % twelve times), so the areas, not seeds, give the spread. Vanilla noise (no Terralith) is fine:
     * the region choice is climate-independent.
     */
    private static void regionShare(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_region_share");
        if (!ModList.get().isLoaded("terrablender")) {
            r.line("TerraBlender not loaded (opt-in: -PwithTerraBlender): skipped");
            r.write();
            h.succeed();
            return;
        }
        RegistryAccess access = h.getLevel().registryAccess();
        LevelStem stem = access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow();
        var settings = ((net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) stem.generator()).generatorSettings();
        try {
            Class.forName("terrablender.util.LevelUtils")
                    .getMethod("initializeBiomes", RegistryAccess.class, Holder.class, ResourceKey.class, net.minecraft.world.level.chunk.ChunkGenerator.class, long.class)
                    .invoke(null, access, stem.type(), LevelStem.OVERWORLD, stem.generator(), 4242L);
        } catch (ReflectiveOperationException e) {
            h.fail("TerraBlender initializeBiomes reflection failed: " + e);
            return;
        }
        var state = net.minecraft.world.level.levelgen.RandomState.create(access.lookupOrThrow(Registries.NOISE), 4242L, settings.value());
        var sampler = state.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build());
        var resolver = stem.generator().getBiomeSource().createResolver(sampler);
        final int half = 60, step = 128;                                        // 121 × 121 samples per area = 15.4 km square
        int[][] centres = {{0, 0}, {40000, 0}, {0, 40000}, {-40000, 0}, {0, -40000}, {40000, 40000}, {-40000, -40000}, {80000, -40000}};
        double sum = 0, min = 1, max = 0;
        for (int[] c : centres) {
            int hits = 0, total = 0;
            for (int i = -half; i <= half; i++) {
                for (int j = -half; j <= half; j++) {
                    String name = resolver.getNoiseBiome((c[0] + i * step) >> 2, 64 >> 2, (c[1] + j * step) >> 2).getRegisteredName();
                    total++;
                    if (name.startsWith("theoria:")) hits++;
                }
            }
            double share = hits / (double) total;
            sum += share;
            min = Math.min(min, share);
            max = Math.max(max, share);
            r.line("area centre (%6d, %6d)  theoria share %.1f %% (%d / %d samples)", c[0], c[1], share * 100, hits, total);
        }
        r.line("region share over %d areas of 15 km: mean %.1f %%, min %.1f %%, max %.1f %%", centres.length, sum / centres.length * 100, min * 100, max * 100);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ Haven: real generation + spawn gate
    private static void haven(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_haven");
        ServerLevel level = h.getLevel();
        var generator = level.getChunkSource().getGenerator();
        // The test level is flat (its heightmap would bury the structure); generation uses the REAL overworld noise generator, placement goes into this level.
        var access = level.registryAccess();
        var noiseSettings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var plains = access.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var noiseGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(plains), noiseSettings);
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(access.lookupOrThrow(Registries.NOISE), 4242L, noiseSettings.value());
        var sampler = randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Map<String, String>> havens = new ArrayList<>();
        for (Map<String, String> row : csv("structures.csv")) if (row.get("kind").equals("haven")) havens.add(row);
        h.assertTrue(havens.size() >= 3, "expected Haven rows: " + havens.size());
        int slot = 0;
        for (Map<String, String> row : havens) {
            String sid = row.get("id");
            Holder<Structure> holder = structures.get(ResourceKey.create(Registries.STRUCTURE, id(sid))).orElseThrow();
            ChunkPos origin = new ChunkPos(2000 + slot * 24, 2000 + (slot % 2) * 24);
            slot++;
            StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), noiseGenerator, noiseGenerator.getBiomeSource(),
                    sampler, randomState, level.getServer().getStructureTemplateManager(), 4242L, origin, 0, level, b -> true);
            h.assertTrue(start.isValid(), sid + ": Structure#generate returned no start");
            BoundingBox box = start.getBoundingBox();
            int w = Integer.parseInt(row.get("w")), d = Integer.parseInt(row.get("d"));
            // beard_* terrain adaptation inflates the start's box by 12 on every side (Structure#adjustBoundingBox): "full" spawn overrides therefore also cover a 12-block margin
            int margin = holder.value().terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE ? 0 : 12;
            h.assertTrue(box.getXSpan() == w + 2 * margin && box.getZSpan() == d + 2 * margin, sid + ": bounding box " + box.getXSpan() + "×" + box.getZSpan() + " ≠ " + w + "×" + d + " + margin " + margin);
            // register the start + references in every covered chunk, then place it chunk by chunk (what chunk generation does)
            ChunkPos minChunk = new ChunkPos(box.minX() >> 4, box.minZ() >> 4), maxChunk = new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);
            ChunkAccess home = level.getChunk(origin.x(), origin.z());
            home.setStartForStructure(holder.value(), start);
            for (int cx = minChunk.x(); cx <= maxChunk.x(); cx++) {
                for (int cz = minChunk.z(); cz <= maxChunk.z(); cz++) {
                    level.getChunk(cx, cz).addReferenceForStructure(holder.value(), origin.pack());
                }
            }
            for (int cx = minChunk.x(); cx <= maxChunk.x(); cx++) {
                for (int cz = minChunk.z(); cz <= maxChunk.z(); cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    start.placeInChunk(level, level.structureManager(), noiseGenerator, level.getRandom(), new BoundingBox(cp.getMinBlockX(), level.getMinY(), cp.getMinBlockZ(),
                            cp.getMaxBlockX(), level.getMaxY(), cp.getMaxBlockZ()), cp);
                }
            }
            int beds = 0, furnaces = 0, chests = 0, withLoot = 0, spawners = 0, solid = 0;
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                var state = level.getBlockState(p);
                if (!state.isAir()) solid++;
                if (state.getBlock() instanceof BedBlock) beds++;
                else if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)) furnaces++;
                else if (state.is(Blocks.SPAWNER)) spawners++;
                else if (state.is(Blocks.CHEST)) {
                    chests++;
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) withLoot++;
                }
            }
            AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            int entities = level.getEntities(null, aabb).size();
            h.assertTrue(beds >= 2 && furnaces >= 1 && chests >= 1, sid + ": beds/furnaces/chests = " + beds + "/" + furnaces + "/" + chests + " (non-air " + solid + ", pieces " + start.getPieces().size()
                    + ", piece box " + start.getPieces().get(0).getBoundingBox() + ", start box " + box + ", chunks " + minChunk + ".." + maxChunk + ")");
            h.assertTrue(withLoot == chests, sid + ": " + (chests - withLoot) + " chests lost their loot table on placement");
            h.assertTrue(spawners == 0 && entities == 0, sid + ": spawners " + spawners + ", entities " + entities);
            // the engine's own spawn gate, for every category, on a grid inside the structure (and its vertical extent)
            int probes = 0;
            BlockPos centre = box.getCenter();
            for (int x = box.minX(); x <= box.maxX(); x += Math.max(2, w / 8)) {
                for (int z = box.minZ(); z <= box.maxZ(); z += Math.max(2, d / 8)) {
                    for (int y : new int[] {box.minY() + 4, centre.getY(), box.maxY() - 1}) {
                        for (MobCategory category : MobCategory.values()) {
                            var spawns = generator.getMobsAt(level, level.structureManager(), category, new BlockPos(x, y, z));
                            h.assertTrue(spawns.isEmpty(), sid + ": " + category.getName() + " spawns allowed at " + x + "," + y + "," + z);
                            probes++;
                        }
                    }
                }
            }
            // control: well outside the structure the biome's own list is back (it is non-empty in the test world's biome, else the probe is vacuous)
            BlockPos outside = new BlockPos(box.maxX() + 40, centre.getY(), box.maxZ() + 40);
            boolean controlHasSpawns = false;
            for (MobCategory category : MobCategory.values()) controlHasSpawns |= !generator.getMobsAt(level, level.structureManager(), category, outside).isEmpty();
            r.line("%-15s y %d..%d  box %dx%dx%d  beds %d furnaces %d chests %d (all with loot)  spawners 0, entities 0  spawn-gate probes %d all empty; control outside non-empty: %s",
                    sid, box.minY(), box.maxY(), box.getXSpan(), box.getYSpan(), box.getZSpan(), beds, furnaces, chests, probes, controlHasSpawns);
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ Ruin / Dungeon / Keep: real generation + placement
    private static void places(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_places");
        ServerLevel level = h.getLevel();
        var access = level.registryAccess();
        var noiseSettings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var plains = access.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var noiseGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(plains), noiseSettings);
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(access.lookupOrThrow(Registries.NOISE), 4243L, noiseSettings.value());
        var sampler = randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
        var structures = access.lookupOrThrow(Registries.STRUCTURE);
        var species = new HashSet<String>();
        for (Map<String, String> sp : csv("mobs.csv")) species.add(sp.get("id") + ":" + sp.get("cls"));
        int slot = 0;
        for (Map<String, String> row : csv("structures.csv")) {
            if (row.get("kind").equals("haven")) continue;
            String sid = row.get("id");
            Holder<Structure> holder = structures.get(ResourceKey.create(Registries.STRUCTURE, id(sid))).orElseThrow();
            ChunkPos origin = new ChunkPos(2400 + slot * 30, 2600);
            slot++;
            StructureStart start = holder.value().generate(holder, level.dimension(), access, noiseGenerator, noiseGenerator.getBiomeSource(), sampler, randomState,
                    level.getServer().getStructureTemplateManager(), 4243L, origin, 0, level, b -> true);
            h.assertTrue(start.isValid(), sid + ": Structure#generate returned no start");
            BoundingBox box = start.getBoundingBox();
            ChunkPos minChunk = new ChunkPos(box.minX() >> 4, box.minZ() >> 4), maxChunk = new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4);
            level.getChunk(origin.x(), origin.z()).setStartForStructure(holder.value(), start);
            for (int cx = minChunk.x(); cx <= maxChunk.x(); cx++) for (int cz = minChunk.z(); cz <= maxChunk.z(); cz++) level.getChunk(cx, cz).addReferenceForStructure(holder.value(), origin.pack());
            for (int cx = minChunk.x(); cx <= maxChunk.x(); cx++) {
                for (int cz = minChunk.z(); cz <= maxChunk.z(); cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    start.placeInChunk(level, level.structureManager(), noiseGenerator, level.getRandom(), new BoundingBox(cp.getMinBlockX(), level.getMinY(), cp.getMinBlockZ(),
                            cp.getMaxBlockX(), level.getMaxY(), cp.getMaxBlockZ()), cp);
                }
            }
            int chests = 0, withLoot = 0, spawners = 0;
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                var state = level.getBlockState(p);
                if (state.is(Blocks.SPAWNER)) spawners++;
                else if (state.is(Blocks.CHEST)) {
                    chests++;
                    if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) withLoot++;
                }
            }
            AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            List<String> kinds = new ArrayList<>();
            for (var e : level.getEntities(null, aabb)) kinds.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
            h.assertTrue(chests >= 1 && withLoot == chests, sid + ": chests " + chests + " with loot " + withLoot);
            switch (row.get("kind")) {
                case "dungeon" -> {
                    h.assertTrue(spawners >= 4, sid + ": spawners " + spawners);
                    h.assertTrue(kinds.contains("stone_guardian"), sid + ": the elite is missing, entities " + kinds);
                }
                case "keep" -> {
                    long bosses = kinds.stream().filter(k -> species.contains(k + ":boss")).count();
                    h.assertTrue(bosses == 1, sid + ": expected exactly 1 boss entity, found " + bosses + " in " + kinds);
                }
                default -> h.assertTrue(spawners == 0, sid + ": a Ruin carries no spawner");
            }
            // spawn override of the non-Haven classes: the natural monster list inside the structure is the listed species (not empty, not the biome's)
            BlockPos inside = box.getCenter();
            var monsters = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.MONSTER, inside);
            boolean listed = !row.get("mobs").equals("none");
            boolean own = monsters.unwrap().stream().anyMatch(w -> BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type()).getNamespace().equals(Theoria.MODID));
            h.assertTrue(!listed || own || row.get("kind").equals("keep"), sid + ": spawn override (monster) does not list our species at " + inside);
            for (var e : level.getEntities(null, aabb)) e.discard();           // bosses/elite must not linger and tick for the rest of the run
            r.line("%-13s y %d..%d  box %dx%dx%d  chests %d (loot %d)  spawners %d  entities %s  override lists Theoria species: %s", sid, box.minY(), box.maxY(), box.getXSpan(), box.getYSpan(),
                    box.getZSpan(), chests, withLoot, spawners, kinds, own);
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ loot
    private static void loot(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_loot");
        ServerLevel level = h.getLevel();
        var tables = level.getServer().reloadableRegistries();
        RandomSource random = RandomSource.create(9);
        for (String name : List.of("haven_small", "haven_medium", "haven_large", "ruin", "dungeon", "keep")) {
            LootTable table = tables.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("chests/" + name)));
            h.assertTrue(table != LootTable.EMPTY, "loot table chests/" + name + " missing");
            int rolls = 800, stacks = 0, empty = 0, gear = 0, catalyst = 0;
            Map<String, Integer> seen = new HashMap<>();
            for (int i = 0; i < rolls; i++) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
                var items = table.getRandomItems(params, random.nextLong());
                if (items.isEmpty()) empty++;
                for (ItemStack stack : items) {
                    stacks++;
                    String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    seen.merge(key, 1, Integer::sum);
                    if (key.startsWith("theoria:catalyst")) catalyst++;
                    if (stack.has(DataComponents.MAX_DAMAGE) || stack.has(DataComponents.EQUIPPABLE) || key.contains("netherite")) gear++;
                }
            }
            h.assertTrue(stacks > rolls, name + ": " + stacks + " stacks in " + rolls + " rolls (table must produce items)");
            h.assertTrue(empty < rolls / 20, name + ": " + empty + " empty rolls of " + rolls + " (partially filled is intended, an empty chest is not)");
            if (name.startsWith("haven")) h.assertTrue(gear == 0, name + ": free gear in a Haven chest (" + gear + " stacks)");
            if (!name.equals("haven_small") && !name.equals("ruin")) h.assertTrue(catalyst > 0, name + ": no catalyst material in " + rolls + " rolls");
            r.line("%-13s %d rolls → %d stacks (%.1f/roll), %d distinct items, catalyst stacks %d, gear stacks %d", name, rolls, stacks, stacks / (double) rolls, seen.size(), catalyst, gear);
        }
        r.write();
        h.succeed();
    }
}
