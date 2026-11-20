package dev.theoria.cosmetics;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link Container} view of a player's {@code theoria:cosmetics} attachment, so the four cosmetic slots are ordinary menu slots (click, shift-click, drag, hotbar swap
 * all work and the server validates every placement). The attachment record is immutable; every write replaces it ({@code setData} also syncs it to the viewers).
 * Slots hold ONE piece, so removing always takes the whole stack.
 */
public final class CosmeticContainer implements Container {
    private final Player player;

    public CosmeticContainer(Player player) {
        this.player = player;
    }

    @Override
    public int getContainerSize() {
        return Cosmetics.SLOTS.size();
    }

    @Override
    public boolean isEmpty() {
        return CosmeticsModule.of(player).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return CosmeticsModule.of(player).get(Cosmetics.SLOTS.get(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return removeItemNoUpdate(slot);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = getItem(slot).copy();
        if (!taken.isEmpty()) setItem(slot, ItemStack.EMPTY);
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        EquipmentSlot equipment = Cosmetics.SLOTS.get(slot);
        // The slot's mayPlace has already vetted a player's click; this guard is for every other caller (commands, tests, a modded menu): a stack that does not belong is dropped, not stored.
        if (!stack.isEmpty() && !CosmeticRules.accepts(equipment, stack)) return;
        player.setData(CosmeticsModule.COSMETICS, CosmeticsModule.of(player).with(equipment, stack));
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player who) {
        return who == player && player.isAlive();
    }

    @Override
    public void clearContent() {
        player.setData(CosmeticsModule.COSMETICS, Cosmetics.NONE);
    }
}
