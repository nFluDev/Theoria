package dev.theoria.gear;

import dev.theoria.Theoria;
import dev.theoria.core.TheoriaRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Phase 6 entry point: materials, armor, tools and weapons, ores, alloys, vanilla re-tuning, netherite gear removal (D6).
 * Everything is driven by {@code theoria/gear.json} (generator/gear.py); Java holds only the mechanics.
 * Called from the mod constructor, before any registry event.
 */
public final class GearModule {
    private GearModule() {}

    public static void init(IEventBus modBus) {
        TheoriaRegistries.LOOT_MODIFIER_TYPES.register("remove_items", () -> RemoveItemsLootModifier.CODEC);
        GearRegistry.init();
        modBus.addListener(VanillaOverrides::onModify);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(OreHardness::apply));
        modBus.addListener(GearModule::hideNetherite);
        GearChecks.init();
    }

    /** Netherite gear stays registered (items cannot be unregistered) but unobtainable: no recipe, no loot, not offered in creative either. */
    private static void hideNetherite(BuildCreativeModeTabContentsEvent event) {
        var removed = GearData.table().removed();
        event.removeIf((ItemStack stack) -> removed.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    static void log(String message) {
        Theoria.LOGGER.info("Gear: {}", message);
    }
}
