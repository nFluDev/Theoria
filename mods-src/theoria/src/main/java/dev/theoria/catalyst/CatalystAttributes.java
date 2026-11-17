package dev.theoria.catalyst;

import dev.theoria.Theoria;
import dev.theoria.catalyst.CatalystHosts.Host;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.jspecify.annotations.Nullable;

/**
 * Type A catalysts (ADR-006 Karar 5): the item's attribute modifiers get one entry per filled attribute slot, computed live from the table — nothing is written
 * to the vanilla {@code attribute_modifiers} component, so rebalancing reaches existing items and the vanilla tooltip shows the lines on its own
 * (NeoForge routes {@code forEachModifier} through this event). Modifier ids are {@code theoria:catalyst/<id>/<slot>[/<target>]}.
 */
public final class CatalystAttributes {
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = new ConcurrentHashMap<>();

    private CatalystAttributes() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CatalystAttributes::onModifiers);
    }

    private static void onModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Catalysts catalysts = stack.get(CatalystComponents.CATALYSTS.get());
        if (catalysts == null || catalysts.slots().isEmpty()) return;
        Host host = CatalystHosts.of(stack);
        if (host == null) return;
        for (Entry entry : entries(host, catalysts)) {
            Holder<Attribute> attribute = attribute(entry.attribute());
            if (attribute != null) event.addModifier(attribute, new AttributeModifier(entry.modifierId(), entry.amount(), entry.operation()), entry.group());
        }
    }

    /** One attribute line a filled slot produces. Shared by the event and the tooltip so they cannot disagree. */
    public record Entry(CatalystData.Def def, int level, String target, String attribute, Identifier modifierId, double amount,
            AttributeModifier.Operation operation, EquipmentSlotGroup group) {}

    public static List<Entry> entries(Host host, Catalysts catalysts) {
        List<Entry> out = new java.util.ArrayList<>();
        CatalystData.Table table = CatalystData.table();
        for (int i = 0; i < catalysts.slots().size(); i++) {
            Catalysts.Slot slot = catalysts.slots().get(i);
            CatalystData.Def def = slot.def();
            if (def == null || def.kind() != CatalystData.Kind.ATTRIBUTE) continue;
            List<String> targets = host.matching(def);
            java.util.Set<String> applied = new java.util.HashSet<>(); // an axe is weapon AND tool: a catalyst that targets both with the same attribute (Şans) counts once
            for (String target : targets) {
                String attribute = def.effects().get(target);
                double amount = table.value(def.id(), target, host.idx(), slot.level());
                if (attribute == null || Double.isNaN(amount) || !applied.add(attribute)) continue;
                String suffix = distinctAttributes(def, targets) > 1 ? "/" + target : "";
                out.add(new Entry(def, slot.level(), target, attribute, Theoria.id("catalyst/" + def.id() + "/" + i + suffix), amount,
                        def.op().equals("add_multiplied_base") ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE, group(target)));
            }
        }
        return out;
    }

    private static int distinctAttributes(CatalystData.Def def, List<String> targets) {
        return (int) targets.stream().map(t -> def.effects().get(t)).filter(java.util.Objects::nonNull).distinct().count();
    }

    private static EquipmentSlotGroup group(String target) {
        return switch (target) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.MAINHAND; // weapon / tool / ranged: while held
        };
    }

    public static @Nullable Holder<Attribute> attribute(String id) {
        return ATTRIBUTES.computeIfAbsent(id, key -> BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(key)).orElse(null));
    }
}
