package dev.theoria.catalyst;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Catalyst Forge's menu (ADR-006 Karar 12). Slot 0 is the target, slots 1-5 the inputs; there is no output slot. "Uygula" ({@link #BUTTON_APPLY}) goes to the
 * server as a menu button and the server is authoritative: it re-plans from the real stacks ({@link CatalystRules#plan}) and commits atomically or not at all.
 * The screen's preview runs the same pure functions on the synced stacks. With the target slot empty, "Uygula" forges a catalyst from the inputs instead
 * ({@link CatalystForging}). Nothing is stored on the block: closing the menu hands every stack back.
 */
public final class CatalystMenu extends AbstractContainerMenu {
    public static final int TARGET = 0;
    public static final int INPUTS = 5;
    public static final int FIRST_INPUT = 1;
    public static final int PLAYER_START = FIRST_INPUT + INPUTS;
    /** Menu button ids: apply, and {@code BUTTON_REMOVE + n} takes the catalyst out of slot n without refund (Q45). */
    public static final int BUTTON_APPLY = 0;
    public static final int BUTTON_REMOVE = 10;

    private final Container container = new SimpleContainer(1 + INPUTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            CatalystMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;

    public CatalystMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public CatalystMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(CatalystItems.MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        addSlot(new Slot(container, TARGET, CatalystLayout.TARGET_X, CatalystLayout.TARGET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CatalystHosts.of(stack) != null;
            }
        });
        for (int i = 0; i < INPUTS; i++) {
            addSlot(new Slot(container, FIRST_INPUT + i, CatalystLayout.INPUT_X + i * 18, CatalystLayout.INPUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isInputItem(stack);
                }
            });
        }
        addStandardInventorySlots(inventory, CatalystLayout.INVENTORY_X, CatalystLayout.INVENTORY_Y);
    }

    /** What the input slots accept: catalysts, Netherite, the three raw materials, a signature material or a gear ingot (graded forging). */
    public static boolean isInputItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof CatalystItem || stack.is(Items.NETHERITE_INGOT)
                || stack.is(CatalystItems.DUST.get()) || stack.is(CatalystItems.ESSENCE.get()) || stack.is(CatalystItems.CORE.get())
                || CatalystForging.bySignature(stack.getItem()) != null || CatalystForging.ingotTier(stack.getItem()) >= 0;
    }

    public ItemStack target() {
        return container.getItem(TARGET);
    }

    public List<ItemStack> inputs() {
        List<ItemStack> out = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) out.add(container.getItem(FIRST_INPUT + i));
        return out;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide()) return true; // the server decides; the client only asks
        if (id == BUTTON_APPLY) {
            apply();
            return true;
        }
        if (id >= BUTTON_REMOVE && id < BUTTON_REMOVE + CatalystData.table().slotMax() + CatalystData.table().platinumBonus()) {
            if (CatalystRules.remove(target(), id - BUTTON_REMOVE)) {
                container.setChanged();
                sound();
            }
            return true;
        }
        return false;
    }

    private void apply() {
        List<ItemStack> inputs = inputs();
        ItemStack target = target();
        boolean done;
        if (target.isEmpty()) {
            ItemStack made = CatalystForging.forge(inputs);
            done = !made.isEmpty();
            if (done) container.setItem(TARGET, made);
        } else {
            done = CatalystRules.apply(target, inputs).ok();
        }
        if (!done) return;
        for (int i = 0; i < INPUTS; i++) if (container.getItem(FIRST_INPUT + i).isEmpty()) container.setItem(FIRST_INPUT + i, ItemStack.EMPTY);
        container.setChanged();
        sound();
    }

    private void sound() {
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, container));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, CatalystItems.FORGE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return moved;
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (slotIndex < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (CatalystHosts.of(stack) != null && !slots.get(TARGET).hasItem()) {
            if (!moveItemStackTo(stack, TARGET, TARGET + 1, false)) return ItemStack.EMPTY;
        } else if (isInputItem(stack)) {
            if (!moveItemStackTo(stack, FIRST_INPUT, PLAYER_START, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == moved.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return moved;
    }

    public Player player() {
        return player;
    }
}
