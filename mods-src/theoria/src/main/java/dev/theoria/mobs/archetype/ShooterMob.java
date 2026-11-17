package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Nişancı: keeps its distance and shoots ({@code projectile=arrow|fireball}, {@code range}, {@code cooldown}). The projectile's owner is the mob, so the
 * damage pipeline scales the hit with its tier like any other mob damage. An arrow is tuned so the hit equals the species' base damage (arrow speed 1.6).
 */
public class ShooterMob extends TheoriaMob implements RangedAttackMob {
    private static final float ARROW_SPEED = 1.6F;

    public ShooterMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, species.integer("cooldown", 40), (float) species.num("range", 20.0)));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel level)) return;
        double xd = target.getX() - getX(), yd = target.getY(0.3333) - getEyeY(), zd = target.getZ() - getZ();
        double flat = Math.sqrt(xd * xd + zd * zd);
        if ("fireball".equals(species().text("projectile"))) {
            Vec3 direction = new Vec3(xd, target.getY(0.5) - getY(0.5), zd).normalize();
            SmallFireball ball = new SmallFireball(level, this, direction);
            ball.setPos(getX(), getY(0.5) + 0.5, getZ());
            level.addFreshEntity(ball);
            playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 1.0F);
            return;
        }
        ItemStack ammo = new ItemStack(Items.ARROW);
        Arrow arrow = new Arrow(level, this, ammo, null);
        arrow.setBaseDamage(getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) / ARROW_SPEED);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Projectile.spawnProjectileUsingShoot(arrow, level, ammo, xd, yd + flat * 0.2, zd, ARROW_SPEED, 4.0F);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
