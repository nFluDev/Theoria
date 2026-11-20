package dev.theoria.cosmetics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** What may sit in a cosmetic slot and which worn piece is a pair of wings (never hidden: D15). Pure; the server uses it for every placement. */
public final class CosmeticRules {
    private CosmeticRules() {}

    /**
     * A cosmetic piece must be armor FOR THAT SLOT with an equipment asset: the vanilla armor layer draws a piece only when its {@code Equippable} names the slot and
     * has an asset id ({@code HumanoidArmorLayer.shouldRender}). Anything else would be accepted and then draw nothing — so it is refused here.
     */
    public static boolean accepts(EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty() || Cosmetics.indexOf(slot) < 0) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot && equippable.assetId().isPresent();
    }

    /** Elytra and any other glider: the chest piece is a function (flying) before it is a look, so the look rules never hide or replace it (D15). */
    public static boolean isWings(ItemStack stack) {
        return stack.has(DataComponents.GLIDER);
    }
}
