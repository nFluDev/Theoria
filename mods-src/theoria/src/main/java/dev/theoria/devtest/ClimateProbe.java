package dev.theoria.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Phase 9b measurement aid (not an assertion): samples the REAL vanilla overworld climate (the same density-function router world generation uses) on a
 * grid for fixed seeds, at the surface and at fixed Y levels, and writes {@code build/dev-out/world/climate_<seed>.csv}. {@code generator/biomes.py --share}
 * maps each sample to the nearest of the region's climate boxes (the rule TerraBlender/vanilla use) to estimate each biome's share of the region and how
 * often each ore's Y range lies inside its biomes. It does not simulate TerraBlender's region weighting and does not replace the Chunky survey.
 */
final class ClimateProbe {
    static final long[] SEEDS = {1101L, 2202L, 3303L, 4404L, 5505L};
    private static final int COLUMNS = 40, STEP = 48;                       // 40×40 columns every 48 blocks = a 1 920-block square per seed
    private static final int[] YS = {-60, -48, -36, -24, -12, 0, 16, 32, 48, 64, 80, 96, 128, 160, 192, 224, 256, 288};
    private static final LevelHeightAccessor TALL = new LevelHeightAccessor() {
        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    };

    private ClimateProbe() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("worlds_climate_probe", ClimateProbe::run);
    }

    private static void run(GameTestHelper h) {
        Dev.Report r = new Dev.Report("worlds_climate_probe");
        var access = h.getLevel().registryAccess();
        var settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var noises = access.lookupOrThrow(Registries.NOISE);
        var plains = access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), settings);
        for (long seed : SEEDS) {
            RandomState state = RandomState.create(noises, seed, settings.value());
            Climate.Sampler sampler = state.createClimateSampler(SamplerContext.builder().enableCaches().build());
            List<String> rows = new ArrayList<>();
            rows.add("seed,x,z,layer,y,t,h,c,e,d,w");
            for (int i = 0; i < COLUMNS; i++) {
                for (int j = 0; j < COLUMNS; j++) {
                    int x = (i - COLUMNS / 2) * STEP, z = (j - COLUMNS / 2) * STEP;
                    int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, TALL, state);
                    rows.add(row(seed, x, z, "surface", surface, sampler.sample(x >> 2, surface >> 2, z >> 2)));
                    if ((i + j) % 4 == 0) {                                  // every 4th column carries the Y profile (ore ranges)
                        for (int y : YS) rows.add(row(seed, x, z, "y", y, sampler.sample(x >> 2, y >> 2, z >> 2)));
                    }
                }
            }
            Dev.Report.write("world/climate_" + seed + ".csv", rows);
            r.line("seed %d: %d samples → world/climate_%d.csv", seed, rows.size() - 1, seed);
        }
        r.write();
        h.succeed();
    }

    private static String row(long seed, int x, int z, String layer, int y, Climate.TargetPoint p) {
        return String.format(java.util.Locale.ROOT, "%d,%d,%d,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f", seed, x, z, layer, y,
                Climate.unquantizeCoord(p.temperature()), Climate.unquantizeCoord(p.humidity()), Climate.unquantizeCoord(p.continentalness()),
                Climate.unquantizeCoord(p.erosion()), Climate.unquantizeCoord(p.depth()), Climate.unquantizeCoord(p.weirdness()));
    }
}
