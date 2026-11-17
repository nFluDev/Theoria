package dev.theoria.catalyst;

import dev.theoria.Theoria;
import dev.theoria.catalyst.CatalystHosts.Host;
import dev.theoria.gear.GearData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * The one {@link GetEnchantmentLevelEvent} listener (ADR-004 Karar 4, ADR-006 Karar 6-9) — proven by the GameTest {@code enchant_effective_level}.
 * It answers "what level does this stack have, for gameplay": the real (vanilla component) level, plus
 * <ul>
 * <li><b>Netherite</b> bonus on real enchantments (≥ 1) — {@link CatalystRules#netheriteBonus};</li>
 * <li><b>catalyst</b> virtual enchantments (element, Onarım, Menzil Gücü, Bilgelik) — not in the component, only visible here;</li>
 * <li><b>innate</b> virtual enchantments of tiered gear (Q34, {@code innate.csv}) — need no component at all.</li>
 * </ul>
 * Enchantments of ours that need the host tier encode it: {@code P = 1 + 5·g + (L − 1)} (decode with {@link CatalystRules#decodeTier}/{@link CatalystRules#decodeLevel}).
 * Vanilla enchantments get a plain level. Hot path: the event fires on almost every gameplay query, so the first lines return for stacks that have neither
 * the component nor an innate entry (a map lookup, no allocation).
 */
public final class EffectiveLevels {
    /** One innate trait of an item: a real vanilla enchantment gets {@code level} added; a Theoria one is P-encoded ({@code encoded}) with the item's tier. */
    private record Innate(ResourceKey<Enchantment> key, int level, int tier, boolean encoded) {}

    private static volatile @Nullable Map<Item, Innate[]> innate;

    private EffectiveLevels() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EffectiveLevels::onLevel);
    }

    // ------------------------------------------------------------------------------------------------ listener
    private static void onLevel(GetEnchantmentLevelEvent event) {
        ItemInstance stack = event.getStack();
        Item item = stack.typeHolder().value();
        Innate[] traits = innateTable().get(item);
        Catalysts catalysts = stack.get(CatalystComponents.CATALYSTS.get());
        if (traits == null && catalysts == null) return;
        Host host = catalysts == null ? null : CatalystHosts.of(item);
        ItemEnchantments.Mutable map = event.getEnchantments();
        Holder<Enchantment> target = event.getTargetEnchant();
        if (target != null) {
            ResourceKey<Enchantment> key = target.unwrapKey().orElse(null);
            if (key == null) return;
            int raw = map.getLevel(target);
            int level = levelFor(key, target, raw, catalysts, host, traits);
            if (level != raw) map.set(target, level);
            return;
        }
        // "All enchantments": real ones (Netherite) and every virtual key this stack can have, each resolved once.
        Map<ResourceKey<Enchantment>, Holder<Enchantment>> keys = new LinkedHashMap<>();
        if (catalysts != null && catalysts.netherite() > 0) {
            for (Holder<Enchantment> held : new ArrayList<>(map.keySet())) held.unwrapKey().ifPresent(k -> keys.put(k, held));
        }
        if (catalysts != null) {
            for (Catalysts.Slot slot : catalysts.slots()) {
                CatalystData.Def def = slot.def();
                if (def != null && def.enchantment() != null) event.getHolder(def.enchantment()).ifPresent(h -> keys.putIfAbsent(def.enchantment(), h));
            }
        }
        if (traits != null) {
            for (Innate trait : traits) event.getHolder(trait.key()).ifPresent(h -> keys.putIfAbsent(trait.key(), h));
        }
        for (var entry : keys.entrySet()) {
            Holder<Enchantment> holder = entry.getValue();
            int raw = map.getLevel(holder);
            int level = levelFor(entry.getKey(), holder, raw, catalysts, host, traits);
            if (level != raw) map.set(holder, level);
        }
    }

    /** The effective level of {@code key} given its real level {@code raw}. Pure; the listener and the checks share it. */
    static int levelFor(ResourceKey<Enchantment> key, Holder<Enchantment> holder, int raw, @Nullable Catalysts catalysts, @Nullable Host host, Innate @Nullable [] traits) {
        CatalystData.Table table = CatalystData.table();
        int netherite = catalysts == null ? 0 : catalysts.netherite();
        int bonus = CatalystRules.netheriteBonus(holder, raw, netherite);
        int plainInnate = 0;
        int encodedInnate = 0;
        int innateTier = 0;
        if (traits != null) {
            for (Innate trait : traits) {
                if (!trait.key().equals(key)) continue;
                if (trait.encoded()) {
                    encodedInnate = Math.max(encodedInnate, trait.level());
                    innateTier = trait.tier();
                } else {
                    plainInnate += trait.level();
                }
            }
        }
        int catalystLevel = 0;
        int catalystTier = 0;
        boolean erudition = false;
        if (catalysts != null && host != null) {
            for (Catalysts.Slot slot : catalysts.slots()) {
                CatalystData.Def def = slot.def();
                if (def == null || def.enchantment() == null || !def.enchantment().equals(key)) continue;
                catalystLevel = Math.max(catalystLevel, slot.level());
                catalystTier = def.graded() ? slot.grade() : host.idx();
                erudition = def.kind() == CatalystData.Kind.ERUDITION;
            }
        }
        int level;
        if (erudition) {
            level = Math.max(raw, catalystLevel) + bonus; // ADR-006 Karar 8: the Netherite bonus is the real part's alone
        } else if (key.identifier().getNamespace().equals(Theoria.MODID) && (catalystLevel > 0 || encodedInnate > 0)) {
            int sum = Math.min(table.levels(), catalystLevel + encodedInnate);
            level = CatalystRules.encode(catalystLevel > 0 ? catalystTier : innateTier, sum);
        } else {
            level = raw + bonus + plainInnate;
        }
        return Math.min(table.enchantLevelCap(), level);
    }

    // ------------------------------------------------------------------------------------------------ innate table
    /** Innate traits of an item, as the checks and the tooltip read them. */
    public record Trait(ResourceKey<Enchantment> key, int level, boolean encoded) {}

    public static List<Trait> traitsOf(Item item) {
        Innate[] traits = innateTable().get(item);
        List<Trait> out = new ArrayList<>();
        if (traits != null) for (Innate t : traits) out.add(new Trait(t.key(), t.level(), t.encoded()));
        return out;
    }

    private static Map<Item, Innate[]> innateTable() {
        Map<Item, Innate[]> current = innate;
        if (current != null) return current;
        synchronized (EffectiveLevels.class) {
            if (innate != null) return innate;
            Map<Item, Map<ResourceKey<Enchantment>, Innate>> building = new IdentityHashMap<>();
            Map<String, GearData.Material> byTier = new HashMap<>();
            for (GearData.Material m : GearData.table().materials()) byTier.put(m.id(), m);
            for (CatalystData.Innate row : CatalystData.table().innate()) {
                GearData.Material m = byTier.get(row.tier());
                if (m == null) throw new IllegalStateException("innate.csv names unknown tier " + row.tier());
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(row.enchant()));
                boolean encoded = key.identifier().getNamespace().equals(Theoria.MODID);
                for (String id : itemsFor(m, row.host())) {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                    building.computeIfAbsent(item, i -> new LinkedHashMap<>()).merge(key, new Innate(key, row.level(), m.idx(), encoded),
                            (a, b) -> new Innate(key, Math.max(a.level(), b.level()), a.tier(), encoded));
                }
            }
            Map<Item, Innate[]> built = new IdentityHashMap<>();
            building.forEach((item, traits) -> built.put(item, traits.values().toArray(new Innate[0])));
            innate = built;
            return built;
        }
    }

    private static List<String> itemsFor(GearData.Material m, String host) {
        List<String> out = new ArrayList<>();
        switch (host) {
            case "armor" -> {
                if (m.armor() != null) for (String piece : GearData.ARMOR_PIECES) out.add(m.itemId(piece));
            }
            case "tool" -> {
                if (m.tools() != null) for (String kind : List.of("pickaxe", "shovel", "hoe", "axe")) out.add(m.itemId(kind));
            }
            case "weapon" -> {
                if (m.tools() != null) for (String kind : List.of("sword", "axe", "spear")) out.add(m.itemId(kind));
            }
            default -> throw new IllegalStateException("innate.csv: unknown host class " + host);
        }
        return out;
    }
}
