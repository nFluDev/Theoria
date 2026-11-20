package dev.theoria.relics;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A relic: a Curios trinket that does nothing in the hand — the effect belongs to {@link RelicEffects}, which looks at what is worn. The item only knows its table row and its tooltip
 * (the tooltip is the item's own, so JEI shows it too — Phase 12's "JEI tooltip", no JEI code needed).
 */
public final class RelicItem extends Item {
    private final RelicData.Def def;

    public RelicItem(Properties properties, RelicData.Def def) {
        super(properties);
        this.def = def;
    }

    public RelicData.Def def() {
        return def;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        RelicText.tooltip(def).forEach(builder);
    }
}
