package dev.theoria.curios.client;

import com.mojang.datafixers.util.Pair;
import dev.theoria.core.client.NetClient;
import dev.theoria.curios.CuriosMaxMenu;
import dev.theoria.curios.OpenCuriosMaxPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.client.screen.CuriosScreen;
import top.theillusivec4.curios.client.screen.button.CuriosButton;

/**
 * Client wiring of Phase 11: the Curios Max screen for the Curios Max menu, and the inventory's Curios button. Curios' button asks the server to open Curios' menu, which the server then
 * replaces with ours — an open, a close and a second open, and the close puts the mouse back in the middle of the window. So on the inventory screens we swap that button for one with the
 * same look and place that asks for ours directly: screen to screen, the cursor stays where it was. (The J key and any other mod's way in still use the server-side redirect, ADR-007 K5.)
 */
public final class CuriosClient {
    private CuriosClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(CuriosClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ScreenEvent.Init.Post.class, CuriosClient::swapButton);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(CuriosMaxMenu.MENU.get(), CuriosMaxScreen::new);
    }

    private static void swapButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        for (GuiEventListener listener : List.copyOf(event.getListenersList())) {
            if (!(listener instanceof CuriosButton old)) continue;
            event.removeListener(old);
            boolean creative = screen instanceof CreativeModeInventoryScreen;
            event.addListener(new OpenButton(screen, old.getX(), old.getY(), old.getWidth(), old.getHeight(), creative ? CuriosButton.SMALL : CuriosButton.BIG));
        }
    }

    /** Curios' button, drawn and placed exactly like Curios' own (same sprites, offsets, creative-tab rule), asking for the Max menu directly. */
    public static final class OpenButton extends ImageButton {
        private final AbstractContainerScreen<?> parent;

        OpenButton(AbstractContainerScreen<?> parent, int x, int y, int w, int h, WidgetSprites sprites) {
            super(x, y, w, h, sprites, button -> press(parent));
            this.parent = parent;
        }

        private static void press(AbstractContainerScreen<?> parent) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack carried = mc.player.containerMenu.getCarried();
            mc.player.containerMenu.setCarried(ItemStack.EMPTY);
            if (parent instanceof InventoryScreen inventory && inventory.recipeBookComponent.isVisible()) inventory.recipeBookComponent.toggleVisibility();
            NetClient.send(new OpenCuriosMaxPayload(carried));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
            boolean creative = parent instanceof CreativeModeInventoryScreen;
            Pair<Integer, Integer> offsets = CuriosScreen.getButtonOffset(creative);
            setX(parent.getLeftPos() + offsets.getFirst() + 2);
            setY(parent.getTopPos() + offsets.getSecond() + (creative ? 70 : 85));
            if (parent instanceof CreativeModeInventoryScreen gui) {
                active = gui.isInventoryOpen();
                if (!active) return;
            }
            super.extractContents(g, mouseX, mouseY, partialTicks);
        }
    }
}
