package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Yılan: low and quick; with {@code hit_run=1} it bites, then slithers away for 1.5 s before coming back. */
public class SerpentMob extends TheoriaMob {
    private int retreat;

    public SerpentMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        if (species.flag("hit_run")) this.goalSelector.addGoal(1, new RetreatGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && species().flag("hit_run")) retreat = 30;
        return hit;
    }

    private final class RetreatGoal extends Goal {
        RetreatGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return retreat > 0 && getTarget() != null;
        }

        @Override
        public void start() {
            Vec3 away = LandRandomPos.getPosAway(SerpentMob.this, 10, 4, getTarget().position());
            if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.4);
        }

        @Override
        public void tick() {
            retreat--;
        }

        @Override
        public boolean canContinueToUse() {
            return retreat > 0 && !getNavigation().isDone();
        }

        @Override
        public void stop() {
            retreat = 0;
        }
    }
}
