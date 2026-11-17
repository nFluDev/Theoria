package dev.theoria.mobs;

import dev.theoria.Theoria;
import dev.theoria.balance.MobScaling;
import dev.theoria.balance.TierRegistry;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base of every Theoria species (docs/05 §4.2). One class per archetype; the species row picks stats and parameters, the tier picks
 * the scaling ({@link MobScaling}). All damage a species deals goes through {@code hurtServer}, so the Phase 2 damage pipeline
 * (tier multipliers, armor formula) applies to melee, projectiles, slams and spells alike.
 * <p>
 * Bosses ({@code cls = boss}) add a boss bar, phase thresholds ({@code phases=0.66|0.33}) that enrage the boss and call
 * {@code adds}, and never despawn.
 */
public abstract class TheoriaMob extends Monster {
    /** bit 0 dormant (sentinel statue) · bit 1 telegraph (windup/charge/cast/breath pose) · bit 2 flying-aggressive. */
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(TheoriaMob.class, EntityDataSerializers.BYTE);
    private static final int DORMANT = 1, TELEGRAPH = 2;

    private final @Nullable ServerBossEvent bossEvent;
    private int phase;

    protected TheoriaMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
        Species sp = species();
        if (sp.flying()) this.moveControl = new FlyingMoveControl(this, 20, true);
        this.bossEvent = sp.boss() ? (ServerBossEvent) new ServerBossEvent(UUID.randomUUID(), getDisplayName(), bossColor(sp), BossEvent.BossBarOverlay.PROGRESS).setDarkenScreen(false) : null;
    }

    public final Species species() {
        return MobRegistry.species(getType());
    }

    /** The archetype's attack goals (priorities 1-5); movement, targeting and looking are added by the base. */
    protected abstract void registerArchetypeGoals(Species species);

    // ------------------------------------------------------------------------------------------------ goals and navigation
    @Override
    protected void registerGoals() {
        Species sp = species();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        registerArchetypeGoals(sp);
        this.goalSelector.addGoal(7, sp.flying() ? new WaterAvoidingRandomFlyingGoal(this, 1.0) : new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        HurtByTargetGoal revenge = new HurtByTargetGoal(this);
        this.targetSelector.addGoal(1, sp.flag("alert") ? revenge.setAlertOthers(TheoriaMob.class) : revenge);
        if (!sp.neutral()) this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (species().flying()) {
            FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
            navigation.setCanOpenDoors(false);
            navigation.setCanFloat(true);
            return navigation;
        }
        return super.createNavigation(level);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return !species().flying() && super.causeFallDamage(fallDistance, damageModifier, source);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, net.minecraft.core.BlockPos pos) {
        if (!species().flying()) super.checkFallDamage(ya, onGround, onState, pos);
    }

    // ------------------------------------------------------------------------------------------------ state and size
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
    }

    private boolean stateBit(int bit) {
        return (this.entityData.get(DATA_STATE) & bit) != 0;
    }

    private void setStateBit(int bit, boolean on) {
        byte now = this.entityData.get(DATA_STATE);
        this.entityData.set(DATA_STATE, (byte) (on ? now | bit : now & ~bit));
    }

    public boolean isDormant() {
        return stateBit(DORMANT);
    }

    public void setDormant(boolean dormant) {
        setStateBit(DORMANT, dormant);
    }

    /** Windup / charge / cast / breath: the client plays the "raised arms, open jaw" pose. */
    public boolean isTelegraphing() {
        return stateBit(TELEGRAPH);
    }

    public void setTelegraphing(boolean on) {
        setStateBit(TELEGRAPH, on);
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return species().xp();   // × the tier's xp multiplier: MobHooks.onExperience
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return !species().boss() && super.removeWhenFarAway(distanceSqr);
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return !species().neutral() && super.isPreventingPlayerRest(level, player);
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return !species().boss() && super.canUsePortal(allowPassengers);
    }


    // ------------------------------------------------------------------------------------------------ sounds (vanilla ones, by archetype)
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return switch (species().archetype()) {
            case HUMANOID -> net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT;
            case PREDATOR -> net.minecraft.sounds.SoundEvents.FOX_AMBIENT;
            case SWARM -> net.minecraft.sounds.SoundEvents.SILVERFISH_AMBIENT;
            case CHARGER -> net.minecraft.sounds.SoundEvents.HOGLIN_AMBIENT;
            case SHOOTER -> net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;
            case CASTER -> net.minecraft.sounds.SoundEvents.WITCH_AMBIENT;
            case FLYER -> net.minecraft.sounds.SoundEvents.PHANTOM_AMBIENT;
            case SERPENT -> net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
            case ASSASSIN -> net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
            case DRAKE -> net.minecraft.sounds.SoundEvents.ENDER_DRAGON_AMBIENT;
            case GIANT, SENTINEL -> null;
        };
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return switch (species().archetype()) {
            case HUMANOID -> net.minecraft.sounds.SoundEvents.ZOMBIE_HURT;
            case PREDATOR -> net.minecraft.sounds.SoundEvents.FOX_HURT;
            case SWARM -> net.minecraft.sounds.SoundEvents.SILVERFISH_HURT;
            case CHARGER -> net.minecraft.sounds.SoundEvents.HOGLIN_HURT;
            case SHOOTER -> net.minecraft.sounds.SoundEvents.SKELETON_HURT;
            case CASTER -> net.minecraft.sounds.SoundEvents.WITCH_HURT;
            case FLYER -> net.minecraft.sounds.SoundEvents.PHANTOM_HURT;
            case SERPENT -> net.minecraft.sounds.SoundEvents.SPIDER_HURT;
            case ASSASSIN -> net.minecraft.sounds.SoundEvents.ENDERMAN_HURT;
            case DRAKE -> net.minecraft.sounds.SoundEvents.ENDER_DRAGON_HURT;
            case GIANT, SENTINEL -> net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;
        };
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return switch (species().archetype()) {
            case HUMANOID -> net.minecraft.sounds.SoundEvents.ZOMBIE_DEATH;
            case PREDATOR -> net.minecraft.sounds.SoundEvents.FOX_DEATH;
            case SWARM -> net.minecraft.sounds.SoundEvents.SILVERFISH_DEATH;
            case CHARGER -> net.minecraft.sounds.SoundEvents.HOGLIN_DEATH;
            case SHOOTER -> net.minecraft.sounds.SoundEvents.SKELETON_DEATH;
            case CASTER -> net.minecraft.sounds.SoundEvents.WITCH_DEATH;
            case FLYER -> net.minecraft.sounds.SoundEvents.PHANTOM_DEATH;
            case SERPENT -> net.minecraft.sounds.SoundEvents.SPIDER_DEATH;
            case ASSASSIN -> net.minecraft.sounds.SoundEvents.ENDERMAN_DEATH;
            case DRAKE -> net.minecraft.sounds.SoundEvents.ENDER_DRAGON_DEATH;
            case GIANT, SENTINEL -> net.minecraft.sounds.SoundEvents.IRON_GOLEM_DEATH;
        };
    }

    // ------------------------------------------------------------------------------------------------ spawn
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (TierRegistry.loaded()) MobScaling.assign(this, species().tier());
        return result;
    }

    // ------------------------------------------------------------------------------------------------ boss (generic: any archetype)
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent != null) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (bossEvent != null) bossEvent.setName(getDisplayName());
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (bossEvent != null) {
            bossEvent.setProgress(getHealth() / getMaxHealth());
            advancePhase(level);
        }
    }

    public int phase() {
        return phase;
    }

    /** Boss bar progress (0..1), or -1 for a species without a bar. */
    public float bossProgress() {
        return bossEvent == null ? -1.0F : bossEvent.getProgress();
    }

    /** Thresholds as health fractions, e.g. {@code 0.66|0.33}; crossing one = enrage + call adds. */
    private double[] thresholds() {
        String text = species().text("phases");
        if (text.isEmpty()) return new double[0];
        String[] parts = text.split("\\|");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    private void advancePhase(ServerLevel level) {
        double[] t = thresholds();
        double fraction = getHealth() / getMaxHealth();
        while (phase < t.length && fraction <= t[phase]) {
            phase++;
            onPhaseStart(level, phase);
        }
    }

    /** Enrage: +10 % speed per phase (a transient-free permanent modifier so it survives a save), then the adds. */
    protected void onPhaseStart(ServerLevel level, int newPhase) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(Theoria.id("boss_enrage"),
                    0.10 * newPhase, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() * 0.8, getZ(), 12, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.0);
        playSound(net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 2.0F, 0.7F);
        for (ServerPlayer player : bossEvent != null ? bossEvent.getPlayers() : java.util.List.<ServerPlayer>of()) {
            player.sendSystemMessage(Component.translatable("theoria.mob.boss.phase", getDisplayName()), true);
        }
        callAdds(level);
    }

    protected void callAdds(ServerLevel level) {
        String addsId = species().text("adds");
        Species adds = addsId.isEmpty() ? null : MobData.byId(addsId);
        if (adds == null) return;
        EntityType<?> type = MobRegistry.type(adds.id());
        int count = species().integer("add_count", 2);
        for (int i = 0; i < count; i++) {
            Entity add = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (add == null) continue;
            double angle = random.nextDouble() * Math.PI * 2, radius = 2.0 + getBbWidth();
            add.snapTo(getX() + Math.cos(angle) * radius, getY(), getZ() + Math.sin(angle) * radius, random.nextFloat() * 360F, 0F);
            if (add instanceof TheoriaMob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                if (getTarget() != null) mob.setTarget(getTarget());
            }
            level.addFreshEntity(add);
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 20, 1.0, 0.5, 1.0, 0.05);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (species().boss()) output.putInt("theoria_phase", phase);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        phase = input.getIntOr("theoria_phase", 0);
        if (bossEvent != null && hasCustomName()) bossEvent.setName(getDisplayName());
    }

    private static BossEvent.BossBarColor bossColor(Species sp) {
        return switch (sp.family()) {
            case "swamp" -> BossEvent.BossBarColor.GREEN;
            case "mountain", "frost" -> BossEvent.BossBarColor.BLUE;
            case "ember" -> BossEvent.BossBarColor.RED;
            case "shadow" -> BossEvent.BossBarColor.PURPLE;
            case "ancient" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.YELLOW;
        };
    }

    // ------------------------------------------------------------------------------------------------ helpers for archetypes
    /** Hits every living non-species entity within {@code radius} (the damage pipeline scales it): AoE slams, breath. Returns the number hit. */
    protected int areaDamage(ServerLevel level, Vec3 center, double radius, double factor, double knockback) {
        int hit = 0;
        AABB box = new AABB(center, center).inflate(radius, radius * 0.6, radius);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !(e instanceof TheoriaMob) && e.isAlive() && e.position().distanceToSqr(center) <= radius * radius)) {
            if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            float amount = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * factor);
            if (victim.hurtServer(level, damageSources().mobAttack(this), amount)) {
                hit++;
                if (knockback > 0) victim.knockback(knockback, center.x - victim.getX(), center.z - victim.getZ(), damageSources().mobAttack(this), amount);
            }
        }
        return hit;
    }

    protected void ringParticles(ServerLevel level, ParticleOptions particle, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            level.sendParticles(particle, getX() + Math.cos(a) * radius, getY() + 0.1, getZ() + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Is {@code this} behind {@code victim} (more than 120° away from where the victim looks)? Used by the assassin. */
    protected boolean isBehind(LivingEntity victim) {
        Vec3 toMe = position().subtract(victim.position()).multiply(1, 0, 1);
        Vec3 forward = victim.getLookAngle().multiply(1, 0, 1);
        if (toMe.lengthSqr() < 1.0E-6 || forward.lengthSqr() < 1.0E-6) return false;
        return forward.normalize().dot(toMe.normalize()) < -0.5;
    }

    /** Convenience for archetype goals. */
    protected abstract static class ArchetypeGoal extends Goal {
        protected final TheoriaMob mob;

        protected ArchetypeGoal(TheoriaMob mob) {
            this.mob = mob;
        }
    }
}
