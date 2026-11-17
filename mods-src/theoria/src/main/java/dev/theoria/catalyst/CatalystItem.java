package dev.theoria.catalyst;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A catalyst on the player's side of the table (ADR-006 Karar 3): one item per catalyst id; the LEVEL (and for the graded Menzil Gücü the GRADE) are item
 * components, so two levels never stack. The item does nothing on its own — it is applied in the Catalyst Forge. Netherite is the vanilla ingot, not this class.
 */
public final class CatalystItem extends Item {
    private final CatalystData.Def def;

    public CatalystItem(Properties properties, CatalystData.Def def) {
        super(properties);
        this.def = def;
    }

    public CatalystData.Def def() {
        return def;
    }

    public static int levelOf(ItemStack stack) {
        return stack.getOrDefault(CatalystComponents.LEVEL.get(), 1);
    }

    public static int gradeOf(ItemStack stack) {
        return stack.getOrDefault(CatalystComponents.GRADE.get(), 0);
    }

    /** "Güç Katalizörü IV": the catalyst's name plus its level numeral. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(def.nameKey()).append(" ").append(CatalystText.roman(levelOf(stack)));
    }
}
