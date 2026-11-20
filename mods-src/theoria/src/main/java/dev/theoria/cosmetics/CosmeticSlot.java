package dev.theoria.cosmetics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** One cosmetic armor slot of a menu: takes only armor made for {@code equipment} ({@link CosmeticRules#accepts}), one piece, and shows the vanilla empty-armor icon. */
public final class CosmeticSlot extends Slot {
    private final EquipmentSlot equipment;

    public CosmeticSlot(Container container, int index, EquipmentSlot equipment, int x, int y) {
        super(container, index, x, y);
        this.equipment = equipment;
    }

    public EquipmentSlot equipment() {
        return equipment;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return CosmeticRules.accepts(equipment, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return Cosmetics.emptyIcon(equipment);
    }
}
