package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

/**
 * Ejder: circles its target 9 blocks up; every {@code cooldown} ticks it breathes ({@code breath=lightning}: a visual-only bolt and a 3-block AoE at the
 * target), and every third breath it dives. Phases, enrage and adds come from the boss base.
 */
public class DrakeMob extends TheoriaMob {
    public DrakeMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new SkyGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
    }

    private final class SkyGoal extends Goal {
        private int ticks, breaths, windup, diveTicks, cooldown = 60;
        private Vec3 aim = Vec3.ZERO;

        SkyGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            ticks++;
            if (diveTicks > 0) {
                diveTicks--;
                getMoveControl().setWantedPosition(target.getX(), target.getEyeY(), target.getZ(), 1.7);
                if (getBoundingBox().inflate(0.5).intersects(target.getBoundingBox()) && level() instanceof ServerLevel sl) {
                    swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                    doHurtTarget(sl, target);
                    diveTicks = 0;
                }
                setTelegraphing(diveTicks > 0);
                return;
            }
            double angle = ticks * 0.04;
            getMoveControl().setWantedPosition(target.getX() + Math.cos(angle) * 10.0, target.getY() + 9.0, target.getZ() + Math.sin(angle) * 10.0, 1.0);
            getLookControl().setLookAt(target, 20.0F, 20.0F);
            if (windup > 0) {
                if (--windup == 0) breathe(target);
                return;
            }
            if (cooldown-- <= 0 && distanceTo(target) <= species().num("range", 28.0)) {
                cooldown = species().integer("cooldown", 70);
                if (++breaths % 3 == 0) {
                    diveTicks = 40;
                    setTelegraphing(true);
                } else {
                    windup = 20;
                    aim = target.position();
                    setTelegraphing(true);
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.0F);
                }
            }
        }

        private void breathe(LivingEntity target) {
            setTelegraphing(false);
            if (!(level() instanceof ServerLevel level)) return;
            Vec3 at = target.position().distanceToSqr(aim) < 36.0 ? target.position() : aim;
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(at.x, at.y, at.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            areaDamage(level, at, 3.0, 1.0, 0.8);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 30, 1.2, 1.0, 1.2, 0.3);
        }

        @Override
        public void stop() {
            setTelegraphing(false);
        }
    }
}
