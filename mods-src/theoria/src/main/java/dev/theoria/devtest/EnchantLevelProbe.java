package dev.theoria.devtest;

import dev.theoria.Theoria;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/**
 * ADR-004/ADR-006 proof listener. It is NOT the production code ({@code dev.theoria.catalyst.EffectiveLevels} is): it lets the
 * {@code enchant_effective_level} GameTest prove the vanilla/NeoForge event chain with a stack marker that owns nothing of ours,
 * so a red result points at the platform and not at our component. Markers live in {@code custom_data}:
 * {@code probe_bonus} (+N on every real enchantment, ADR-004 Karar 3-4) and {@code probe_virtual_<path>} (a level for an enchantment the stack does not carry).
 */
final class EnchantLevelProbe {
    static final String BONUS = "theoria_probe_bonus";
    static final String VIRTUAL_PREFIX = "theoria_probe_virtual_";

    private EnchantLevelProbe() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(EnchantLevelProbe::onLevel);
    }

    private static void onLevel(GetEnchantmentLevelEvent event) {
        CustomData data = event.getStack().get(DataComponents.CUSTOM_DATA);
        if (data == null) return; // the cheap exit the production listener must also have (ADR-004 "olay çok sık")
        CompoundTag tag = data.copyTag();
        int bonus = tag.getIntOr(BONUS, 0);
        var target = event.getTargetEnchant();
        if (target != null) {
            apply(event, target, tag, bonus);
        } else {
            for (Holder<Enchantment> held : new ArrayList<>(event.getEnchantments().keySet())) apply(event, held, tag, bonus);
            for (String key : tag.keySet()) { // virtual enchantments are not in the map yet: look them up by id
                if (!key.startsWith(VIRTUAL_PREFIX)) continue;
                String path = key.substring(VIRTUAL_PREFIX.length());
                event.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                        net.minecraft.resources.Identifier.withDefaultNamespace(path))).ifPresent(h -> apply(event, h, tag, bonus));
            }
        }
    }

    private static void apply(GetEnchantmentLevelEvent event, Holder<Enchantment> ench, CompoundTag tag, int bonus) {
        int current = event.getEnchantments().getLevel(ench);
        int virtual = ench.unwrapKey().map(k -> tag.getIntOr(VIRTUAL_PREFIX + k.identifier().getPath(), 0)).orElse(0);
        int level = current + (current >= 1 ? bonus : 0) + virtual; // bonus only on a real (vanilla >= 1) enchantment
        if (level != current) event.getEnchantments().set(ench, Math.min(255, level)); // set() clamps at 255 anyway (ADR-004 [log])
        if (Theoria.LOGGER.isTraceEnabled()) Theoria.LOGGER.trace("probe {} {} -> {}", ench.getRegisteredName(), current, level);
    }
}
