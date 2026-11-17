package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Insansı: a plain melee walker (zombie-like AI). Wears and holds items like any humanoid; the mainhand weapon deals vanilla damage ({@code MobHooks}, Q35). */
public class HumanoidMob extends TheoriaMob {
    public HumanoidMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
    }
}
