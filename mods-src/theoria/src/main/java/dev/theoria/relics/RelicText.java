package dev.theoria.relics;

import dev.theoria.catalyst.CatalystText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Everything player-facing that is computed for a relic (tooltip lines): pure functions over {@link RelicData.Def}, shared by the item, JEI (which shows the item's own tooltip) and the
 * tests, so what the player reads is what is tested. The wording is in the lang files ({@code theoria.relic.*}); the numbers are the ones in the data table and nowhere else.
 */
public final class RelicText {
    private RelicText() {}

    /** The values the description's {@code %s}s show, in order — {@code generator/relics.py} checks each description has exactly this many. */
    public static Object[] displayValues(RelicData.Def def) {
        List<String> values = new ArrayList<>();
        switch (def.kind()) {
            case ATTRIBUTE -> {
                String fmt = def.str("fmt");
                if (!fmt.equals("none")) {
                    for (RelicData.Mod mod : def.mods()) values.add(signed(fmt.equals("pct") ? mod.amount() * 100 : mod.amount()) + (fmt.equals("pct") ? "%" : ""));
                }
            }
            case MAGNET, REVEAL -> values.add(CatalystText.number(def.num("radius")));
            case FOOD, REPAIR -> values.add(CatalystText.number(def.num("interval") / 20.0));
            case IMMUNE -> values.add(CatalystText.number(def.num("factor") * 100) + "%");
            case SURVIVE -> {
                values.add(CatalystText.number(def.num("cooldown") / 1200.0));
                values.add(CatalystText.number(def.num("heal") * 100) + "%");
            }
            case EFFECT -> {}
        }
        return values.toArray();
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "−") + CatalystText.number(Math.abs(value));
    }

    /** Where this relic is found: chest sources by their lang key, bosses by the entity name. */
    public static Component sources(RelicData.Def def) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < def.sources().size(); i++) {
            String id = def.sources().get(i).source();
            boolean boss = RelicData.table().sources().stream().anyMatch(s -> s.id().equals(id) && s.boss());
            if (i > 0) out.append(Component.literal(", "));
            String key = (boss ? "entity.theoria." : "theoria.relic.source.") + id;
            out.append(Component.translatable(key));
        }
        return out;
    }

    /** Tooltip lines under the name: rarity, what it does, which Curios slot, where to find it. */
    public static List<Component> tooltip(RelicData.Def def) {
        ChatFormatting color = def.vanillaRarity().color();
        String descKey = "theoria.relic.desc." + def.id();
        String rarityKey = "theoria.relic.rarity." + def.rarity();
        String slotKey = "curios.identifier." + def.slot();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(rarityKey).withStyle(color));
        lines.add(Component.translatable(descKey, displayValues(def)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("theoria.relic.slot", Component.translatable(slotKey)).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("theoria.relic.source", sources(def)).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
