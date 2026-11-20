package dev.theoria.stats.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.Theoria;
import dev.theoria.stats.StatsModule;
import dev.theoria.wings.WingsLayout;
import dev.theoria.wings.client.WingsClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client wiring of Phase 10: the {@code K} key (rebindable), the "Status" button next to "Wings" under the ESC menu's vanilla column, the Orb of
 * Oblivion tooltip. The XP bar shows vanilla's level ({@code dev.theoria.hud.client.LevelLayer}); the skill level is on the Status screen. No mixins, vanilla GUI only.
 */
public final class StatsClient {
    public static final String BUTTON_KEY = "theoria.stats.button";
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Theoria.id("main"));
    public static final KeyMapping OPEN = new KeyMapping("key.theoria.stats", InputConstants.KEY_K, CATEGORY);

    private StatsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(StatsClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(StatsClient::onTick);
        NeoForge.EVENT_BUS.addListener(StatsClient::onScreenInit);
        NeoForge.EVENT_BUS.addListener(StatsClient::onTooltip);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN);
    }

    /** The key opens the screen in the world (no other screen open); the screen itself closes on the same key. */
    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.gui.screen() == null) mc.gui.setScreen(new StatsScreen(null));
        }
    }

    /** Same measuring as WingsClient: only the vanilla centre column counts, the row goes under it, our button is the second of two. */
    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) return;
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractButton button) || WingsClient.isOurs(button)) continue;
            int centre = button.getX() + button.getWidth() / 2;
            if (Math.abs(centre - screen.width / 2) > WingsLayout.PAUSE_CENTRE_TOLERANCE) continue;
            top = Math.min(top, button.getY());
            bottom = Math.max(bottom, button.getY() + button.getHeight());
        }
        if (bottom == Integer.MIN_VALUE) return;
        WingsLayout.Rect r = WingsLayout.pauseRow(top, bottom, screen.width, screen.height, WingsClient.PAUSE_BUTTONS).get(1);
        event.addListener(WingsClient.ours(Button.builder(Component.translatable(BUTTON_KEY), button -> Minecraft.getInstance().gui.setScreen(new StatsScreen(screen)))
                .bounds(r.x(), r.y(), r.w(), r.h()).build()));
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (StatsModule.OBLIVION_ORB != null && stack.is(StatsModule.OBLIVION_ORB.get())) {
            event.getToolTip().add(Component.translatable("theoria.stats.orb.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
