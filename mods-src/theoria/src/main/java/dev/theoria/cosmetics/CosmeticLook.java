package dev.theoria.cosmetics;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Which stack the armor layer draws on one slot of one avatar — the whole look rule of docs/04 §6 as one pure function (ADR-007 K4), so the unit checks,
 * the GameTests and the render-state modifier run the very same code.
 *
 * <pre>
 *   chest and the worn piece is wings (elytra) ............ the worn piece (D15: never hidden, never replaced)
 *   the viewer does not show cosmetics for this avatar .... the worn piece (vanilla — the hide switches are part of the look too)
 *   the slot's hide switch is on ........................... nothing (neither the cosmetic nor the worn piece)
 *   a cosmetic piece is in the slot ........................ the cosmetic piece (it overrides the worn one)
 *   otherwise .............................................. the worn piece
 * </pre>
 * With both viewer switches off every avatar is drawn exactly as vanilla does.
 */
public final class CosmeticLook {
    private CosmeticLook() {}

    /**
     * @param self       the avatar is the viewer's own player
     * @param showOwn    setting "show my cosmetics" — applies to {@code self}
     * @param showOthers setting "show other players' cosmetics" — applies to everyone else
     */
    public record Viewer(boolean self, boolean showOwn, boolean showOthers) {
        public static final Viewer VANILLA = new Viewer(true, false, false);

        boolean showsCosmetics() {
            return self ? showOwn : showOthers;
        }
    }

    public static ItemStack resolve(EquipmentSlot slot, ItemStack worn, ItemStack cosmetic, boolean hidden, Viewer viewer) {
        if (slot == EquipmentSlot.CHEST && CosmeticRules.isWings(worn)) return worn;
        if (!viewer.showsCosmetics()) return worn;
        if (hidden) return ItemStack.EMPTY;
        if (!cosmetic.isEmpty()) return cosmetic;
        return worn;
    }
}
