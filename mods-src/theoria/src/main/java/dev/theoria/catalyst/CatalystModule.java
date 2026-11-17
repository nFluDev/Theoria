package dev.theoria.catalyst;

import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * Phase 7 entry point: the catalyst table, the component, the items and the forge, and the listeners that make catalysts do something
 * (attributes, virtual enchantments, combat hooks, Erudition). Called from the mod constructor, before any registry event.
 */
public final class CatalystModule {
    private CatalystModule() {}

    public static void init(IEventBus modBus) {
        CatalystComponents.init();
        CatalystItems.init();
        modBus.addListener(CatalystModule::addPlayerAttributes);
        EffectiveLevels.init();
        CatalystAttributes.init();
        CatalystCombat.init();
        ElementEffects.init();
        Erudition.init();
        Restoration.init();
        CatalystChecks.init();
    }

    private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityTypes.PLAYER, CatalystComponents.CRIT_CHANCE);
        event.add(EntityTypes.PLAYER, CatalystComponents.CRIT_DAMAGE);
        event.add(EntityTypes.PLAYER, CatalystComponents.LIFE_STEAL);
        event.add(EntityTypes.PLAYER, CatalystComponents.THORNS);
        event.add(EntityTypes.PLAYER, CatalystComponents.FOOD_SATURATION);
    }
}
