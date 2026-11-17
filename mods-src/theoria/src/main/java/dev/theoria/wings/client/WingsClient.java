package dev.theoria.wings.client;

import dev.theoria.wings.WingsLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client wiring of Phase 4: the "Wings" button under the ESC menu's vanilla column (ADR-002 b). Placement follows LucidRem's
 * {@code PauseMenuHook} lessons (45.5): measure ONLY the vanilla centre column — mods that put buttons at the screen's corners and
 * edges (Essential, …) would drag a whole-screen measurement down to the screen floor — and clamp the result into the screen.
 */
public final class WingsClient {
    /** Lang key of the button label; the dev scenario finds the button by it. */
    public static final String BUTTON_KEY = "theoria.wings.button";

    private WingsClient() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(WingsClient::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) return;
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractButton button)) continue; // the title text and other non-buttons are not part of the column
            int centre = button.getX() + button.getWidth() / 2;
            if (Math.abs(centre - screen.width / 2) > WingsLayout.PAUSE_CENTRE_TOLERANCE) continue;
            top = Math.min(top, button.getY());
            bottom = Math.max(bottom, button.getY() + button.getHeight());
        }
        if (bottom == Integer.MIN_VALUE) return; // no vanilla column: the "saving world" variant of the screen
        WingsLayout.Rect r = WingsLayout.pauseRow(top, bottom, screen.width, screen.height, 1).get(0);
        event.addListener(Button.builder(Component.translatable(BUTTON_KEY),
                        button -> Minecraft.getInstance().gui.setScreen(new WingsScreen(screen)))
                .bounds(r.x(), r.y(), r.w(), r.h()).build());
    }
}
