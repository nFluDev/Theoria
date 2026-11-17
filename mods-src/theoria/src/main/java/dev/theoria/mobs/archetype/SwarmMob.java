package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Sürü: small, quick and weak alone. Spawns in groups (biome modifier min/max) and calls its kin when hurt ({@code alert=1}). */
public class SwarmMob extends TheoriaMob {
    public SwarmMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
    }
}
