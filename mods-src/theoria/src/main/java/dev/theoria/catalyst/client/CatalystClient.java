package dev.theoria.catalyst.client;

import dev.theoria.catalyst.CatalystItems;
import dev.theoria.catalyst.CatalystTooltip;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client wiring of Phase 7: the Catalyst Forge screen and the catalyst tooltip lines. */
public final class CatalystClient {
    private CatalystClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(CatalystClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(CatalystClient::onTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CatalystItems.MENU.get(), CatalystScreen::new);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        var lines = event.getToolTip();
        CatalystTooltip.boostEnchantLines(event.getItemStack(), lines);
        lines.addAll(CatalystTooltip.forStack(event.getItemStack()));
    }
}
