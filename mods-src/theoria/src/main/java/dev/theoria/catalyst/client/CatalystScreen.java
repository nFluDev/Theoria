package dev.theoria.catalyst.client;

import dev.theoria.catalyst.CatalystHosts;
import dev.theoria.catalyst.CatalystLayout;
import dev.theoria.catalyst.CatalystMenu;
import dev.theoria.catalyst.CatalystRules;
import dev.theoria.catalyst.CatalystForging;
import dev.theoria.catalyst.CatalystTooltip;
import dev.theoria.catalyst.Catalysts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Kataliz Ocağı (ADR-006 Karar 12). Left: the vanilla-style container (target + 5 inputs + inventory). Right: the target's catalyst slots (× takes one out, no refund),
 * the green/red preview and "Uygula" (with the target slot empty: "Üret"). The preview is computed from the synced stacks by the same pure functions the server
 * uses ({@link CatalystRules#plan}, {@link CatalystForging#plan}); the SERVER decides — a click only asks. It does NOT pause the game
 * ({@link #isPauseScreen}, CLAUDE.md): a paused single-player server processes no packets. How it looks is the user's approval (no screenshots).
 */
public final class CatalystScreen extends AbstractContainerScreen<CatalystMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF373737;
    private static final int WELL = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF707070;
    private static final int TEXT_LOCKED = 0xFF909090;

    private final Button[] remove = new Button[5];
    private Button apply;
    private int shownSlots = -1;

    public CatalystScreen(CatalystMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CatalystLayout.IMAGE_W, CatalystLayout.IMAGE_H);
    }

    /** Must stay false: see the class comment. (The base class already returns false; stated again so nobody "fixes" it.) */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < remove.length; i++) {
            final int slot = i;
            remove[i] = addRenderableWidget(Button.builder(Component.literal("×"), b -> press(CatalystMenu.BUTTON_REMOVE + slot))
                    .bounds(leftPos + CatalystLayout.RIGHT_X + CatalystLayout.RIGHT_W - CatalystLayout.REMOVE_W, topPos + CatalystLayout.SLOT_LIST_Y + i * CatalystLayout.SLOT_LINE_H,
                            CatalystLayout.REMOVE_W, CatalystLayout.SLOT_LINE_H - 1).build());
            remove[i].setTooltip(Tooltip.create(Component.translatable("theoria.catalyst.remove")));
            remove[i].visible = false;
        }
        apply = addRenderableWidget(Button.builder(Component.translatable("theoria.catalyst.apply"), b -> press(CatalystMenu.BUTTON_APPLY))
                .bounds(leftPos + CatalystLayout.RIGHT_X, topPos + CatalystLayout.APPLY_Y, CatalystLayout.RIGHT_W, CatalystLayout.APPLY_H).build());
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_EDGE);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL);
        menu.slots.forEach(slot -> g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, WELL));
        g.fill(leftPos + CatalystLayout.RIGHT_X - 4, topPos + 6, leftPos + CatalystLayout.RIGHT_X - 3, topPos + imageHeight - 6, WELL); // column divider
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, CatalystLayout.INVENTORY_Y - 11, TEXT, false);
        ItemStack target = menu.target();
        List<ItemStack> inputs = menu.inputs();
        CatalystHosts.Host host = CatalystHosts.of(target);
        Catalysts catalysts = Catalysts.of(target);

        int x = CatalystLayout.RIGHT_X;
        if (host == null) {
            g.text(font, Component.translatable("theoria.catalyst.problem." + (target.isEmpty() ? "no_target" : "not_host")), x, CatalystLayout.SLOT_LIST_Y, TEXT_DIM, false);
            hideRemove();
        } else {
            int count = CatalystRules.slotCount(host);
            g.text(font, Component.translatable("theoria.catalyst.slots", catalysts.slots().size(), count), x, 6, TEXT, false);
            for (int i = 0; i < remove.length; i++) {
                int y = CatalystLayout.SLOT_LIST_Y + i * CatalystLayout.SLOT_LINE_H + 1;
                boolean filled = i < catalysts.slots().size();
                if (i >= count) {
                    g.text(font, Component.translatable("theoria.catalyst.slot.locked"), x, y, TEXT_LOCKED, false);
                } else if (filled) {
                    g.text(font, CatalystTooltip.slotLine(host, catalysts.slots().get(i)), x, y, TEXT, false);
                } else {
                    g.text(font, Component.translatable("theoria.catalyst.slot.empty"), x, y, TEXT_DIM, false);
                }
                remove[i].visible = filled;
            }
        }
        g.text(font, Component.translatable("theoria.catalyst.preview.title"), x, CatalystLayout.PREVIEW_Y - 11, TEXT, false);
        List<Component> lines = CatalystTooltip.preview(target, inputs);
        int y = CatalystLayout.PREVIEW_Y;
        for (Component line : lines) {
            for (var wrapped : font.split(line, CatalystLayout.RIGHT_W)) {
                if (y > CatalystLayout.APPLY_Y - CatalystLayout.PREVIEW_LINE_H) break;
                g.text(font, wrapped, x, y, 0xFFFFFFFF, false);
                y += CatalystLayout.PREVIEW_LINE_H;
            }
        }
        refreshApply(target, inputs);
    }

    private void hideRemove() {
        for (Button b : remove) b.visible = false;
    }

    /** "Uygula" / "Üret": enabled only when the pure plan says it can be committed; the reason is the tooltip. */
    private void refreshApply(ItemStack target, List<ItemStack> inputs) {
        boolean forging = target.isEmpty();
        apply.setMessage(Component.translatable(forging ? "theoria.catalyst.forge" : "theoria.catalyst.apply"));
        List<Component> reasons = new ArrayList<>();
        boolean ok;
        if (forging) {
            CatalystForging.Plan plan = CatalystForging.plan(inputs);
            ok = plan.ok();
            if (!ok) reasons.add(Component.translatable(plan.problem().langKey()));
        } else {
            CatalystRules.Plan plan = CatalystRules.plan(target, inputs);
            ok = plan.ok();
            if (!ok) reasons.add(Component.translatable(plan.problem().langKey(), plan.needed()));
        }
        apply.active = ok;
        apply.setTooltip(reasons.isEmpty() ? null : Tooltip.create(reasons.get(0)));
    }

    // ------------------------------------------------------------ dev scenario access (devtest catalyst)
    /** "Uygula"/"Üret" button, for the unattended client scenario. */
    public Button applyButton() {
        return apply;
    }

    /** The × button of catalyst slot {@code i}. */
    public Button removeButton(int i) {
        return remove[i];
    }

    /** {@code {leftPos, topPos, imageWidth, imageHeight}} */
    public int[] bounds() {
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
