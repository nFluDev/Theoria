package dev.theoria.catalyst;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Bilgelik (Erudition), ADR-006 Karar 8 / ADR-004 Karar 7: {@code theoria:erudition} is a real enchantment (max level 5, table/anvil/loot) that a Bilgelik catalyst also
 * grants virtually. The effect is Java, not datapack JSON: vanilla applies each worn piece's {@code mob_experience} effect separately and MULTIPLIES them, which cannot
 * express "sum over pieces, total capped". Here: the percentages of mainhand + four armor pieces are SUMMED, capped at {@code erudition.total_cap}, and applied to
 * exactly two XP sources — mobs killed by the player and blocks the player broke. Furnaces, fishing, trading, breeding, XP bottles and the grindstone are NOT touched:
 * grindstone/bottle refunds would loop "spend → refund × multiplier".
 */
public final class Erudition {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, dev.theoria.Theoria.id("erudition"));
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Erudition() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Erudition::onMobXp);
        NeoForge.EVENT_BUS.addListener(Erudition::onBlockXp);
    }

    /** XP bonus of one level, in percent: the table's I-V values, then +step per level (VI = V + step). */
    public static int percent(int level) {
        CatalystData.Table t = CatalystData.table();
        if (level <= 0) return 0;
        return level <= t.eruditionPct().length ? t.eruditionPct()[level - 1] : t.eruditionPct()[t.eruditionPct().length - 1] + t.eruditionStep() * (level - t.eruditionPct().length);
    }

    /** Summed and capped bonus (percent) of a player's gear. */
    public static int totalPercent(Player player) {
        var lookup = player.level().registryAccess().lookup(Registries.ENCHANTMENT);
        if (lookup.isEmpty()) return 0;
        Holder<Enchantment> holder = lookup.get().get(KEY).orElse(null);
        if (holder == null) return 0;
        int sum = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) sum += percent(stack.getEnchantmentLevel(holder));
        }
        return Math.min(sum, CatalystData.table().eruditionTotalCap());
    }

    /** XP after the bonus: {@code amount × (100 + percent) / 100}, rounded to the nearest. */
    public static int scaled(int amount, int percent) {
        return percent <= 0 ? amount : (int) Math.round(amount * (100.0 + percent) / 100.0);
    }

    private static void onMobXp(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null || killer.level().isClientSide()) return;
        int percent = totalPercent(killer);
        if (percent > 0) event.setDroppedExperience(scaled(event.getDroppedExperience(), percent));
    }

    private static void onBlockXp(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player) || event.getDroppedExperience() <= 0) return;
        int percent = totalPercent(player);
        if (percent > 0) event.setDroppedExperience(scaled(event.getDroppedExperience(), percent));
    }
}
