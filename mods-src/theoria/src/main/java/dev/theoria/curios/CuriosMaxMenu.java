package dev.theoria.curios;

import dev.theoria.core.TheoriaRegistries;
import dev.theoria.cosmetics.CosmeticContainer;
import dev.theoria.cosmetics.CosmeticSlot;
import dev.theoria.cosmetics.Cosmetics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosMenu;

/**
 * The Curios Max menu (ADR-007 K2, redesigned 2026-11-20 on the user's screenshot): Curios' own classic {@link CuriosMenu} — crafting grid, armor, inventory, offhand, the left panel of
 * Curios slots with their render toggles — plus ONE row on top of that panel: the four cosmetic armor slots ({@code theoria:cosmetics}) and the Wings cell. Slot order is exactly
 * Curios' [0 result · 1-4 crafting · 5-8 armor · 9-44 inventory · 45 offhand · 46… Curios slots], with the four cosmetic slots appended LAST (so every index Curios' quick-move
 * code hard-codes stays valid). One page only: Curios' paging (a server-side rebuild per page) is not used, the panel grows columns instead ({@link CuriosMaxLayout}).
 * It must report ITS menu type to the server (Curios' constructor passes Curios' own), hence the {@code menuType} access transformer.
 *
 * <p>Curios' constructor calls {@link #setPage} before this class' fields exist: the override uses only what {@link CuriosMenu} has assigned by then ({@code player},
 * {@code curiosHandler}) and builds everything else locally.
 */
public final class CuriosMaxMenu extends CuriosMenu {
    public static DeferredHolder<MenuType<?>, MenuType<CuriosMenu>> MENU;
    /** result 1 + crafting 4 + armor 4 + inventory 36 + offhand 1 — Curios' slots start here. */
    public static final int CURIO_START = 46;
    public static final int INVENTORY_START = 9;
    public static final int INVENTORY_END = 45;

    public CuriosMaxMenu(int containerId, Inventory inventory) {
        super(containerId, inventory);
        this.menuType = MENU.get();
    }

    public static void register() {
        MENU = TheoriaRegistries.MENUS.register("curios_max", () -> new MenuType<CuriosMenu>((id, inventory) -> new CuriosMaxMenu(id, inventory), FeatureFlags.VANILLA_SET));
    }

    /** Vanilla's own title key (the label above the crafting grid, as in Curios' classic screen) — not ours to translate. */
    private static final String TITLE_KEY = "container.crafting";

    public static MenuProvider provider() {
        return new SimpleMenuProvider((id, inventory, player) -> new CuriosMaxMenu(id, inventory), Component.translatable(TITLE_KEY));
    }

    // ------------------------------------------------------------------------------------------------ slots
    @Override
    public void setPage(int page) {
        List<ICurioStacksHandler> visible = new ArrayList<>();
        int total = 0;
        if (curiosHandler != null) {
            for (ICurioStacksHandler stacks : curiosHandler.getCurios().values()) {
                if (stacks.isVisible() && stacks.getSlots() > 0) {
                    visible.add(stacks);
                    total += stacks.getSlots();
                }
            }
        }
        int columns = CuriosMaxLayout.columns(total);
        panelWidth = CuriosMaxLayout.panelWidth(columns);
        slots.clear();
        lastSlots.clear();
        remoteSlots.clear();
        addResultSlot(player, 154, 28);
        addCraftingGridSlots(98, 18);
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = Cosmetics.SLOTS.get(i); // head, chest, legs, feet = the vanilla armor slots in menu order
            addSlot(new ArmorSlot(player.getInventory(), player, slot, 39 - i, 8, 8 + i * 18, Cosmetics.emptyIcon(slot)));
        }
        addStandardInventorySlots(player.getInventory(), 8, 84);
        addSlot(new Slot(player.getInventory(), 40, 77, 62) {
            @Override
            public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
                owner().onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
                super.setByPlayer(newStack, oldStack);
            }

            @Override
            public Identifier getNoItemIcon() {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });
        int[] rows = new int[columns];
        Arrays.fill(rows, 1); // every column starts with the cosmetic row
        int n = 0;
        for (ICurioStacksHandler stacks : visible) {
            for (int i = 0; i < stacks.getSlots(); i++, n++) {
                int column = n % columns;
                int row = 1 + n / columns;
                addSlot(new CurioSlot(player, stacks.getStacks(), i, stacks.getIdentifier(), CuriosMaxLayout.cellX(column, panelWidth), CuriosMaxLayout.rowY(row),
                        stacks.getRenders(), stacks.getActiveStates(), stacks.canToggleRendering(), false));
                rows[column]++;
            }
        }
        CosmeticContainer cosmetics = new CosmeticContainer(player);
        for (int i = 0; i < Cosmetics.SLOTS.size(); i++) {
            addSlot(new CosmeticSlot(cosmetics, i, Cosmetics.SLOTS.get(i), CuriosMaxLayout.cellX(i, panelWidth), CuriosMaxLayout.rowY(0)));
        }
        grid.clear();
        for (int count : rows) grid.add(count);
        currentPage = 0;
        totalPages = 1;
        hasCosmetics = false; // Curios' own cosmetic stacks are not used (the cosmetic row is ours)
    }

    /** Slots of the player's Curios inventory the panel shows. */
    public int curioSlotCount() {
        int n = 0;
        for (Slot s : slots) if (s instanceof CurioSlot) n++;
        return n;
    }

    /** The cosmetic slot for {@code equipment}, or null. */
    public CosmeticSlot cosmeticSlot(EquipmentSlot equipment) {
        for (Slot s : slots) if (s instanceof CosmeticSlot c && c.equipment() == equipment) return c;
        return null;
    }

    // ------------------------------------------------------------------------------------------------ moving items
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        if (index >= CURIO_START && index < slots.size() && slots.get(index) instanceof CosmeticSlot) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            ItemStack before = stack.copy();
            if (stack.isEmpty() || !moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, before);
            else slot.setChanged();
            return before;
        }
        return super.quickMoveStack(who, index);
    }
}
