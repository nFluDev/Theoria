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
import net.minecraft.world.phys.Vec3;

/** Boğa: stamps for a second ({@code charge_cd} ticks apart), then rushes its target at {@code charge_speed}× speed and hits it with a heavy shove. */
public class ChargerMob extends TheoriaMob {
    public ChargerMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new ChargeGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    private final class ChargeGoal extends Goal {
        private int cooldown = 40, windup, rush;
        private Vec3 aim = Vec3.ZERO;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity target = getTarget();
            if (target == null || !onGround()) return false;
            double d = distanceTo(target);
            return d >= 4.0 && d <= 16.0 && hasLineOfSight(target);
        }

        @Override
        public void start() {
            getNavigation().stop();
            windup = 20;
            rush = 0;
            setTelegraphing(true);
            playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 1.1F);
        }

        @Override
        public boolean canContinueToUse() {
            return getTarget() != null && (windup > 0 || rush > 0);
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            if (windup > 0) {
                getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (level() instanceof ServerLevel sl && windup % 5 == 0) sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 4, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.02);
                if (--windup == 0) {
                    aim = target.position();
                    rush = 30;
                }
                return;
            }
            rush--;
            getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, species().num("charge_speed", 1.6));
            if (distanceToSqr(target) < Math.pow(getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 0.8, 2) && level() instanceof ServerLevel sl) {
                swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                doHurtTarget(sl, target);
                target.knockback(1.4, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(ChargerMob.this), 0.0F);
                rush = 0;
            } else if (horizontalCollision && rush < 24) {
                rush = 0;   // ran into a wall: the rush ends
            }
        }

        @Override
        public void stop() {
            setTelegraphing(false);
            cooldown = species().integer("charge_cd", 100);
        }
    }
}
