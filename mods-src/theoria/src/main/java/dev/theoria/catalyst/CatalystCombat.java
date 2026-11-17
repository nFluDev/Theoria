package dev.theoria.catalyst;

import dev.theoria.balance.TierRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/**
 * Consumers of the custom combat attributes (ADR-006 Karar 5): critical chance/damage, life steal, thorns, extra food saturation. The attributes are ordinary
 * {@code ADD_VALUE} attributes — catalysts add to them through {@link CatalystAttributes}, and Phase 10's Status screen adds to the same ones. The soft caps
 * ({@code agg_caps}) apply HERE, to the sum, not to any single source. Server-side only: the server decides every outcome.
 */
public final class CatalystCombat {
    private static final float VANILLA_CRIT = 1.5F;

    private CatalystCombat() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CatalystCombat::onCritical);
        NeoForge.EVENT_BUS.addListener(CatalystCombat::onDamaged);
        NeoForge.EVENT_BUS.addListener(CatalystCombat::onEaten);
    }

    /** Attribute value capped at the table's soft cap; 0 for an entity that does not have the attribute. */
    static double capped(LivingEntity entity, Holder<Attribute> attribute, String capKey) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return 0.0;
        double value = instance.getValue();
        if (value <= 0.0) return 0.0;
        Double cap = CatalystData.table().aggCaps().get(capKey);
        return cap == null ? value : Math.min(value, cap);
    }

    /** Critical hits (ADR-006 Karar 5): a roll at {@code crit_chance} turns a normal full-strength hit into a critical one; every critical hit gets {@code + crit_damage}. */
    private static void onCritical(CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        var player = event.getEntity();
        double chance = capped(player, CatalystComponents.CRIT_CHANCE, "theoria:crit_chance");
        double bonus = capped(player, CatalystComponents.CRIT_DAMAGE, "theoria:crit_damage");
        if (chance <= 0.0 && bonus <= 0.0) return;
        if (!event.isCriticalHit() && chance > 0.0 && player.getAttackStrengthScale(0.5F) > 0.9F && player.getRandom().nextDouble() < chance) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(VANILLA_CRIT);
        }
        if (event.isCriticalHit()) event.setDamageMultiplier(event.getDamageMultiplier() + (float) bonus);
    }

    /** Life steal (attacker side) and thorns (victim side), after the damage has landed. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.THORNS)) return; // never answer thorns with life steal or thorns
        float dealt = event.getInflictedDamage();
        if (dealt <= 0.0F) return;
        if (source.getEntity() instanceof LivingEntity attacker) {
            double steal = capped(attacker, CatalystComponents.LIFE_STEAL, "theoria:life_steal");
            if (steal > 0.0) attacker.heal((float) (dealt * steal / healScale(attacker)));
            if (source.getDirectEntity() == attacker) {
                double thorns = capped(victim, CatalystComponents.THORNS, "theoria:thorns");
                if (thorns > 0.0 && attacker != victim) attacker.hurtServer(level, level.damageSources().thorns(victim), (float) thorns);
            }
        }
    }

    /**
     * Every heal is scaled by max health / base max health ({@code DamageHooks#onHeal}, docs/06 §7). Life steal is defined as "this share of the damage dealt",
     * so it divides that scale out and the player gets exactly the share — not the share times the tier's health multiplier.
     */
    private static double healScale(LivingEntity entity) {
        if (!TierRegistry.loaded()) return 1.0;
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getBaseValue() <= 0) return 1.0;
        return Math.max(1.0, entity.getMaxHealth() / maxHealth.getBaseValue());
    }

    private static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getItem().has(DataComponents.FOOD)) return;
        double extra = capped(player, CatalystComponents.FOOD_SATURATION, "theoria:food_saturation");
        if (extra <= 0.0) return;
        FoodData food = player.getFoodData();
        food.setSaturation((float) Math.min(food.getFoodLevel(), food.getSaturationLevel() + extra));
    }
}
