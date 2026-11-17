package dev.theoria.gear;

import dev.theoria.balance.Tier;
import dev.theoria.balance.TierRegistry;
import dev.theoria.core.check.Checks;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /theoria test gear}: safe in a live world (reads default components and block states, creates nothing).
 * The same numbers the balance datapack serves ({@link TierRegistry}) must come out of the items a player holds — tooltip = table.
 */
final class GearChecks {
    private static final double EPS = 1.0e-4;

    private GearChecks() {}

    static void init() {
        Checks.register("gear", "table", s -> {
            GearData.Table t = GearData.table();
            long armor = t.materials().stream().filter(m -> m.armor() != null).count();
            long tools = t.materials().stream().filter(m -> m.tools() != null).count();
            Checks.require(armor == 14, "expected 14 armor tiers, table has " + armor);
            Checks.require(tools == 11, "expected 11 tool tiers, table has " + tools);
            for (String name : GearRegistry.ITEMS.keySet()) Checks.require(GearRegistry.ITEMS.get(name).isBound(), "item not registered: " + name);
            return armor + " armor tiers, " + tools + " tool tiers, " + GearRegistry.ITEMS.size() + " new items, " + GearRegistry.BLOCKS.size() + " new blocks";
        });
        Checks.register("gear", "armor_matches_balance", s -> armorMatchesBalance());
        Checks.register("gear", "tools_match_balance", s -> toolsMatchBalance());
        Checks.register("gear", "mining_gates", s -> miningGates());
        Checks.register("gear", "ore_hardness", s -> oreHardness());
        Checks.register("gear", "netherite_unobtainable", s -> netheriteGone(s.getServer()));
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        Checks.require(item != null, "item missing: " + id);
        return item;
    }

    static double sum(Item item, net.minecraft.core.Holder<Attribute> attribute) {
        ItemAttributeModifiers mods = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        Checks.require(mods != null, BuiltInRegistries.ITEM.getKey(item) + " has no attribute modifiers");
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) if (e.attribute().equals(attribute)) total += e.modifier().amount();
        return total;
    }

    static Tier tier(String id) {
        for (Tier t : TierRegistry.snapshot().tiers()) if (t.id().equals(id)) return t;
        throw new IllegalStateException("no balance tier named " + id);
    }

    // ------------------------------------------------------------------------------------------------ armor
    private static String armorMatchesBalance() {
        int n = 0;
        for (GearData.Material m : GearData.table().materials()) {
            if (m.armor() == null) continue;
            Tier t = tier(m.id());
            int[] expected = {t.armor().helmet(), t.armor().chestplate(), t.armor().leggings(), t.armor().boots()};
            double armor = 0, toughness = 0;
            for (int i = 0; i < 4; i++) {
                Item piece = item(m.itemId(GearData.ARMOR_PIECES.get(i)));
                double points = sum(piece, Attributes.ARMOR);
                Checks.require(points == expected[i], m.id() + " " + GearData.ARMOR_PIECES.get(i) + " armor " + points + " ≠ " + expected[i]);
                armor += points;
                toughness += sum(piece, Attributes.ARMOR_TOUGHNESS);
            }
            Checks.require(armor == t.armor().total(), m.id() + " set armor " + armor + " ≠ " + t.armor().total());
            Checks.require(Math.abs(toughness - t.toughness()) < EPS, m.id() + " set toughness " + toughness + " ≠ " + t.toughness());
            Integer chestDurability = item(m.itemId("chestplate")).components().get(DataComponents.MAX_DAMAGE);
            Checks.require(chestDurability != null && chestDurability == t.armorDurability(), m.id() + " chestplate durability " + chestDurability + " ≠ " + t.armorDurability());
            n++;
        }
        return n + " armor sets: piece points, set total, set toughness and chestplate durability equal the balance datapack";
    }

    // ------------------------------------------------------------------------------------------------ tools
    private static String toolsMatchBalance() {
        int n = 0, weapons = 0;
        for (GearData.Material m : GearData.table().materials()) {
            if (m.tools() == null) continue;
            boolean hasTier = m.armor() != null; // wood/stone sit outside the armor ladder; their numbers are covered by the GameTest against tools.csv
            Tier t = hasTier ? tier(m.id()) : null;
            for (String kind : GearData.TOOL_KINDS) {
                Item item = item(m.itemId(kind));
                GearData.Kind k = m.tools().kinds().get(kind);
                double damage = GearStats.PLAYER_BASE_ATTACK + sum(item, Attributes.ATTACK_DAMAGE);
                Checks.require(Math.abs(damage - k.damage()) < EPS, m.id() + " " + kind + " damage " + damage + " ≠ " + k.damage());
                Integer durability = item.components().get(DataComponents.MAX_DAMAGE);
                Checks.require(durability != null && durability == m.tools().durability(), m.id() + " " + kind + " durability " + durability);
                if (t != null) {
                    if (kind.equals("sword")) Checks.require(Math.abs(damage - t.swordDamage()) < EPS, m.id() + " sword " + damage + " ≠ balance " + t.swordDamage());
                    Checks.require(durability == t.toolDurability(), m.id() + " " + kind + " durability ≠ balance " + t.toolDurability());
                }
                weapons++;
            }
            n++;
        }
        return n + " tool tiers / " + weapons + " items: damage (displayed total), durability and balance sword/durability agree";
    }

    // ------------------------------------------------------------------------------------------------ gates
    /** Every gated block × every pickaxe: drops iff tool idx ≥ gate idx. Needs the datapack tags loaded (a running world). */
    private static String miningGates() {
        int pairs = 0;
        for (GearData.Material m : GearData.table().materials()) {
            if (m.tools() == null) continue;
            Tool tool = item(m.itemId("pickaxe")).components().get(DataComponents.TOOL);
            Checks.require(tool != null, m.id() + " pickaxe has no tool component");
            for (GearData.Gate gate : GearData.table().gates()) {
                for (GearData.GateBlock gb : gate.blocks()) {
                    BlockState state = BuiltInRegistries.BLOCK.getValue(Identifier.parse(gb.id())).defaultBlockState();
                    boolean expected = m.tools().idx() >= gate.toolIdx();
                    Checks.require(tool.isCorrectForDrops(state) == expected,
                            String.format(Locale.ROOT, "%s pickaxe (idx %d) on %s (needs idx %d): drops=%s, expected %s", m.id(), m.tools().idx(), gb.id(), gate.toolIdx(), !expected, expected));
                    pairs++;
                }
            }
        }
        return pairs + " pickaxe × gated block pairs: drops exactly when the tool tier ≥ the block's required tier";
    }

    private static String oreHardness() {
        int n = 0;
        for (GearData.Gate gate : GearData.table().gates()) {
            for (GearData.GateBlock gb : gate.blocks()) {
                if (gb.hardness() == null) continue;
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(gb.id()));
                float actual = block.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                Checks.require(Math.abs(actual - gb.hardness()) < EPS, gb.id() + " hardness " + actual + " ≠ " + gb.hardness());
                n++;
            }
        }
        return n + " gated blocks have the table's hardness";
    }

    // ------------------------------------------------------------------------------------------------ netherite (D6)
    private static String netheriteGone(net.minecraft.server.MinecraftServer server) {
        var manager = server.getRecipeManager();
        int checked = 0;
        for (String id : GearData.table().removed()) {
            String name = id.substring("minecraft:".length());
            if (name.equals("netherite_upgrade_smithing_template")) continue; // duplication recipe is NeoForge's copy and inert: no source of the template (loot filter)
            String recipe = name + "_smithing";
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.withDefaultNamespace(recipe));
            Checks.require(manager.byKey(key).isEmpty(), "netherite recipe still loaded: minecraft:" + recipe);
            checked++;
        }
        return "none of the " + checked + " netherite smithing upgrade recipes is loaded (netherite ingot recipes stay: universal catalyst)";
    }
}
