package dev.theoria.relics;

import dev.theoria.core.check.Checks;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Area "relic" of {@code /theoria test}: {@code relic/table} (the table as loaded: every relic is an item in its Curios tag, nothing is orphaned), {@code relic/loot} (every source's
 * loot table really rolls its relics) and ONE CHECK PER RELIC, named by its id: {@code /theoria test relic night_vision_ring}. The per-relic check is {@link RelicProbe}: it wears the relic
 * and proves the effect; the command's player is the wearer (run from a console without a player, only the data part runs and the result says so).
 */
public final class RelicChecks {
    /** Rolls per source in {@code relic/loot}: enough for the rarest relic of the rarest source to show up (≈ 50 expected). */
    static final int ROLLS = 4000;
    /** The design size of the set (docs/07 Faz 12: "~25 relics"). */
    public static final int MIN_RELICS = 24;

    private RelicChecks() {}

    public static void init() {
        Checks.register("relic", "table", source -> table());
        Checks.register("relic", "loot", source -> loot(source.getLevel()));
        for (RelicData.Def def : RelicData.all()) {
            Checks.register("relic", def.id(), source -> {
                ServerPlayer player = source.getPlayer();
                if (player == null) return RelicProbe.data(def) + "; live part skipped (no player — run it in game)";
                return RelicProbe.run(player, def);
            });
        }
    }

    static String table() {
        RelicData.Table table = RelicData.table();
        Checks.require(table.relics().size() >= MIN_RELICS, table.relics().size() + " relics, the phase promises about 25");
        Map<String, Integer> perSlot = new HashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (RelicData.Def def : table.relics()) {
            Checks.require(ids.add(def.id()), "relic " + def.id() + " appears twice");
            Checks.require(RelicItems.RELICS.containsKey(def.id()), "relic " + def.id() + " has no item");
            RelicProbe.data(def);
            perSlot.merge(def.slot(), 1, Integer::sum);
        }
        for (RelicData.SourceDef source : table.sources()) {
            Checks.require(table.relics().stream().anyMatch(d -> d.sources().stream().anyMatch(s -> s.source().equals(source.id()))), "source " + source.id() + " drops no relic");
        }
        long legendary = table.relics().stream().filter(d -> d.rarity().equals("legendary")).count();
        return table.relics().size() + " relics in " + perSlot.size() + " slot types " + new java.util.TreeMap<>(perSlot) + ", " + legendary + " legendary, " + table.sources().size() + " sources";
    }

    /** Rolls each source's relic table {@link #ROLLS} times: every relic that lists the source must come out at least once, nothing else may. */
    static String loot(ServerLevel level) {
        int total = 0;
        for (RelicData.SourceDef source : RelicData.table().sources()) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("theoria", "relics/" + source.id())));
            LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
            Map<String, Integer> seen = new HashMap<>();
            RandomSource random = RandomSource.create(20261120L + source.id().hashCode()); // ONE source for all rolls: sequential seeds of an LCG give correlated first draws
            for (int i = 0; i < ROLLS; i++) {
                for (ItemStack stack : table.getRandomItems(params, random)) {
                    if (stack.getItem() instanceof RelicItem relic) seen.merge(relic.def().id(), 1, Integer::sum);
                    else Checks.require(false, "source " + source.id() + " rolled a non-relic: " + stack);
                }
            }
            for (RelicData.Def def : RelicData.all()) {
                boolean listed = def.sources().stream().anyMatch(s -> s.source().equals(source.id()));
                Checks.require(listed == seen.containsKey(def.id()), (listed ? def.id() + " never came out of " : def.id() + " came out of ") + source.id() + " in " + ROLLS + " rolls");
            }
            total += seen.values().stream().mapToInt(Integer::intValue).sum();
        }
        return RelicData.table().sources().size() + " sources × " + ROLLS + " rolls, " + total + " relics rolled, each only from the sources it lists";
    }
}
