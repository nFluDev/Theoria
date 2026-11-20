package dev.theoria.worlds;

import dev.theoria.Theoria;
import dev.theoria.core.check.Checks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;

/**
 * Area "worlds" of {@code /theoria test}: the generated worldgen data as loaded by the running game (biomes registered and tagged, ore features only in
 * their tagged biomes, structures with templates that exist, Havens without natural spawns, the TerraBlender region present). Read-only: safe in a survival world.
 * Expected counts come from the generated tags themselves; the CSV-side expectations live in tools/check_worldgen.py and the GameTests.
 */
public final class WorldChecks {
    /** docs/05 §5: v1 ≥ 20 Theoria biomes. */
    private static final int MIN_BIOMES = 20;

    private WorldChecks() {}

    static void init() {
        Checks.register("worlds", "biomes", s -> biomes(s.getServer()));
        Checks.register("worlds", "ores", s -> ores(s.getServer()));
        Checks.register("worlds", "structures", s -> structures(s.getServer()));
        Checks.register("worlds", "havens", s -> havens(s.getServer()));
        Checks.register("worlds", "terrablender", s -> terrablender(s.getServer()));
        Checks.register("worlds", "feature_order", s -> featureOrder(s.getServer()));
    }

    /**
     * The crash 9b shipped: once Theoria biomes took part in world generation, vanilla's {@code FeatureSorter} found a cycle in their hand-written per-step feature lists
     * ("Feature order cycle found, involved sources: [theoria:storm_islands, theoria:iron_uplands, theoria:steel_steppe]") and chunk generation died. This runs the real
     * sorter over every Overworld biome the running game has (vanilla + Theoria + whatever else the pack adds, e.g. Terralith in a real world), with NeoForge biome
     * modifiers already applied, exactly as ChunkGenerator does on its first chunk.
     */
    private static String featureOrder(MinecraftServer server) {
        Registry<Biome> registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        List<Holder<Biome>> overworld = tagged(registry, BiomeTags.IS_OVERWORLD);
        Checks.require(!overworld.isEmpty(), "#minecraft:is_overworld is empty");
        try {
            FeatureSorter.buildFeaturesPerStep(overworld, biome -> biome.value().getGenerationSettings().features(), true);
        } catch (IllegalStateException e) {
            Checks.require(false, "feature order cycle among " + overworld.size() + " Overworld biomes: " + e.getMessage());
        }
        long ours = overworld.stream().filter(b -> b.getRegisteredName().startsWith("theoria:")).count();
        return overworld.size() + " Overworld biomes (" + ours + " Theoria): feature order has no cycle";
    }

    private static <T> List<Holder<T>> tagged(Registry<T> registry, TagKey<T> tag) {
        List<Holder<T>> out = new ArrayList<>();
        registry.get(tag).ifPresent(set -> set.forEach(out::add));
        return out;
    }

    private static String biomes(MinecraftServer server) {
        Registry<Biome> registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        List<Holder<Biome>> ours = tagged(registry, TagKey.create(Registries.BIOME, Theoria.id("is_theoria")));
        Checks.require(ours.size() >= MIN_BIOMES, "theoria:is_theoria has " + ours.size() + " biomes, want >= " + MIN_BIOMES);
        for (Holder<Biome> biome : ours) {
            Checks.require(biome.is(BiomeTags.IS_OVERWORLD), biome.getRegisteredName() + " is not in #minecraft:is_overworld");
            Checks.require(biome.value().getGenerationSettings().features().size() == 11, biome.getRegisteredName() + ": feature steps");
        }
        int land = tagged(registry, TagKey.create(Registries.BIOME, Theoria.id("land"))).size();
        Checks.require(land > 0 && land < ours.size(), "theoria:land must be a proper subset");
        return ours.size() + " biomes registered + tagged overworld, " + land + " surface (land) biomes";
    }

    private static String ores(MinecraftServer server) {
        Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        int ores = 0, pairs = 0, wildPairs = 0;
        List<Holder<Biome>> overworld = tagged(biomes, BiomeTags.IS_OVERWORLD);
        for (var tagEntry : biomes.getTags().toList()) {
            String path = tagEntry.key().location().getPath();
            if (!tagEntry.key().location().getNamespace().equals(Theoria.MODID) || !path.startsWith("has_ore/")) continue;
            String ore = path.substring("has_ore/".length());
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, Theoria.id("ore_" + ore));
            Checks.require(placed.get(key).isPresent(), "placed feature theoria:ore_" + ore + " missing");
            Checks.require(tagEntry.size() > 0, "empty tag " + path);
            for (Holder<Biome> biome : biomes.listElements().toList()) {
                boolean carries = biome.value().getGenerationSettings().features().stream().anyMatch(step -> step.stream().anyMatch(p -> p.is(key)));
                boolean tagged = biome.is(tagEntry.key());
                Checks.require(carries == tagged, "ore_" + ore + " in " + biome.getRegisteredName() + ": carries=" + carries + " tagged=" + tagged);
                if (carries) pairs++;
            }
            // Hybrid (docs/10 Q63): the sparse "wild" vein goes into EVERY Overworld biome (vanilla, Terralith, ours), the home vein only into its has_ore biomes
            ResourceKey<PlacedFeature> wildKey = ResourceKey.create(Registries.PLACED_FEATURE, Theoria.id("ore_" + ore + "_wild"));
            Checks.require(placed.get(wildKey).isPresent(), "placed feature theoria:ore_" + ore + "_wild missing");
            for (Holder<Biome> biome : overworld) {
                boolean carriesWild = biome.value().getGenerationSettings().features().stream().anyMatch(step -> step.stream().anyMatch(p -> p.is(wildKey)));
                Checks.require(carriesWild, "ore_" + ore + "_wild is missing from Overworld biome " + biome.getRegisteredName());
                wildPairs++;
            }
            ores++;
        }
        Checks.require(ores >= 7, "expected >= 7 has_ore tags, found " + ores);
        return ores + " ore features: home vein in exactly the biomes of its has_ore tag (" + pairs + " pairs), wild vein in all " + overworld.size() + " Overworld biomes (" + wildPairs + " pairs)";
    }

    private static String structures(MinecraftServer server) {
        Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        int count = 0, templates = 0;
        for (String group : new String[] {"havens", "ruins", "dungeons", "keeps"}) {
            List<Holder<Structure>> members = tagged(registry, TagKey.create(Registries.STRUCTURE, Theoria.id(group)));
            Checks.require(!members.isEmpty(), "#theoria:" + group + " is empty");
            for (Holder<Structure> structure : members) {
                Identifier id = structure.unwrapKey().orElseThrow().identifier();
                StructureTemplatePool pool = pools.getValue(id);
                Checks.require(pool != null && !pool.getTemplates().isEmpty(), "template pool " + id + " missing");
                for (var pair : pool.getTemplates()) {
                    StructurePoolElement element = pair.getFirst();
                    var size = element.getSize(server.getStructureTemplateManager(), Rotation.NONE);
                    Checks.require(size.getX() * size.getY() * size.getZ() > 0, id + ": a pool template is missing or empty");
                    templates++;
                }
                count++;
            }
        }
        return count + " structures in 4 groups, " + templates + " pool templates present and non-empty";
    }

    private static String havens(MinecraftServer server) {
        Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> members = tagged(registry, TagKey.create(Registries.STRUCTURE, Theoria.id("havens")));
        Checks.require(members.size() >= 3, "expected >= 3 Haven structures (S/M/L), found " + members.size());
        for (Holder<Structure> haven : members) {
            for (MobCategory category : MobCategory.values()) {
                var override = haven.value().spawnOverrides().get(category);
                Checks.require(override != null && override.spawns().isEmpty(), haven.getRegisteredName() + ": natural spawns for " + category.getName() + " are not blocked");
            }
        }
        return members.size() + " Haven structures block every natural spawn category (" + MobCategory.values().length + " categories)";
    }

    /** The real-biome-source probe: columns per side, spacing, and the Y levels (surface, cave) sampled. */
    private static final int GRID = 24, STEP = 96;
    private static final int[] SAMPLE_Y = {64, -40};
    /** Thresholds are well under the measured values (see docs/journal/f09.md); they only separate "the region is in world generation" from "it is not". */
    private static final int MIN_SHARE_PERCENT = 5, MIN_DISTINCT = 6;

    /**
     * "Registry has the region" is NOT enough (9b's first version stopped there and shipped a region that never generated: TerraBlender 26.3.0.0.9
     * builds its biome search trees in ServerAboutToStart BEFORE it loads the data-driven regions). So this also samples the REAL overworld biome source
     * (the registry's LevelStem, the object TerraBlender mutates; the GameTest level itself is flat) and requires Theoria biomes to actually come out of it.
     */
    private static String terrablender(MinecraftServer server) {
        if (!ModList.get().isLoaded("terrablender")) return "TerraBlender not loaded: biomes exist but are not placed by world generation (skipped)";
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("terrablender", "region"));
        Registry<Object> regions = server.registryAccess().lookup(key).orElse(null);
        Checks.require(regions != null, "terrablender:region registry missing");
        Checks.require(regions.containsKey(Theoria.id("overworld")), "region theoria:overworld not loaded by TerraBlender");

        Checks.require(TerraBlenderBridge.ok(), TerraBlenderBridge.status());

        var access = server.registryAccess();
        ChunkGenerator generator = access.lookupOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD).orElseThrow().value().generator();
        if (!(generator instanceof NoiseBasedChunkGenerator)) {
            // GameTest worlds are flat: there is no real biome source to sample. worlds_terrablender_generation builds the real one from the normal preset instead.
            return "region registered; " + TerraBlenderBridge.status() + "; sampling skipped (overworld is " + generator.getClass().getSimpleName() + ", run in a normal world)";
        }
        Probe p = probe(access, generator);
        Checks.require(p.hits() * 100 >= p.total() * MIN_SHARE_PERCENT,
                "region theoria:overworld is registered but the overworld biome source yields " + p.hits() + "/" + p.total() + " Theoria biomes (< " + MIN_SHARE_PERCENT + " %): it never entered world generation");
        Checks.require(p.seen().size() >= MIN_DISTINCT, "only " + p.seen().size() + " distinct Theoria biomes in " + p.total() + " samples (want >= " + MIN_DISTINCT + "): " + p.seen());
        return "region theoria:overworld in world generation: " + p.summary();
    }

    /** Theoria biomes found by {@link #probe}: {@code hits} of {@code total} samples, and which of the 20. */
    public record Probe(int hits, int total, Set<String> seen) {
        public String summary() {
            return hits + "/" + total + " samples (" + (total == 0 ? 0 : hits * 100 / total) + " %), " + seen.size() + " distinct biomes";
        }
    }

    /**
     * Samples a noise generator's biome source on a {@value #GRID}×{@value #GRID} grid (spacing {@value #STEP}) at the surface and in caves, through the 26.3
     * resolver world generation uses (TerraBlender wraps the lookup inside it), and counts the {@code theoria:} biomes. Public: the GameTest builds a real
     * overworld generator and calls this too.
     */
    public static Probe probe(RegistryAccess access, ChunkGenerator generator) {
        var settings = ((NoiseBasedChunkGenerator) generator).generatorSettings();
        RandomState state = RandomState.create(access.lookupOrThrow(Registries.NOISE), 7001L, settings.value());
        Climate.Sampler sampler = state.createClimateSampler(SamplerContext.builder().enableCaches().build());
        BiomeResolver resolver = generator.getBiomeSource().createResolver(sampler);
        Set<String> seen = new TreeSet<>();
        int hits = 0, total = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int x = (i - GRID / 2) * STEP, z = (j - GRID / 2) * STEP;
                for (int y : SAMPLE_Y) {
                    String name = resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2).getRegisteredName();
                    total++;
                    if (name.startsWith("theoria:")) {
                        hits++;
                        seen.add(name.substring("theoria:".length()));
                    }
                }
            }
        }
        return new Probe(hits, total, seen);
    }
}
