package dev.theoria.mobs.archetype;

import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Nöbetçi: a statue (or a burrowed worm) that wakes when a player comes within {@code wake_range} or when it is hurt; dormant it does not act at all. */
public class SentinelMob extends TheoriaMob {
    public SentinelMob(EntityType<? extends TheoriaMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerArchetypeGoals(Species species) {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        setDormant(species().flag("dormant"));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Dormant = no AI step at all (LivingEntity#aiStep skips serverAiStep for an immobile entity). */
    @Override
    protected boolean isImmobile() {
        return isDormant() || super.isImmobile();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!isDormant()) return;
        Player near = level.getNearestPlayer(this, species().num("wake_range", 5.0));
        if (near != null && !near.isCreative() && !near.isSpectator()) wake(level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && isDormant()) wake(level);
        return hurt;
    }

    public void wake(ServerLevel level) {
        setDormant(false);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5, getZ(), 16, getBbWidth() * 0.4, 0.4, getBbWidth() * 0.4, 0.05);
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.2F, 0.6F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("theoria_dormant", isDormant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setDormant(input.getBooleanOr("theoria_dormant", species().flag("dormant")));
    }
}
