package dev.theoria.gear;

import dev.theoria.Theoria;
import dev.theoria.core.TheoriaRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registers every block and item of the gear table (new materials only — vanilla materials are re-tuned in {@link VanillaOverrides}).
 * Runs from the mod constructor, before the registry events; everything comes from {@link GearData} (generator/gear.py), nothing is hand-listed.
 */
public final class GearRegistry {
    /** Registered items by short name (e.g. "cobalt_sword", "tin_ingot", "tin_ore"), in registration order. */
    public static final Map<String, DeferredItem<? extends Item>> ITEMS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<? extends Block>> BLOCKS = new LinkedHashMap<>();
    public static DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;

    private GearRegistry() {}

    public static void init() {
        GearData.Table table = GearData.table();
        for (GearData.Material m : table.materials()) {
            if (m.vanilla()) continue;
            if (m.armor() != null) registerArmor(m);
            if (m.tools() != null) registerTools(m);
        }
        for (String name : table.items()) ITEMS.put(name, TheoriaRegistries.ITEMS.registerSimpleItem(name));
        for (GearData.Block b : table.blocks()) registerBlock(b);
        TAB = TheoriaRegistries.CREATIVE_TABS.register("gear", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.theoria.gear"))
                .icon(() -> new ItemStack(ITEMS.containsKey("platinum_ingot") ? ITEMS.get("platinum_ingot").get() : Items.IRON_INGOT))
                .displayItems((params, output) -> ITEMS.values().forEach(item -> output.accept(item.get())))
                .build());
    }

    // ------------------------------------------------------------------------------------------------ armor
    private static void registerArmor(GearData.Material m) {
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Theoria.id(m.id()));
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Theoria.id("repairs_" + m.id()));
        // Durability multiplier and defence map are placeholders: both are overwritten below with the table's exact per-piece numbers.
        ArmorMaterial material = new ArmorMaterial(1, Map.of(), m.enchantability(), equipSound(m.equipSound()), 0.0F, 0.0F, repair, asset);
        for (String piece : GearData.ARMOR_PIECES) {
            ITEMS.put(m.id() + "_" + piece, TheoriaRegistries.ITEMS.registerItem(m.id() + "_" + piece, Item::new, () -> new Item.Properties()
                    .humanoidArmor(material, GearStats.armorType(piece))
                    .durability(m.armor().durability().get(piece))
                    .attributes(GearStats.armorAttributes(m.armor(), piece))));
        }
    }

    private static Holder<SoundEvent> equipSound(String name) {
        return switch (name) {
            case "leather" -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case "chain" -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case "copper" -> SoundEvents.ARMOR_EQUIP_COPPER;
            case "iron" -> SoundEvents.ARMOR_EQUIP_IRON;
            case "gold" -> SoundEvents.ARMOR_EQUIP_GOLD;
            case "diamond" -> SoundEvents.ARMOR_EQUIP_DIAMOND;
            case "netherite" -> SoundEvents.ARMOR_EQUIP_NETHERITE;
            default -> throw new IllegalArgumentException("materials.csv: unknown equip_sound '" + name + "'");
        };
    }

    // ------------------------------------------------------------------------------------------------ tools and weapons
    private static void registerTools(GearData.Material m) {
        GearData.Tools t = m.tools();
        TagKey<Block> incorrect = TagKey.create(Registries.BLOCK, Theoria.id("incorrect_for_" + m.id() + "_tool"));
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Theoria.id("repairs_" + m.id()));
        ToolMaterial plain = new ToolMaterial(incorrect, t.durability(), (float) t.miningSpeed(), 0.0F, m.enchantability(), repair);
        for (String kind : GearData.TOOL_KINDS) {
            GearData.Kind k = t.kinds().get(kind);
            String name = m.id() + "_" + kind;
            ITEMS.put(name, TheoriaRegistries.ITEMS.registerItem(name, Item::new, () -> switch (kind) {
                case "sword" -> new Item.Properties().sword(plain, (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), (float) (k.speed() - GearStats.PLAYER_BASE_SPEED));
                case "axe" -> new Item.Properties().axe(plain, (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), (float) (k.speed() - GearStats.PLAYER_BASE_SPEED));
                case "pickaxe" -> new Item.Properties().pickaxe(plain, (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), (float) (k.speed() - GearStats.PLAYER_BASE_SPEED));
                case "shovel" -> new Item.Properties().shovel(plain, (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), (float) (k.speed() - GearStats.PLAYER_BASE_SPEED));
                case "hoe" -> new Item.Properties().hoe(plain, (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), (float) (k.speed() - GearStats.PLAYER_BASE_SPEED));
                case "spear" -> spear(m, t, k, incorrect, repair);
                default -> throw new IllegalArgumentException(kind);
            }));
        }
    }

    /** Vanilla's spear() derives the jab damage from the material's attackDamageBonus and the attack speed from the attack duration (1/duration − 4). */
    private static Item.Properties spear(GearData.Material m, GearData.Tools t, GearData.Kind k, TagKey<Block> incorrect, TagKey<Item> repair) {
        ToolMaterial material = new ToolMaterial(incorrect, t.durability(), (float) t.miningSpeed(), (float) (k.damage() - GearStats.PLAYER_BASE_ATTACK), m.enchantability(), repair);
        double[] s = t.spear();
        return new Item.Properties().spear(material, (float) s[0], (float) s[1], (float) s[2], (float) s[3], (float) s[4], (float) s[5], (float) s[6], (float) s[7], (float) s[8]);
    }

    // ------------------------------------------------------------------------------------------------ blocks
    private static void registerBlock(GearData.Block b) {
        boolean deepslate = b.id().startsWith("deepslate_");
        DeferredBlock<? extends Block> block;
        if (b.kind().equals("ore")) {
            var xp = b.xpMax() == 0 ? ConstantInt.of(0) : UniformInt.of(b.xpMin(), b.xpMax());
            block = TheoriaRegistries.BLOCKS.registerBlock(b.id(), p -> new DropExperienceBlock(xp, p), () -> BlockBehaviour.Properties.of()
                    .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE).requiresCorrectToolForDrops()
                    .strength((float) b.hardness(), (float) b.resistance()).sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE));
        } else {
            block = TheoriaRegistries.BLOCKS.registerBlock(b.id(), Block::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength((float) b.hardness(), (float) b.resistance()).sound(SoundType.IRON));
        }
        BLOCKS.put(b.id(), block);
        ITEMS.put(b.id(), TheoriaRegistries.ITEMS.registerSimpleBlockItem(block));
    }

    /** Every item of this registry, for creative tab, checks and tests. */
    public static List<Item> allItems() {
        List<Item> out = new ArrayList<>();
        ITEMS.values().forEach(i -> out.add(i.get()));
        return out;
    }
}
