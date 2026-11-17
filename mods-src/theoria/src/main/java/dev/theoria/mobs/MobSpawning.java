package dev.theoria.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

/** Natural spawn rule of a species: its Y range and zone band on top of the vanilla light/surface rule. Eggs, commands, spawners and structures bypass the band. */
public final class MobSpawning {
    private MobSpawning() {}

    public static boolean canSpawn(Species sp, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (sp.boss() || !inBand(sp, level, pos)) return false;
        var type = MobRegistry.type(sp.id());
        return sp.neutral() ? Mob.checkMobSpawnRules(type, level, reason, pos, random)
                : net.minecraft.world.entity.monster.Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /** Y inside the species' range and the zone tier within {@code band} of the species tier. */
    public static boolean inBand(Species sp, ServerLevelAccessor level, BlockPos pos) {
        if (pos.getY() < sp.minY() || pos.getY() > sp.maxY()) return false;
        return Math.abs(MobZones.tierAt(level, pos) - sp.tier()) <= MobData.balance().zone().band();
    }
}
