package dev.theoria.mobs;

import dev.theoria.Theoria;
import dev.theoria.balance.MobScaling;
import dev.theoria.balance.TierRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * The mob rules that apply to vanilla and Theoria mobs alike (docs/06 §12, ADR-007):
 * <ol>
 * <li><b>zone tier</b> — a vanilla monster is born at the tier of its zone ({@link MobZones});</li>
 * <li><b>XP</b> — the kill XP is multiplied by {@code xp_growth^tier} (before Bilgelik, which multiplies the result);</li>
 * <li><b>weapon normalization</b> (Q35) — a weapon in a mob's hand deals its <em>vanilla</em> damage; the tier multiplier is what grows it;</li>
 * <li><b>on-hit effects</b> — species parameters {@code hit_effect} / {@code ignite}, for melee, arrows and slams alike.</li>
 * </ol>
 */
public final class MobHooks {
    static final Identifier WEAPON_MODIFIER = Theoria.id("mob_weapon_normalize");

    private MobHooks() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(MobHooks::onFinalize);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, MobHooks::onExperience);
        NeoForge.EVENT_BUS.addListener(MobHooks::onEquipment);
        NeoForge.EVENT_BUS.addListener(MobHooks::onJoin);
        NeoForge.EVENT_BUS.addListener(MobHooks::onDamaged);
    }

    // ------------------------------------------------------------------------------------------------ zone tier
    private static void onFinalize(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (mob instanceof TheoriaMob || event.getSpawnType() == EntitySpawnReason.COMMAND || !TierRegistry.loaded()) return;
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;
        int zone = MobZones.tierAt(event.getLevel(), net.minecraft.core.BlockPos.containing(event.getX(), event.getY(), event.getZ()));
        if (zone > 0 && !MobScaling.isTiered(mob)) MobScaling.assign(mob, zone);
    }

    // ------------------------------------------------------------------------------------------------ XP
    private static void onExperience(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        if (!MobScaling.isTiered(entity)) return;
        event.setDroppedExperience(scaledExperience(event.getDroppedExperience(), MobScaling.tierOf(entity)));
    }

    public static int scaledExperience(int base, int tier) {
        double scaled = base * Math.pow(MobData.balance().xpGrowth(), tier);
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.round(scaled));
    }

    // ------------------------------------------------------------------------------------------------ weapon normalization (Q35)
    private static void onEquipment(LivingEquipmentChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.MAINHAND && event.getEntity() instanceof Mob mob) normalizeWeapon(mob);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) normalizeWeapon(mob);
    }

    /** Brings the mob's attack damage back to "base + vanilla weapon": a transient modifier of (vanilla bonus − actual item bonus). */
    public static void normalizeWeapon(Mob mob) {
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) return;
        damage.removeModifier(WEAPON_MODIFIER);
        ItemStack stack = mob.getMainHandItem();
        if (stack.isEmpty()) return;
        Integer vanillaTotal = MobData.balance().weapons().get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (vanillaTotal == null) return;
        double[] itemBonus = {0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) itemBonus[0] += modifier.amount();
        });
        double delta = itemBonus[0] - (vanillaTotal - 1);   // the player's base 1 is part of the "total" and not of the item
        if (Math.abs(delta) > 1.0E-6) damage.addTransientModifier(new AttributeModifier(WEAPON_MODIFIER, -delta, AttributeModifier.Operation.ADD_VALUE));
    }

    // ------------------------------------------------------------------------------------------------ on-hit effects
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof TheoriaMob attacker) || event.getEntity() == attacker || event.getInflictedDamage() <= 0) return;
        Species sp = attacker.species();
        applyEffect(event.getEntity(), sp.text("hit_effect"));
        int ignite = sp.integer("ignite", 0);
        if (ignite > 0) event.getEntity().igniteForSeconds(ignite);
    }

    /** {@code namespace:effect:ticks:amplifier} → applied; empty text = nothing. */
    public static void applyEffect(LivingEntity target, String spec) {
        if (spec.isEmpty()) return;
        String[] p = spec.split(":");
        Identifier id = Identifier.fromNamespaceAndPath(p[0], p[1]);
        BuiltInRegistries.MOB_EFFECT.get(id).ifPresent(effect -> target.addEffect(new MobEffectInstance(effect, Integer.parseInt(p[2]), Integer.parseInt(p[3]))));
    }
}
