package dev.theoria.gear;

import dev.theoria.Theoria;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Turns the gear table into vanilla components. One place for both registered (new) items and the vanilla overrides,
 * so a tooltip can never disagree with the table. Conventions (docs/06 §2):
 * armor = per-piece points; set toughness is spread as a quarter per piece; weapon damage in the table is the displayed total
 * (the player's base 1 is added by the game) so the modifier is {@code damage − 1}; attack speed in the table is attacks per second
 * (the game's base is 4) so the modifier is {@code speed − 4}.
 */
final class GearStats {
    static final double PLAYER_BASE_ATTACK = 1.0;
    static final double PLAYER_BASE_SPEED = 4.0;

    private GearStats() {}

    static ArmorType armorType(String piece) {
        return switch (piece) {
            case "helmet" -> ArmorType.HELMET;
            case "chestplate" -> ArmorType.CHESTPLATE;
            case "leggings" -> ArmorType.LEGGINGS;
            case "boots" -> ArmorType.BOOTS;
            default -> throw new IllegalArgumentException("Unknown armor piece " + piece);
        };
    }

    static ItemAttributeModifiers armorAttributes(GearData.Armor armor, String piece) {
        ArmorType type = armorType(piece);
        EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
        Identifier id = Identifier.withDefaultNamespace("armor." + type.getName());
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        b.add(Attributes.ARMOR, new AttributeModifier(id, armor.points().get(piece), AttributeModifier.Operation.ADD_VALUE), slot);
        b.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, armor.toughness() / GearData.ARMOR_PIECES.size(), AttributeModifier.Operation.ADD_VALUE), slot);
        if (armor.knockback() > 0) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, armor.knockback() / GearData.ARMOR_PIECES.size(), AttributeModifier.Operation.ADD_VALUE), slot);
        }
        if (armor.speed() > 0) { // innate trait (docs/05 §1: leather +2 %, mithril +4 % for the whole set)
            b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(Theoria.id("armor_speed." + type.getName()),
                    armor.speed() / GearData.ARMOR_PIECES.size(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
        }
        return b.build();
    }

    static ItemAttributeModifiers weaponAttributes(GearData.Kind kind) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, kind.damage() - PLAYER_BASE_ATTACK,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, kind.speed() - PLAYER_BASE_SPEED,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Same rules as the vanilla tool, but every rule that mines-and-drops uses the table's mining speed (denies-drops / sword rules untouched). */
    static Tool retune(Tool tool, double miningSpeed) {
        List<Tool.Rule> rules = new ArrayList<>();
        for (Tool.Rule rule : tool.rules()) {
            boolean mines = rule.speed().isPresent() && rule.correctForDrops().orElse(false);
            rules.add(mines ? new Tool.Rule(rule.blocks(), java.util.Optional.of((float) miningSpeed), rule.correctForDrops()) : rule);
        }
        return new Tool(List.copyOf(rules), tool.defaultMiningSpeed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative());
    }
}
