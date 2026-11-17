package dev.theoria.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Zone tier (docs/06 §12): which tier a monster is born at, by distance from the world spawn, depth/height and dimension. The rule is
 * deliberately world-agnostic — Phase 9a maps biomes to tier bands and re-tunes {@code data/balance/mob_zones.csv}.
 * {@code tools/balance/mobs.py} holds the same function ({@code zone_tier}); {@code data/balance/mob_zone_cases.csv} proves they agree.
 */
public final class MobZones {
    public enum Dimension { OVERWORLD, NETHER, END }

    private MobZones() {}

    public static int tier(Dimension dim, double dist, int y) {
        MobData.Zone z = MobData.balance().zone();
        int tier = switch (dim) {
            case OVERWORLD -> 0;
            case NETHER -> z.nether();
            case END -> z.end();
        };
        tier += (int) Math.floor(dist / z.distStep());
        if (y < z.depthFrom()) tier += Math.floorDiv(z.depthFrom() - y, z.depthStep());
        if (y > z.heightFrom()) tier += Math.floorDiv(y - z.heightFrom(), z.heightStep());
        return Math.max(0, Math.min(z.cap(), tier));
    }

    public static Dimension dimensionOf(ServerLevel level) {
        if (level.dimension() == Level.NETHER) return Dimension.NETHER;
        if (level.dimension() == Level.END) return Dimension.END;
        return Dimension.OVERWORLD;
    }

    /** Tier at {@code pos}: horizontal distance to the world spawn point, in that dimension's own coordinates. */
    public static int tierAt(ServerLevelAccessor level, BlockPos pos) {
        BlockPos spawn = level.getLevelData().getRespawnData().pos();
        double dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
        return tier(dimensionOf(level.getLevel()), Math.sqrt(dx * dx + dz * dz), pos.getY());
    }
}
