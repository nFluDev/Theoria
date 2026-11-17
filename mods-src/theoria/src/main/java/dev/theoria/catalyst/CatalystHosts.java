package dev.theoria.catalyst;

import dev.theoria.gear.GearData;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Which items can carry catalysts and at which tier (ADR-006 Karar 1). The host table is derived from the gear table ({@code gear.json}: every material's
 * armor pieces and tools, vanilla ones included) plus the tierless ranged items of the catalyst table; an item with no known tier is not a host (a tier is what
 * gives it slots and values). Built lazily on first use — after registries are frozen — and immutable afterwards.
 */
public final class CatalystHosts {
    /** {@code idx} is the material tier (0-13), or the table's tierless idx for bow-like items; {@code armor} is head/chest/legs/feet or null. */
    public record Host(int idx, boolean tierless, boolean weapon, boolean tool, @Nullable String armor) {
        /** Targets of {@code def} this host can use: "weapon"/"tool"/"ranged"/an armor slot (an axe is both weapon and tool). Empty = the catalyst does not fit. */
        public List<String> matching(CatalystData.Def def) {
            List<String> out = new ArrayList<>(2);
            if (weapon && def.targets().contains("weapon")) out.add("weapon");
            if (tool && def.targets().contains("tool")) out.add("tool");
            if (tierless && def.targets().contains("ranged")) out.add("ranged");
            if (armor != null && def.targets().contains(armor)) out.add(armor);
            return out;
        }

        public boolean accepts(CatalystData.Def def) {
            return !matching(def).isEmpty();
        }

        /** The host class used in innate.csv and the forge UI: weapon / tool / armor / ranged. */
        public boolean isClass(String hostClass) {
            return switch (hostClass) {
                case "weapon" -> weapon;
                case "tool" -> tool;
                case "armor" -> armor != null;
                case "ranged" -> tierless;
                default -> false;
            };
        }
    }

    private static final Map<String, String> ARMOR_SLOT = Map.of("helmet", "head", "chestplate", "chest", "leggings", "legs", "boots", "feet");
    private static volatile @Nullable Map<Item, Host> hosts;

    private CatalystHosts() {}

    public static @Nullable Host of(ItemStack stack) {
        return of(stack.getItem());
    }

    public static @Nullable Host of(Item item) {
        return table().get(item);
    }

    /** Every host item with its record, for checks and creative previews. */
    public static Map<Item, Host> all() {
        return table();
    }

    private static Map<Item, Host> table() {
        Map<Item, Host> current = hosts;
        if (current != null) return current;
        synchronized (CatalystHosts.class) {
            if (hosts != null) return hosts;
            Map<Item, Host> built = new IdentityHashMap<>();
            for (GearData.Material m : GearData.table().materials()) {
                if (m.armor() != null) {
                    for (String piece : GearData.ARMOR_PIECES) put(built, m.itemId(piece), new Host(m.idx(), false, false, false, ARMOR_SLOT.get(piece)));
                }
                if (m.tools() != null) {
                    for (String kind : GearData.TOOL_KINDS) {
                        boolean weapon = kind.equals("sword") || kind.equals("axe") || kind.equals("spear");
                        boolean tool = !kind.equals("sword") && !kind.equals("spear");
                        put(built, m.itemId(kind), new Host(m.idx(), false, weapon, tool, null));
                    }
                }
            }
            CatalystData.Table t = CatalystData.table();
            for (String id : t.tierlessItems()) put(built, id, new Host(t.tierlessIdx(), true, false, false, null));
            hosts = built; // never mutated after this line
            return built;
        }
    }

    private static void put(Map<Item, Host> map, String id, Host host) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("catalyst host " + id + " is not a registered item");
        map.put(item, host);
    }
}
