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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Büyücü: backs away from players and, every {@code cooldown} ticks, casts at a target within {@code range}: magic damage that bypasses armor
 * (the damage pipeline scales it with the tier's <em>bypass</em> multiplier, docs/06 §4) and the {@code cast} effect ({@code effect:ticks:amplifier}).
 */
public class CasterMob extends TheoriaMob {
    public CasterMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new CastGoal());
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.0, 1.2));
    }

    private final class CastGoal extends Goal {
        private int cooldown = 30, windup;

        CastGoal() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && distanceTo(target) <= species().num("range", 16.0) && hasLineOfSight(target);
        }

        @Override
        public void start() {
            windup = 12;
            setTelegraphing(true);
            playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.2F);
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--windup > 0 || !(level() instanceof ServerLevel level)) return;
            target.hurtServer(level, damageSources().indirectMagic(CasterMob.this, CasterMob.this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            dev.theoria.mobs.MobHooks.applyEffect(target, species().text("cast"));
            Vec3 from = getEyePosition(), to = target.getEyePosition();
            for (int i = 1; i <= 8; i++) {
                Vec3 p = from.lerp(to, i / 8.0);
                level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.1);
            }
            playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            setTelegraphing(false);
            cooldown = species().integer("cooldown", 70);
        }
    }
}
