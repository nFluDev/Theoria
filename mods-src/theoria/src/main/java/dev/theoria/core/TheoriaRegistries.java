package dev.theoria.core;

import dev.theoria.Theoria;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Every DeferredRegister of the mod lives here and is attached to the mod bus in {@link #ORDER}.
 * A new registry type = one field + one entry in ORDER (blocks before items before everything that refers to them).
 */
public final class TheoriaRegistries {
    /** Phase 6 gear: blocks before items (block items refer to blocks), tab last (refers to items). Populated by dev.theoria.gear.GearRegistry. */
    /** Phase 7 catalysts: the {@code theoria:catalysts} component and friends, the custom combat attributes, the forge menu. Components first: item defaults refer to them. */
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Theoria.MODID);
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Theoria.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Theoria.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Theoria.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Theoria.MODID);
    /** Phase 8 species: entity types before items (spawn eggs refer to them). */
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Theoria.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Theoria.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Theoria.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Theoria.MODID);
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Theoria.MODID);

    private static final List<DeferredRegister<?>> ORDER = List.of(DATA_COMPONENTS, ATTRIBUTES, BLOCKS, ENTITY_TYPES, ITEMS, MENUS, CREATIVE_TABS, LOOT_MODIFIER_TYPES, ATTACHMENTS, TEST_FUNCTIONS);

    private TheoriaRegistries() {}

    public static void init(IEventBus modBus) {
        ORDER.forEach(register -> register.register(modBus));
    }

    /** Entry counts per registry, for /theoria test core. */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        for (DeferredRegister<?> register : ORDER) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(register.getRegistryKey().identifier().getPath()).append('=').append(register.getEntries().size());
        }
        return sb.toString();
    }
}
