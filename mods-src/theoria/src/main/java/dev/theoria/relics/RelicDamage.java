package dev.theoria.relics;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The two reactive relic kinds. {@code immune}: a worn relic removes a share of the damage of one damage-type tag (fire, explosion, freezing, lightning); several relics multiply their
 * remainders. {@code survive}: once per cooldown a lethal hit is turned into nothing and the player is brought back to a share of their health (the Phoenix Feather; the cooldown is the
 * item's, so the inventory shows it). Both are pure functions first ({@link #immuneFactor}, {@link #survive}) and thin event listeners second, so the tests call the real code.
 */
public final class RelicDamage {
    private RelicDamage() {}

    public static void init() {
        // After Theoria's own HIGHEST modifiers (DamageHooks): our share is taken from the already tier-scaled hit. Survive reads the FINAL damage (after armor, absorption, enchantments).
        NeoForge.EVENT_BUS.addListener(RelicDamage::onIncoming);
        NeoForge.EVENT_BUS.addListener(RelicDamage::onDamage);
    }

    static TagKey<DamageType> tag(RelicData.Def def) {
        return TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse(def.str("damage")));
    }

    static boolean isFire(RelicData.Def def) {
        return def.str("damage").equals("minecraft:is_fire");
    }

    /** Share (0..1) of {@code source}'s damage the player's worn relics remove. */
    public static double immuneFactor(ServerPlayer player, DamageSource source) {
        double keep = 1.0;
        for (String id : RelicEffects.wornCached(player)) {
            RelicData.Def def = RelicData.get(id);
            if (def != null && def.kind() == RelicData.Kind.IMMUNE && source.is(tag(def))) keep *= 1.0 - def.num("factor");
        }
        return 1.0 - keep;
    }

    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double factor = immuneFactor(player, event.getSource());
        if (factor >= 0.999) event.setCanceled(true);
        else if (factor > 0.0) event.setAmount((float) (event.getAmount() * (1.0 - factor)));
    }

    /**
     * Phoenix Feather: {@code damage} is what is about to be dealt. If it would kill and a worn survive relic is off cooldown, the hit becomes 0, the player is restored to the relic's share of
     * max health, the relic's cooldown starts and the totem animation plays. Returns the damage to apply.
     */
    public static float survive(ServerPlayer player, float damage) {
        if (damage < player.getHealth() + player.getAbsorptionAmount()) return damage;
        for (String id : RelicEffects.wornCached(player)) {
            RelicData.Def def = RelicData.get(id);
            if (def == null || def.kind() != RelicData.Kind.SURVIVE) continue;
            ItemStack stack = RelicEffects.wornStack(player, id);
            if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) continue;
            player.getCooldowns().addCooldown(stack, (int) def.num("cooldown"));
            player.setHealth(Math.max(player.getHealth(), (float) (def.num("heal") * player.getMaxHealth())));
            player.level().broadcastEntityEvent(player, (byte) 35); // the totem animation
            player.sendSystemMessage(Component.translatable("theoria.relic.survived", stack.getHoverName()), true);
            return 0.0F;
        }
        return damage;
    }

    private static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float saved = survive(player, event.getNewDamage());
        if (saved != event.getNewDamage()) event.setNewDamage(saved);
    }
}
