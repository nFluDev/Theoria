package dev.theoria.relics;

import dev.theoria.core.TheoriaRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers one item per relic row and the "Theoria: Relics" creative tab. Everything comes from {@link RelicData}: a new row = a new item. */
public final class RelicItems {
    /** Relic items by relic id, in table order. */
    public static final Map<String, DeferredItem<RelicItem>> RELICS = new LinkedHashMap<>();
    public static DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;

    private RelicItems() {}

    static void init() {
        for (RelicData.Def def : RelicData.all()) {
            RELICS.put(def.id(), TheoriaRegistries.ITEMS.registerItem("relic/" + def.id(), p -> new RelicItem(p, def), () -> new Item.Properties().stacksTo(1).rarity(def.vanillaRarity())));
        }
        TAB = TheoriaRegistries.CREATIVE_TABS.register("relics", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.theoria.relics"))
                .icon(() -> stack(RelicData.all().get(0).id())) // the first row of data/relics.csv
                .displayItems((params, output) -> tabContents().forEach(output::accept))
                .build());
    }

    public static ItemStack stack(String id) {
        DeferredItem<RelicItem> item = RELICS.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    /** The tab lists legendary first, then rare, uncommon, common — inside a rarity the table order. */
    private static List<ItemStack> tabContents() {
        List<RelicData.Def> defs = new ArrayList<>(RelicData.all());
        defs.sort(Comparator.comparingInt((RelicData.Def d) -> -rarityOrder(d.rarity())).thenComparingInt(RelicData.Def::index));
        List<ItemStack> out = new ArrayList<>();
        for (RelicData.Def def : defs) out.add(stack(def.id()));
        return out;
    }

    private static int rarityOrder(String rarity) {
        return switch (rarity) {
            case "legendary" -> 3;
            case "rare" -> 2;
            case "uncommon" -> 1;
            default -> 0;
        };
    }
}
