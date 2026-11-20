package dev.theoria.curios;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * The one door from the rest of the mod to the Curios inventory (ADR-007 K1: {@code dev.theoria.curios} is the only package that names Curios classes). Phase 12's relic effects ask
 * "what does this entity wear?" here and never see a Curios type. Everything is server-state reading plus two writers for tests and the {@code /theoria test relic} probe.
 */
public final class WornCurios {
    private WornCurios() {}

    /** Every non-empty stack in the entity's Curios slots (cosmetic stacks are not worn and are not listed). Empty list for an entity without a Curios inventory. */
    public static List<ItemStack> stacks(LivingEntity entity) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(entity);
        if (handler == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (ICurioStacksHandler type : handler.getCurios().values()) {
            IDynamicStackHandler slots = type.getStacks();
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStackInSlot(i);
                if (!stack.isEmpty()) out.add(stack);
            }
        }
        return out;
    }

    /** The slot count of a slot type for this entity (0 when the type is not loaded for it). */
    public static int slotCount(LivingEntity entity, String slotType) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(entity);
        if (handler == null) return 0;
        return handler.getStacksHandler(slotType).map(ICurioStacksHandler::getSlots).orElse(0);
    }

    /** Puts {@code stack} into the first empty slot of {@code slotType} (bypassing the tag validator — callers that care ask {@link #accepts}); the slot index, or -1 when every slot is taken. */
    public static int equip(LivingEntity entity, String slotType, ItemStack stack) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(entity);
        if (handler == null) return -1;
        ICurioStacksHandler type = handler.getStacksHandler(slotType).orElse(null);
        if (type == null) return -1;
        IDynamicStackHandler slots = type.getStacks();
        for (int i = 0; i < slots.getSlots(); i++) {
            if (slots.getStackInSlot(i).isEmpty()) {
                slots.setStackInSlot(i, stack);
                return i;
            }
        }
        return -1;
    }

    /** Overwrites one slot and returns what was in it (for swap-and-restore probes). */
    public static ItemStack swap(LivingEntity entity, String slotType, int index, ItemStack stack) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(entity);
        if (handler == null) return ItemStack.EMPTY;
        ICurioStacksHandler type = handler.getStacksHandler(slotType).orElse(null);
        if (type == null || index < 0 || index >= type.getSlots()) return ItemStack.EMPTY;
        ItemStack before = type.getStacks().getStackInSlot(index);
        type.getStacks().setStackInSlot(index, stack);
        return before;
    }

    /** Would Curios let this stack into that slot type of this entity (the {@code curios:tag} validator: {@code #curios:<slot>} or {@code #curios:curio})? */
    public static boolean accepts(LivingEntity entity, String slotType, ItemStack stack) {
        return CuriosApi.isStackValid(new top.theillusivec4.curios.api.SlotContext(slotType, entity, 0, false, true), stack);
    }
}
