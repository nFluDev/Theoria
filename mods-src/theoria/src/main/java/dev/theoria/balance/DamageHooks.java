package dev.theoria.balance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The combat hooks (ADR-003, docs/06 §4 §6 §7). No mixins, vanilla's {@code CombatRules} is untouched:
 * <ol>
 * <li>mob damage multiplier — the attacker's tier scales the incoming amount ({@code setAmount});</li>
 * <li>{@code Reduction.ARMOR} modifier — replaces vanilla's armor reduction with {@link BalanceMath#armorFraction};</li>
 * <li>{@code Reduction.ENCHANTMENTS} modifier — vanilla's magic reduction × {@code ench_scale};</li>
 * <li>heal scaling — every heal × max health / base max health.</li>
 * </ol>
 * Vanilla still runs its own armor step (it wears the armor out, {@code hurtArmor}); we only overwrite the result.
 * Until the balance data has loaded every hook leaves vanilla behaviour alone.
 */
public final class DamageHooks {
    private DamageHooks() {}

    public static void init() {
        // HIGHEST: our modifiers run first in insertion order; another mod's modifier then sees our result (ADR-003 Karar 1).
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DamageHooks::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DamageHooks::onHeal);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        TierRegistry.Snapshot data = TierRegistry.snapshot();
        if (data == null) return;
        CombatParams combat = data.combat();
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // Order matters: the amount is final before the ARMOR modifier reads it as H (it runs later, inside actuallyHurt).
        if (source.getEntity() instanceof LivingEntity attacker) {
            Tier tier = MobScaling.tierDataOf(attacker);
            if (tier != null) {
                double mult = source.is(DamageTypeTags.BYPASSES_ARMOR) ? tier.mob().bypassMult() : tier.mob().dmgMult();
                if (mult != 1.0) event.setAmount((float) (event.getAmount() * mult));
            }
        }
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, vanilla) -> armorReduction(combat, victim, container, vanilla));
        event.addReductionModifier(DamageContainer.Reduction.ENCHANTMENTS, (container, vanilla) -> (float) (vanilla * combat.enchScale()));
    }

    /** {@code vanilla} is what vanilla would have removed; ours replaces it. {@code bypasses_armor} sources stay at vanilla's 0. */
    private static float armorReduction(CombatParams combat, LivingEntity victim, DamageContainer container, float vanilla) {
        DamageSource source = container.getSource();
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return vanilla;
        float hit = container.getNewDamage(); // H: after shield/helmet/freeze and i-frames, before armor (ADR-003)
        double fraction = BalanceMath.armorFraction(combat, victim.getArmorValue(), victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS), hit);
        // Same step as vanilla's CombatRules.getDamageAfterAbsorb: the weapon may bend the fraction (Breach).
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && victim.level() instanceof ServerLevel level) {
            fraction = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(level, weapon, victim, source, (float) fraction), 0.0F, 1.0F);
        }
        return (float) (hit * fraction);
    }

    /** Healing is a share of max health, like vanilla at 20 (docs/06 §7); modifiers and tiers move max health, the base stays. */
    private static void onHeal(LivingHealEvent event) {
        if (!TierRegistry.loaded()) return;
        LivingEntity entity = event.getEntity();
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getBaseValue() <= 0) return;
        event.setAmount((float) (event.getAmount() * entity.getMaxHealth() / maxHealth.getBaseValue()));
    }
}
