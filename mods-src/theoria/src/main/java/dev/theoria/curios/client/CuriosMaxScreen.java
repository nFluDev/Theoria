package dev.theoria.curios.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.core.client.NetClient;
import dev.theoria.cosmetics.CosmeticRules;
import dev.theoria.cosmetics.CosmeticSlot;
import dev.theoria.cosmetics.Cosmetics;
import dev.theoria.cosmetics.CosmeticsModule;
import dev.theoria.cosmetics.ToggleCosmeticHiddenPayload;
import dev.theoria.curios.CuriosMaxLayout;
import dev.theoria.curios.CuriosMaxMenu;
import dev.theoria.wings.client.WingsScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.client.screen.CuriosScreen;
import top.theillusivec4.curios.client.screen.button.ICuriosWidget;
import top.theillusivec4.curios.client.screen.button.RenderButton;
import top.theillusivec4.curios.common.inventory.container.CuriosMenu;

/**
 * Curios Max (docs/04 §7, ADR-007 K2) — Curios' OWN classic screen, subclassed: the inventory on the right, Curios' panel hanging off its left edge (same texture, same columns, the
 * render-toggle dot at the top-right of every slot), and one extra row on top of the panel: the four cosmetic armor slots and the Wings cell, a separator line under it. Each cosmetic
 * slot has the same dot: lit = drawn, dim = hidden (a hidden slot draws nothing on the character, whatever is worn or set there). <b>Ctrl + left click</b> on an armor piece in the
 * inventory puts it into the matching cosmetic slot (it overrides what is worn there); Ctrl + click on a cosmetic piece takes it out. Everything is the vanilla container protocol — the
 * server validates every move — except the hide dot, one small request. It does NOT pause the game ({@link #isPauseScreen}, CLAUDE.md). How it looks is the user's approval.
 */
public final class CuriosMaxScreen extends CuriosScreen {
    /** Curios' own texture: the render-toggle dot is 8×8 at u=75 (lit) / u=83 (dim). */
    private static final Identifier CURIO_TEXTURE = Identifier.fromNamespaceAndPath("curios", "textures/gui/curios/inventory.png");
    private static final int COSMETIC_TINT = 0x66A070E0;
    private static final int SEPARATOR = 0xFF373737;
    private static final int HOVER = 0x80FFFFFF;

    /** Dev scenario evidence: frames drawn. */
    private static long frames;

    public CuriosMaxScreen(CuriosMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** Must stay false: see the class comment. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private CuriosMaxMenu max() {
        return (CuriosMaxMenu) menu;
    }

    @Override
    public void init() {
        super.init(); // Curios builds its render dots here, through updateRenderButtons() — which adds ours too
        int want = CuriosMaxLayout.leftPos(width, menu.grid.size());
        if (leftPos < want) { // a narrow GUI (320): push the inventory right so the panel stays on screen, and every widget with it
            int dx = want - leftPos;
            leftPos = want;
            for (GuiEventListener child : children()) {
                if (child instanceof AbstractWidget w) w.setX(w.getX() + dx);
            }
        }
    }

    /** Curios' render dots, plus ours: the four hide dots and the Wings cell (re-created whenever Curios rebuilds its widgets). */
    @Override
    public void updateRenderButtons() {
        super.updateRenderButtons();
        if (!(menu instanceof CuriosMaxMenu max)) return;
        for (int i = 0; i < Cosmetics.SLOTS.size(); i++) {
            CosmeticSlot slot = max.cosmeticSlot(Cosmetics.SLOTS.get(i));
            if (slot != null) addRenderableWidget(new HideDot(leftPos + slot.x + 12, topPos + slot.y - 1, i));
        }
        addRenderableWidget(new WingsCell(leftPos + CuriosMaxLayout.cellX(CuriosMaxLayout.WINGS_CELL, menu.panelWidth), topPos + CuriosMaxLayout.rowY(0),
                () -> minecraft.gui.setScreen(new WingsScreen(this))));
    }

    // ------------------------------------------------------------------------------------------------ drawing
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        frames++;
        // the cosmetic row stands out: a violet tint under its slots and its Wings cell, and a separator line under it
        for (Slot slot : menu.slots) {
            if (slot instanceof CosmeticSlot) g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, COSMETIC_TINT);
        }
        int wingsX = leftPos + CuriosMaxLayout.cellX(CuriosMaxLayout.WINGS_CELL, menu.panelWidth);
        g.fill(wingsX - 1, topPos + CuriosMaxLayout.rowY(0) - 1, wingsX + 17, topPos + CuriosMaxLayout.rowY(0) + 17, COSMETIC_TINT);
        int x0 = leftPos + CuriosMaxLayout.cellX(0, menu.panelWidth) - 1;
        int y = topPos + CuriosMaxLayout.rowY(1) - 1;
        g.fill(x0, y - 1, x0 + CuriosMaxLayout.SLOT * menu.grid.size(), y + 1, SEPARATOR);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredSlot instanceof CosmeticSlot cosmetic && !cosmetic.hasItem() && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(List.of(Component.translatable("theoria.curios.cosmetic_slot." + cosmetic.equipment().getName()).getVisualOrderText()), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------------------------------------ Ctrl + click
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.hasControlDown() && hoveredSlot != null && toggleCosmetic(hoveredSlot)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The Ctrl + click of the design: an armor piece in the inventory goes into its cosmetic slot (a piece already there comes back to the clicked slot — a swap), a cosmetic piece goes
     * back to the inventory. Three vanilla container clicks at most; the server checks each ({@code CosmeticSlot#mayPlace}). Public so the unattended scenario can press it without a keyboard.
     *
     * @return true if the click was ours
     */
    public boolean toggleCosmetic(Slot source) {
        if (minecraft == null || minecraft.gameMode == null || minecraft.player == null || !(menu instanceof CuriosMaxMenu max) || !menu.getCarried().isEmpty() || !source.hasItem()) return false;
        int id = menu.containerId;
        if (source instanceof CosmeticSlot) {
            minecraft.gameMode.handleContainerInput(id, source.index, 0, ContainerInput.QUICK_MOVE, minecraft.player);
            return true;
        }
        if (source.index < CuriosMaxMenu.INVENTORY_START || source.index >= CuriosMaxMenu.INVENTORY_END) return false; // worn armor and Curios slots keep their vanilla clicks
        for (EquipmentSlot equipment : Cosmetics.SLOTS) {
            if (!CosmeticRules.accepts(equipment, source.getItem())) continue;
            CosmeticSlot target = max.cosmeticSlot(equipment);
            if (target == null) return false;
            minecraft.gameMode.handleContainerInput(id, source.index, 0, ContainerInput.PICKUP, minecraft.player);
            minecraft.gameMode.handleContainerInput(id, target.index, 0, ContainerInput.PICKUP, minecraft.player);
            if (!menu.getCarried().isEmpty()) minecraft.gameMode.handleContainerInput(id, source.index, 0, ContainerInput.PICKUP, minecraft.player); // the old cosmetic piece
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ widgets
    /** The hide dot of a cosmetic slot: Curios' own render-toggle sprite and behaviour (lit = drawn, dim = hidden), a request to the server. */
    public static final class HideDot extends RenderButton {
        private final int index;

        HideDot(int x, int y, int index) {
            super(null, x, y, 8, 8, 75, 0, CURIO_TEXTURE, button -> NetClient.send(new ToggleCosmeticHiddenPayload(index)));
            this.index = index;
        }

        public boolean hidden() {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null && CosmeticsModule.of(player).isHidden(Cosmetics.SLOTS.get(index));
        }

        /** Curios calls this after the screen is drawn (its dots sit on top of the slots). */
        @Override
        public void renderButtonOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
            g.blit(RenderPipelines.GUI_TEXTURED, CURIO_TEXTURE, getX(), getY(), hidden() ? 83 : 75, 0, width, height, 256, 256);
        }

        /** Test hook: the same press a click makes. */
        public void press() {
            onPress(null);
        }
    }

    /** The fifth cell of the cosmetic row: wings are cosmetic too (D14), so it opens the Wings screen. */
    public static final class WingsCell extends ImageButton implements ICuriosWidget {
        private final Runnable open;

        WingsCell(int x, int y, Runnable open) {
            super(x, y, 16, 16, RenderButton.BUTTON_SPRITES, button -> open.run());
            this.open = open;
            setTooltip(Tooltip.create(Component.translatable("theoria.curios.wings")));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
            g.item(new ItemStack(Items.ELYTRA), getX(), getY());
            if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + 16, getY() + 16, HOVER);
        }

        public void press() {
            open.run();
        }
    }

    // ------------------------------------------------------------------------------------------------ dev scenario evidence
    public static long frames() {
        return frames;
    }

    public List<HideDot> hideDots() {
        List<HideDot> out = new ArrayList<>();
        for (GuiEventListener child : children()) if (child instanceof HideDot d) out.add(d);
        return out;
    }

    public WingsCell wingsCell() {
        for (GuiEventListener child : children()) if (child instanceof WingsCell w) return w;
        return null;
    }
}
