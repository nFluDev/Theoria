package dev.theoria.catalyst;

import dev.theoria.core.TheoriaRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers the catalyst system's items, the forge block, its menu type and the creative tab. Everything comes from {@link CatalystData}: a new catalyst row = a new item. */
public final class CatalystItems {
    /** Catalyst items by id (Netherite is the vanilla ingot and has none), in table order. */
    public static final Map<String, DeferredItem<CatalystItem>> CATALYSTS = new LinkedHashMap<>();
    public static DeferredItem<Item> DUST;
    public static DeferredItem<Item> ESSENCE;
    public static DeferredItem<Item> CORE;
    public static DeferredBlock<CatalystForgeBlock> FORGE;
    public static DeferredItem<BlockItem> FORGE_ITEM;
    public static DeferredHolder<MenuType<?>, MenuType<CatalystMenu>> MENU;
    public static DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;

    private CatalystItems() {}

    static void init() {
        for (CatalystData.Def def : CatalystData.table().defs().values()) {
            if (def.kind() == CatalystData.Kind.NETHERITE) continue;
            CATALYSTS.put(def.id(), TheoriaRegistries.ITEMS.registerItem("catalyst/" + def.id(), p -> new CatalystItem(p, def),
                    () -> new Item.Properties().component(CatalystComponents.LEVEL.get(), 1)));
        }
        DUST = TheoriaRegistries.ITEMS.registerSimpleItem("catalyst_dust");
        ESSENCE = TheoriaRegistries.ITEMS.registerSimpleItem("catalyst_essence");
        CORE = TheoriaRegistries.ITEMS.registerSimpleItem("catalyst_core");
        FORGE = TheoriaRegistries.BLOCKS.registerBlock("catalyst_forge", CatalystForgeBlock::new, () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL));
        FORGE_ITEM = TheoriaRegistries.ITEMS.registerSimpleBlockItem(FORGE);
        MENU = TheoriaRegistries.MENUS.register("catalyst_forge", () -> new MenuType<>(CatalystMenu::new, FeatureFlags.VANILLA_SET));
        TAB = TheoriaRegistries.CREATIVE_TABS.register("catalysts", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.theoria.catalysts"))
                .icon(() -> new ItemStack(FORGE_ITEM.get()))
                .displayItems((params, output) -> tabContents().forEach(output::accept))
                .build());
    }

    /** A catalyst item stack at {@code level}; {@code grade} only matters for a graded catalyst. */
    public static ItemStack stack(CatalystData.Def def, int level, int grade) {
        DeferredItem<CatalystItem> item = CATALYSTS.get(def.id());
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item.get());
        stack.set(CatalystComponents.LEVEL.get(), level);
        if (def.graded()) stack.set(CatalystComponents.GRADE.get(), grade);
        return stack;
    }

    /** Creative tab: forge, raw materials, then every catalyst at every level (graded: the lowest and the highest tier). */
    private static List<ItemStack> tabContents() {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(FORGE_ITEM.get()));
        out.add(new ItemStack(DUST.get()));
        out.add(new ItemStack(ESSENCE.get()));
        out.add(new ItemStack(CORE.get()));
        int levels = CatalystData.table().levels();
        for (CatalystData.Def def : CatalystData.table().defs().values()) {
            if (def.kind() == CatalystData.Kind.NETHERITE) continue;
            for (int level = 1; level <= levels; level++) {
                if (def.graded()) {
                    out.add(stack(def, level, 0));
                    out.add(stack(def, level, CatalystData.table().tierlessIdx()));
                } else {
                    out.add(stack(def, level, 0));
                }
            }
        }
        return out;
    }
}
