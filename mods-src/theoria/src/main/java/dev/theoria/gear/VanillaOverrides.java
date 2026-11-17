package dev.theoria.gear;

import dev.theoria.Theoria;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * D6 "yeni id yok": vanilla leather/wood/stone/copper/iron/gold/diamond gear keeps its ids, its stats come from the table.
 * {@link ModifyDefaultComponentsEvent} runs after vanilla's own initializers, so we overwrite exactly four components per item:
 * durability, enchantability, attribute modifiers (armor/toughness/attack/speed) and — tools only — the mining speed inside the Tool rules.
 * Mining GATES are not here: they are the {@code incorrect_for_*_tool} tags (datapack, generated). A missing vanilla id fails loudly
 * (a Minecraft update that renames an item must not silently leave it un-tuned).
 */
final class VanillaOverrides {
    private VanillaOverrides() {}

    static void onModify(ModifyDefaultComponentsEvent event) {
        for (GearData.Material m : GearData.table().materials()) {
            if (!m.vanilla()) continue;
            if (m.armor() != null) {
                for (String piece : GearData.ARMOR_PIECES) {
                    GearData.Armor armor = m.armor();
                    event.modify(vanilla(m.itemId(piece)), (components, context, item) -> {
                        components.set(DataComponents.MAX_DAMAGE, armor.durability().get(piece));
                        components.set(DataComponents.ENCHANTABLE, new Enchantable(m.enchantability()));
                        components.set(DataComponents.ATTRIBUTE_MODIFIERS, GearStats.armorAttributes(armor, piece));
                    });
                }
            }
            if (m.tools() != null) {
                GearData.Tools tools = m.tools();
                for (String kind : GearData.TOOL_KINDS) {
                    GearData.Kind k = tools.kinds().get(kind);
                    event.modify(vanilla(m.itemId(kind)), (components, context, item) -> {
                        components.set(DataComponents.MAX_DAMAGE, tools.durability());
                        components.set(DataComponents.ENCHANTABLE, new Enchantable(m.enchantability()));
                        // The spear keeps vanilla's own attack-speed modifier (1/duration − 4); only its damage is retuned.
                        components.set(DataComponents.ATTRIBUTE_MODIFIERS, kind.equals("spear")
                                ? withDamage(components.get(DataComponents.ATTRIBUTE_MODIFIERS), k.damage()) : GearStats.weaponAttributes(k));
                        Tool tool = components.get(DataComponents.TOOL);
                        if (tool != null && !kind.equals("sword")) components.set(DataComponents.TOOL, GearStats.retune(tool, tools.miningSpeed()));
                    });
                }
            }
        }
    }

    private static Item vanilla(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null || BuiltInRegistries.ITEM.getKey(item).toString().equals("minecraft:air")) {
            throw new IllegalStateException("gear.json names vanilla item " + id + " but it does not exist in this Minecraft version");
        }
        return item;
    }

    private static ItemAttributeModifiers withDamage(ItemAttributeModifiers existing, double damage) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry e : existing.modifiers()) {
            if (e.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)) {
                var changed = new net.minecraft.world.entity.ai.attributes.AttributeModifier(e.modifier().id(), damage - GearStats.PLAYER_BASE_ATTACK, e.modifier().operation());
                entries.add(new ItemAttributeModifiers.Entry(e.attribute(), changed, e.slot(), e.display()));
            } else {
                entries.add(e);
            }
        }
        Theoria.LOGGER.debug("vanilla spear damage retuned to {}", damage);
        return new ItemAttributeModifiers(List.copyOf(entries));
    }
}
