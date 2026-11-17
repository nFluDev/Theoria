package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

/** Suikastçı: fast; strikes {@code backstab}× harder from behind and blinks behind its target ({@code blink=1}, every {@code blink_cd} ticks). */
public class AssassinMob extends TheoriaMob {
    private static final Identifier BACKSTAB = dev.theoria.Theoria.id("backstab");
    private int blinkCooldown;

    public AssassinMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        if (species.flag("blink")) this.goalSelector.addGoal(2, new BlinkGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, false));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean backstab = damage != null && target instanceof LivingEntity victim && isBehind(victim);
        if (backstab) {
            damage.addTransientModifier(new AttributeModifier(BACKSTAB, species().num("backstab", 1.5) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
        try {
            return super.doHurtTarget(level, target);
        } finally {
            if (backstab) damage.removeModifier(BACKSTAB);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (blinkCooldown > 0) blinkCooldown--;
    }

    private final class BlinkGoal extends Goal {
        BlinkGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || blinkCooldown > 0 || !(level() instanceof ServerLevel)) return false;
            double d = distanceTo(target);
            return d > 6.0 && d < 18.0 && hasLineOfSight(target);
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            ServerLevel level = (ServerLevel) level();
            blinkCooldown = species().integer("blink_cd", 100);
            Vec3 back = target.getLookAngle().multiply(1, 0, 1);
            if (back.lengthSqr() < 1.0E-6) return;
            Vec3 spot = target.position().subtract(back.normalize().scale(2.0));
            Vec3 from = position();
            level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 20, 0.3, 0.6, 0.3, 0.3);
            if (randomTeleport(spot.x, spot.y, spot.z, true, state -> state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))) {
                level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.6, 0.3, 0.3);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.2F);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
