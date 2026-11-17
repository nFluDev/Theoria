package dev.theoria.mobs.client;

import dev.theoria.Theoria;
import dev.theoria.mobs.MobData;
import dev.theoria.mobs.MobRegistry;
import dev.theoria.mobs.Species;
import java.util.Locale;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client wiring of Phase 8: one model layer per rig, one renderer per species, the spawn-egg tooltip (tier, class, drops). */
public final class MobClient {
    private MobClient() {}

    public static ModelLayerLocation layer(String rig) {
        return new ModelLayerLocation(Theoria.id("rig/" + rig), "main");
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(MobClient::registerLayers);
        modBus.addListener(MobClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(MobClient::onTooltip);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        RigData.rigs().forEach((name, rig) -> event.registerLayerDefinition(layer(name), () -> RigData.layerDefinition(rig)));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (Species sp : MobData.species()) event.registerEntityRenderer(MobRegistry.type(sp.id()), context -> new SpeciesRenderer(context, sp));
    }

    /** Spawn egg of a species: "Tier 4 · Wild · Normal" + its drop list (the replacement for a JEI drop page, which would need a compile dependency). */
    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Theoria.MODID) || !key.endsWith("_spawn_egg")) return;
        Species sp = MobData.byId(key.substring(0, key.length() - "_spawn_egg".length()));
        if (sp == null) return;
        var lines = event.getToolTip();
        lines.add(Component.translatable("theoria.mob.tier", sp.tier(), Component.translatable("theoria.mob.family." + sp.family()), Component.translatable("theoria.mob.cls." + sp.cls())).withStyle(ChatFormatting.GRAY));
        if (sp.drops().isEmpty()) return;
        lines.add(Component.translatable("theoria.mob.drops").withStyle(ChatFormatting.DARK_GRAY));
        for (Species.Drop drop : sp.drops()) {
            var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(drop.item()));
            Component name = item.map(h -> new ItemStack(h.value()).getHoverName()).orElse(Component.literal(drop.item()));
            lines.add(Component.translatable("theoria.mob.drop", name, String.format(Locale.ROOT, "%.0f", drop.chance() * 100)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
