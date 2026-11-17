package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Uçan: circles above its target and dives ({@code swoop=1}): hover (up to 4 s) → dive at 1.8× speed → recover for 2.5 s. Hits on contact. */
public class FlyerMob extends TheoriaMob {
    public FlyerMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        if (species.flag("swoop")) this.goalSelector.addGoal(2, new SwoopGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
    }

    private final class SwoopGoal extends Goal {
        private int phase, ticks, cooldown = 40;   // 0 hover · 1 dive · 2 recover

        SwoopGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            phase = 0;
            ticks = 0;
            setTelegraphing(false);
        }

        @Override
        public boolean canContinueToUse() {
            return getTarget() != null && getTarget().isAlive() && !(phase == 2 && ticks > 50);
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            ticks++;
            double speed = species().speed();
            switch (phase) {
                case 0 -> {
                    getMoveControl().setWantedPosition(target.getX() + Math.cos(ticks * 0.15) * 4.0, target.getY() + 5.5, target.getZ() + Math.sin(ticks * 0.15) * 4.0, 1.0);
                    if (ticks > 20 && Math.abs(getY() - (target.getY() + 5.5)) < 2.0 || ticks > 80) {
                        phase = 1;
                        ticks = 0;
                        setTelegraphing(true);
                    }
                }
                case 1 -> {
                    getMoveControl().setWantedPosition(target.getX(), target.getEyeY() - 0.4, target.getZ(), 1.8);
                    if (getBoundingBox().inflate(0.4).intersects(target.getBoundingBox()) && level() instanceof ServerLevel sl) {
                        swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                        doHurtTarget(sl, target);
                        phase = 2;
                        ticks = 0;
                        setTelegraphing(false);
                    } else if (ticks > 30) {
                        phase = 2;
                        ticks = 0;
                        setTelegraphing(false);
                    }
                }
                default -> getMoveControl().setWantedPosition(getX() + (getX() - target.getX()) * 0.5, target.getY() + 7.0, getZ() + (getZ() - target.getZ()) * 0.5, 1.2);
            }
        }

        @Override
        public void stop() {
            setTelegraphing(false);
            cooldown = 20;
        }
    }
}
