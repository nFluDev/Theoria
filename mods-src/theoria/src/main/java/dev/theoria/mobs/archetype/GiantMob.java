package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Dev: slow and heavy. Every {@code slam_cd} ticks, when something is within reach, it raises its arms for 0.9 s and slams the ground: {@code slam_radius}-block AoE with a shove. */
public class GiantMob extends TheoriaMob {
    public GiantMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new SlamGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    private final class SlamGoal extends Goal {
        private int cooldown = 40, windup;

        SlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && distanceTo(target) <= species().num("slam_radius", 4.0) * 0.85;
        }

        @Override
        public void start() {
            windup = 18;
            getNavigation().stop();
            setTelegraphing(true);
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target != null) getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--windup > 0 || !(level() instanceof ServerLevel level)) return;
            double radius = species().num("slam_radius", 4.0);
            areaDamage(level, position(), radius, 0.9, 1.2);
            ringParticles(level, ParticleTypes.POOF, radius, 32);
            ringParticles(level, ParticleTypes.CRIT, radius * 0.6, 16);
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
        }

        @Override
        public void stop() {
            setTelegraphing(false);
            cooldown = species().integer("slam_cd", 90);
        }
    }
}
