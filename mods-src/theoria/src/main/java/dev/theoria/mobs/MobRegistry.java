package dev.theoria.mobs;

import dev.theoria.core.TheoriaRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registers one entity type, one spawn egg, the attributes and the spawn placement per {@link Species}; plus the Heart Crystal and the creative tab. */
public final class MobRegistry {
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<TheoriaMob>>> TYPES = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<SpawnEggItem>> EGGS = new LinkedHashMap<>();
    public static DeferredItem<Item> HEART_CRYSTAL;
    public static DeferredHolder<CreativeModeTab, CreativeModeTab> TAB;

    private MobRegistry() {}

    static void init(IEventBus modBus) {
        for (Species sp : MobData.species()) {
            var type = TheoriaRegistries.ENTITY_TYPES.registerEntityType(sp.id(), sp.archetype().factory(), sp.category(), b -> {
                b.sized(sp.width(), sp.height()).clientTrackingRange(sp.boss() ? 10 : 8).eyeHeight(sp.height() * 0.85F);
                if (sp.flag("fire_immune")) b.fireImmune();
                return b;
            });
            TYPES.put(sp.id(), type);
            EGGS.put(sp.id(), TheoriaRegistries.ITEMS.registerItem(sp.id() + "_spawn_egg", SpawnEggItem::new, () -> new Item.Properties().spawnEgg(type.get())));
        }
        HEART_CRYSTAL = TheoriaRegistries.ITEMS.registerItem("heart_crystal", HeartCrystalItem::new, () -> new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));
        TAB = TheoriaRegistries.CREATIVE_TABS.register("mobs", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.theoria.mobs"))
                .icon(() -> new ItemStack(HEART_CRYSTAL.get()))
                .displayItems((params, output) -> {
                    output.accept(HEART_CRYSTAL.get());
                    EGGS.values().forEach(egg -> output.accept(egg.get()));
                }).build());
        modBus.addListener(MobRegistry::onAttributes);
        modBus.addListener(MobRegistry::onSpawnPlacements);
    }

    public static Species species(EntityType<?> type) {
        Species sp = MobData.byId(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        if (sp == null) throw new IllegalStateException(type + " is not a Theoria species");
        return sp;
    }

    public static EntityType<TheoriaMob> type(String id) {
        return TYPES.get(id).get();
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (Species sp : MobData.species()) {
            AttributeSupplier.Builder b = Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, sp.hp()).add(Attributes.ATTACK_DAMAGE, sp.dmg()).add(Attributes.ARMOR, sp.armor())
                    .add(Attributes.MOVEMENT_SPEED, sp.speed()).add(Attributes.FOLLOW_RANGE, sp.follow())
                    .add(Attributes.KNOCKBACK_RESISTANCE, sp.kbRes()).add(Attributes.SCALE, sp.scale())
                    .add(Attributes.ATTACK_KNOCKBACK, sp.num("knockback", 0.0));
            if (sp.flying()) b.add(Attributes.FLYING_SPEED, sp.speed());
            event.put(TYPES.get(sp.id()).get(), b.build());
        }
    }

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (Species sp : MobData.species()) {
            var type = TYPES.get(sp.id()).get();
            event.register(type, sp.flying() ? SpawnPlacementTypes.NO_RESTRICTIONS : SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (t, level, reason, pos, random) -> MobSpawning.canSpawn(sp, level, reason, pos, random), RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
