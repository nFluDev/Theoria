package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;

/** Yırtıcı: fast quadruped that leaps at its prey ({@code leap}) and, for spiders, climbs walls ({@code climb=1}). */
public class PredatorMob extends TheoriaMob {
    public PredatorMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        double leap = species.num("leap", 0.0);
        if (leap > 0) this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, (float) leap));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return species().flag("climb") ? new WallClimberNavigation(this, level) : super.createNavigation(level);
    }

    @Override
    public boolean onClimbable() {
        return species().flag("climb") && this.horizontalCollision;
    }
}
