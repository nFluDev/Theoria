package dev.theoria.catalyst;

import dev.theoria.Theoria;
import dev.theoria.catalyst.CatalystHosts.Host;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Onarım (Restoration), ADR-006 Karar 9: once a second, every held/worn item with the virtual enchantment {@code theoria:restoration} (a catalyst, or Orikalkum's
 * innate trait) mends {@code value × max durability}. The fraction comes from the table (V: the item mends fully in about 8 minutes); fractional points are rolled
 * so a small value still adds up over time. Server-side, players only.
 */
public final class Restoration {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, Theoria.id("restoration"));
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int INTERVAL = 20;

    private Restoration() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Restoration::onTick);
    }

    /** Fraction of max durability restored per second by this stack (0 = none). */
    public static double rate(ItemStack stack, Holder<Enchantment> restoration) {
        int p = stack.getEnchantmentLevel(restoration);
        if (p <= 0) return 0.0;
        Host host = CatalystHosts.of(stack);
        if (host == null) return 0.0;
        CatalystData.Def def = CatalystData.table().defs().get("restoration");
        double best = 0.0;
        for (String target : host.matching(def)) {
            double value = CatalystData.table().value("restoration", target, CatalystRules.decodeTier(p), CatalystRules.decodeLevel(p));
            if (!Double.isNaN(value)) best = Math.max(best, value);
        }
        return best;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0) return;
        Holder<Enchantment> holder = player.level().registryAccess().lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(KEY)).orElse(null);
        if (holder == null) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamaged()) continue;
            double heal = rate(stack, holder) * stack.getMaxDamage();
            if (heal <= 0.0) continue;
            int points = (int) heal + (player.getRandom().nextDouble() < heal - Math.floor(heal) ? 1 : 0);
            if (points > 0) stack.setDamageValue(Math.max(0, stack.getDamageValue() - points));
        }
    }
}
