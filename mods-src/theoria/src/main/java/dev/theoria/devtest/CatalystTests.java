package dev.theoria.devtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.theoria.Theoria;
import dev.theoria.catalyst.CatalystComponents;
import dev.theoria.catalyst.CatalystData;
import dev.theoria.catalyst.CatalystHosts;
import dev.theoria.catalyst.CatalystItems;
import dev.theoria.catalyst.CatalystRules;
import dev.theoria.catalyst.CatalystTooltip;
import dev.theoria.catalyst.Catalysts;
import dev.theoria.catalyst.Erudition;
import dev.theoria.core.check.Checks;
import dev.theoria.gear.GearData;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import org.apache.commons.lang3.mutable.MutableFloat;

/**
 * Phase 7b GameTests (after the platform proof {@code enchant_effective_level}). Expected values come from the CSVs the Python model writes
 * ({@code data/balance/catalyst*.csv, enchant_caps.csv, innate.csv}), never from {@code catalysts/default.json}: the point is that the generated table, the
 * registered items and the running listeners agree with the single source. Entity/menu scenarios are in {@link CatalystCombatTests}.
 */
final class CatalystTests {
    private static final double EPS = 2.0e-3;

    private CatalystTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("catalyst_checks", CatalystTests::checks);
        add.accept("catalyst_data", CatalystTests::data);
        add.accept("catalyst_component", CatalystTests::component);
        add.accept("catalyst_example", CatalystTests::example);
        add.accept("catalyst_attributes", CatalystTests::attributes);
        CatalystEffectTests.register(add);
        CatalystCombatTests.register(add);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static CatalystData.Def def(String id) {
        return CatalystData.table().defs().get(id);
    }

    static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("missing item " + id);
        return item;
    }

    static ItemStack catalyst(String id, int level) {
        return CatalystItems.stack(def(id), level, 0);
    }

    static Holder<Enchantment> ench(ServerLevel level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    static Holder<Enchantment> theoria(ServerLevel level, String id) {
        return ench(level, ResourceKey.create(Registries.ENCHANTMENT, Theoria.id(id)));
    }

    static ItemStack withCatalysts(ItemStack stack, Catalysts.Slot... slots) {
        stack.set(CatalystComponents.CATALYSTS.get(), new Catalysts(List.of(slots), 0));
        return stack;
    }

    static Catalysts.Slot slot(String id, int level) {
        return new Catalysts.Slot(Theoria.id(id), level, 0);
    }

    static double csvDouble(String text) {
        return Double.parseDouble(text);
    }

    static int idxOf(String tierId) {
        for (var row : Csv.read("balance/tiers.csv")) if (row.get("id").equals(tierId)) return Integer.parseInt(row.get("idx"));
        throw new IllegalStateException("unknown tier " + tierId);
    }

    /** Sum of the modifiers a stack contributes to {@code slot} for attribute {@code id}, restricted to the catalyst modifiers (id prefix theoria:catalyst/). */
    static double catalystModifier(ItemStack stack, EquipmentSlotGroup slot, String attributeId) {
        double[] total = {0};
        stack.forEachModifier(slot, (attribute, modifier, display) -> {
            if (attribute.unwrapKey().map(k -> k.identifier().toString()).orElse("").equals(attributeId) && modifier.id().getPath().startsWith("catalyst/")) total[0] += modifier.amount();
        });
        return total[0];
    }

    static void keys(Component component, Set<String> out) {
        if (component.getContents() instanceof TranslatableContents t) {
            out.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof Component c) keys(c, out);
        }
        for (Component sibling : component.getSiblings()) keys(sibling, out);
    }

    static Set<String> keysOf(List<Component> lines) {
        Set<String> out = new HashSet<>();
        lines.forEach(l -> keys(l, out));
        return out;
    }

    private static JsonObject langFile(String name) {
        try (InputStream in = CatalystTests.class.getResourceAsStream("/assets/theoria/lang/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------ /theoria test catalysts
    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_checks");
        var results = Checks.run("catalysts", h.getLevel().getServer().createCommandSourceStack());
        for (var res : results) r.line("%s %s — %s", res.ok() ? "PASS" : "FAIL", res.name(), res.detail());
        r.write();
        h.assertTrue(!results.isEmpty(), "no catalyst checks registered");
        for (var res : results) h.assertTrue(res.ok(), res.name() + ": " + res.detail());
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the generated table, host table, lang
    private static void data(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_data");
        CatalystData.Table t = CatalystData.table();
        ServerLevel level = h.getLevel();

        // catalysts.csv ↔ table (order, kind, targets, min_idx, signature)
        var rows = Csv.read("balance/catalysts.csv");
        h.assertValueEqual(t.defs().size(), rows.size(), "catalyst count (catalysts.csv)");
        int i = 0;
        for (var row : rows) {
            CatalystData.Def def = (CatalystData.Def) t.defs().values().toArray()[i++];
            h.assertValueEqual(def.id(), row.get("id"), "catalyst order");
            h.assertValueEqual(def.minIdx(), Integer.parseInt(row.get("min_idx")), def.id() + " min_idx");
            h.assertValueEqual(def.signature(), row.get("signature"), def.id() + " signature");
            h.assertValueEqual(def.targets(), Set.copyOf(List.of(row.get("targets").split(" "))), def.id() + " targets");
            h.assertValueEqual(def.cap(), csvDouble(row.get("cap")), def.id() + " cap");
        }
        Set<String> signatures = new HashSet<>();
        t.defs().values().forEach(d -> signatures.add(d.signature()));
        h.assertValueEqual(signatures.size(), t.defs().size(), "signature materials must be unique (they select the catalyst in the forge)");

        // catalyst_rules.csv ↔ table
        Map<String, String> rules = Csv.keyValue("balance/catalyst_rules.csv");
        h.assertValueEqual(t.levels(), Integer.parseInt(rules.get("levels")), "levels");
        h.assertValueEqual(t.slotBase(), Integer.parseInt(rules.get("slot_base")), "slot_base");
        h.assertValueEqual(t.slotStep(), Integer.parseInt(rules.get("slot_step")), "slot_step");
        h.assertValueEqual(t.slotMax(), Integer.parseInt(rules.get("slot_max")), "slot_max");
        h.assertValueEqual(t.platinumIdx(), Integer.parseInt(rules.get("platinum_idx")), "platinum_idx");
        h.assertValueEqual(t.tierlessSlots(), Integer.parseInt(rules.get("tierless_slots")), "tierless_slots");
        String[] cost = rules.get("netherite_cost").split(" ");
        for (int k = 0; k < cost.length; k++) h.assertValueEqual(t.netheriteCost()[k], Integer.parseInt(cost[k]), "netherite_cost[" + k + "]");
        h.assertValueEqual(t.netheriteMax(), Integer.parseInt(rules.get("netherite_max")), "netherite_max");
        h.assertValueEqual(t.eruditionTotalCap(), Integer.parseInt(rules.get("erudition_total_cap")), "erudition_total_cap");
        for (int lv = 1; lv <= 5; lv++) {
            String[] parts = rules.get("cost_l" + lv).split(" ");
            CatalystData.Ladder step = t.ladder().get(lv - 1);
            h.assertTrue(step.dust() == Integer.parseInt(parts[0]) && step.signature() == Integer.parseInt(parts[1])
                    && step.essence() == Integer.parseInt(parts[2]) && step.core() == Integer.parseInt(parts[3]), "ladder level " + lv);
        }

        // catalyst_values.csv ↔ table (all cells)
        var values = Csv.read("balance/catalyst_values.csv");
        int cells = 0;
        for (var row : values) {
            for (int lv = 1; lv <= 5; lv++) {
                double expected = csvDouble(row.get("l" + lv));
                double actual = t.value(row.get("id"), row.get("target"), Integer.parseInt(row.get("idx")), lv);
                h.assertTrue(Math.abs(expected - actual) < 1.0e-3, row.get("id") + "/" + row.get("target") + " T" + row.get("idx") + " L" + lv + ": csv " + expected + " vs table " + actual);
                cells++;
            }
        }
        long javaRows = t.values().values().stream().flatMap(m -> m.values().stream()).flatMap(java.util.Arrays::stream).filter(java.util.Objects::nonNull).count();
        h.assertValueEqual((int) javaRows, values.size(), "value rows in the table vs catalyst_values.csv");

        // enchant_caps.csv, innate.csv ↔ table
        var caps = Csv.read("balance/enchant_caps.csv");
        h.assertValueEqual(t.enchantCaps().size(), caps.size(), "enchant_caps rows");
        for (var row : caps) h.assertValueEqual(t.enchantCaps().get(row.get("enchant")), Integer.parseInt(row.get("netherite_cap")), "cap of " + row.get("enchant"));
        var innate = Csv.read("balance/innate.csv");
        h.assertValueEqual(t.innate().size(), innate.size(), "innate rows");

        // host table ↔ tiers.csv / tools.csv
        int hostItems = 0;
        for (var row : Csv.read("balance/tools.csv")) {
            String prefix = prefix(row.get("tool"));
            String id = (isVanilla(row.get("tool")) ? "minecraft:" : "theoria:") + prefix + "_" + row.get("kind");
            CatalystHosts.Host host = CatalystHosts.of(item(id));
            h.assertTrue(host != null && host.idx() == Integer.parseInt(row.get("idx")), id + " should be a host at tier " + row.get("idx") + ", got " + host);
            hostItems++;
        }
        for (var row : Csv.read("balance/tiers.csv")) {
            for (String piece : GearData.ARMOR_PIECES) {
                String id = (isVanilla(row.get("id")) ? "minecraft:" : "theoria:") + prefix(row.get("id")) + "_" + piece;
                CatalystHosts.Host host = CatalystHosts.of(item(id));
                h.assertTrue(host != null && host.idx() == Integer.parseInt(row.get("idx")) && host.armor() != null, id + " should be an armor host at tier " + row.get("idx"));
                hostItems++;
            }
        }
        h.assertValueEqual(CatalystHosts.all().size(), hostItems + t.tierlessItems().size(), "host table size = tools + armor + tierless");

        // lang: every name the code asks for exists in both languages
        for (String lang : List.of("en_us", "tr_tr")) {
            JsonObject file = langFile(lang);
            List<String> wanted = new ArrayList<>(List.of("itemGroup.theoria.catalysts", "block.theoria.catalyst_forge", "container.theoria.catalyst_forge",
                    "item.theoria.catalyst_dust", "item.theoria.catalyst_essence", "item.theoria.catalyst_core"));
            for (CatalystData.Def def : t.defs().values()) {
                if (def.kind() == CatalystData.Kind.NETHERITE) continue;
                wanted.add(def.nameKey());
                if (def.kind() != CatalystData.Kind.ATTRIBUTE) wanted.add("theoria.catalyst.desc." + def.id());
                if (def.enchantment() != null) wanted.add("enchantment.theoria." + def.id());
            }
            for (CatalystRules.Problem p : CatalystRules.Problem.values()) wanted.add(p.langKey());
            for (dev.theoria.catalyst.CatalystForging.Problem p : dev.theoria.catalyst.CatalystForging.Problem.values()) wanted.add(p.langKey());
            for (int idx = 0; idx < 14; idx++) wanted.add("theoria.catalyst.tier." + idx);
            for (String host : List.of("weapon", "tool", "ranged", "head", "chest", "legs", "feet")) wanted.add("theoria.catalyst.host." + host);
            for (String name : List.of("crit_chance", "crit_damage", "life_steal", "thorns", "food_saturation")) wanted.add("attribute.name.theoria." + name);
            for (String ui : List.of("slots", "slot.empty", "slot.locked", "apply", "forge", "remove", "preview.title", "preview.nothing", "line.add", "line.upgrade", "line.netherite",
                    "line.make", "line.cost", "line.next", "cost.dust", "cost.essence", "cost.core", "cost.signature", "tooltip.fits", "tooltip.min_tier", "tooltip.grade",
                    "tooltip.applied_at", "tooltip.forge_cost", "tooltip.slots", "tooltip.innate", "tooltip.netherite", "tooltip.raw_material")) wanted.add("theoria.catalyst." + ui);
            for (String key : wanted) h.assertTrue(file.has(key), lang + " lang file lacks " + key);
            r.line("%s: %d keys checked", lang, wanted.size());
        }

        // the datapack enchantments exist, Bilgelik is a real enchantment, Menzil Gücü's lookup agrees with the CSV
        for (CatalystData.Def def : t.defs().values()) if (def.enchantment() != null) h.assertTrue(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(def.enchantment()).isPresent(), "enchantment " + def.id());
        Holder<Enchantment> ranged = theoria(level, "ranged_power");
        Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        DamageSource source = level.damageSources().generic();
        int lookupCells = 0;
        for (var row : values) {
            if (!row.get("id").equals("ranged_power")) continue;
            for (int lv = 1; lv <= 5; lv++) {
                MutableFloat out = new MutableFloat(0f);
                ranged.value().modifyDamage(level, CatalystRules.encode(Integer.parseInt(row.get("idx")), lv), new ItemStack(Items.BOW), zombie, source, out);
                h.assertTrue(Math.abs(out.floatValue() - csvDouble(row.get("l" + lv))) < 2.0e-3, "ranged_power lookup at T" + row.get("idx") + " L" + lv + ": " + out.floatValue() + " vs " + row.get("l" + lv));
                lookupCells++;
            }
        }
        r.line("%d value cells, %d host items (+%d tierless), %d ranged_power lookup cells agree with the CSVs", cells, hostItems, t.tierlessItems().size(), lookupCells);
        r.write();
        h.succeed();
    }

    static boolean isVanilla(String material) {
        return List.of("wood", "stone", "leather", "copper", "iron", "gold", "diamond").contains(material);
    }

    static String prefix(String material) {
        return switch (material) { case "wood" -> "wooden"; case "gold" -> "golden"; default -> material; };
    }

    // ------------------------------------------------------------------------------------------------ component: codec, sync, persistence, atomic rules
    private static void component(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_component");
        ServerLevel level = h.getLevel();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());

        ItemStack sword = withCatalysts(new ItemStack(Items.DIAMOND_SWORD), slot("attack_speed", 4), new Catalysts.Slot(Theoria.id("ranged_power"), 2, 6));
        sword.set(CatalystComponents.CATALYSTS.get(), new Catalysts(sword.get(CatalystComponents.CATALYSTS.get()).slots(), 3));
        JsonElement saved = ItemStack.CODEC.encodeStart(ops, sword).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        h.assertValueEqual(Catalysts.of(loaded), Catalysts.of(sword), "component after JSON save/load");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sword);
        ItemStack synced = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        h.assertValueEqual(Catalysts.of(synced), Catalysts.of(sword), "component after the network round trip (networkSynchronized)");
        r.line("save/load and sync keep %s", Catalysts.of(sword));

        // invalid records fail the codec (the stack then loads without the component, the world is not corrupted)
        String[] bad = {
                "{\"slots\":[{\"id\":\"theoria:nope\",\"level\":1}]}",
                "{\"slots\":[{\"id\":\"theoria:attack_speed\",\"level\":9}]}",
                "{\"slots\":[{\"id\":\"theoria:attack_speed\",\"level\":1},{\"id\":\"theoria:attack_speed\",\"level\":2}]}",
                "{\"slots\":[{\"id\":\"theoria:attack_speed\",\"level\":1,\"grade\":3}]}",
                "{\"netherite\":6}"};
        for (String json : bad) h.assertTrue(Catalysts.CODEC.parse(ops, JsonParser.parseString(json)).isError(), "codec should reject " + json);
        h.assertTrue(Catalysts.CODEC.parse(ops, JsonParser.parseString("{\"slots\":[{\"id\":\"theoria:ranged_power\",\"level\":2,\"grade\":6}]}")).isSuccess(), "graded catalyst with a grade is valid");
        r.line("%d invalid records rejected", bad.length);

        // slot counts of real items against the formula in catalyst_rules.csv (T0-T13) and the tierless 3
        Map<String, String> rules = Csv.keyValue("balance/catalyst_rules.csv");
        int base = Integer.parseInt(rules.get("slot_base")), step = Integer.parseInt(rules.get("slot_step")), max = Integer.parseInt(rules.get("slot_max"));
        int platinumIdx = Integer.parseInt(rules.get("platinum_idx")), platinumSlots = Integer.parseInt(rules.get("platinum_slots"));
        for (var host : CatalystHosts.all().values()) {
            int expected = host.tierless() ? Integer.parseInt(rules.get("tierless_slots")) : Math.min(max, base + host.idx() / step) + (host.idx() >= platinumIdx ? platinumSlots : 0);
            h.assertValueEqual(CatalystRules.slotCount(host), expected, "slot count at tier " + host.idx());
        }

        // eligibility gates (min_idx, class), and atomic failure: nothing consumed, nothing changed
        ItemStack stoneSword = new ItemStack(Items.STONE_SWORD); // tier 1: Critical Chance needs 2
        ItemStack crit = catalyst("crit_chance", 1);
        CatalystRules.Plan tooLow = CatalystRules.apply(stoneSword, List.of(crit));
        h.assertTrue(tooLow.problem() == CatalystRules.Problem.TIER_TOO_LOW && crit.getCount() == 1 && stoneSword.get(CatalystComponents.CATALYSTS.get()) == null, "min_idx gate: " + tooLow.problem());
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack power = catalyst("power", 1); // weapon-only
        CatalystRules.Plan wrong = CatalystRules.apply(helmet, List.of(power));
        h.assertTrue(wrong.problem() == CatalystRules.Problem.WRONG_CLASS && power.getCount() == 1, "class gate: " + wrong.problem());
        ItemStack rock = new ItemStack(Items.COBBLESTONE);
        h.assertTrue(CatalystRules.plan(new ItemStack(Items.STICK), List.of(power)).problem() == CatalystRules.Problem.NOT_HOST, "a stick is not a host");
        h.assertTrue(CatalystRules.plan(new ItemStack(Items.IRON_SWORD), List.of(rock)).problem() == CatalystRules.Problem.NOT_A_CATALYST, "a stone is not a catalyst");

        // iron sword: 2 slots. Two fit; the third is refused and the whole application (even the valid first two) is undone.
        ItemStack iron = new ItemStack(Items.IRON_SWORD);
        ItemStack a = catalyst("attack_speed", 2), b = catalyst("power", 2), c = catalyst("frost", 1);
        CatalystRules.Plan third = CatalystRules.apply(iron, List.of(a, b, c));
        h.assertTrue(third.problem() == CatalystRules.Problem.NO_SLOT, "third catalyst on an iron sword: " + third.problem());
        h.assertTrue(a.getCount() == 1 && b.getCount() == 1 && c.getCount() == 1 && iron.get(CatalystComponents.CATALYSTS.get()) == null, "a refused application must consume and change nothing");
        CatalystRules.Plan two = CatalystRules.apply(iron, List.of(a, b));
        h.assertTrue(two.ok() && a.isEmpty() && b.isEmpty(), "two catalysts fit an iron sword: " + two.problem());
        h.assertTrue(Boolean.TRUE.equals(iron.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "a catalyst item glows (glint override)");

        // same catalyst: only a better one replaces
        ItemStack equal = catalyst("attack_speed", 2), better = catalyst("attack_speed", 3);
        h.assertTrue(CatalystRules.plan(iron, List.of(equal)).problem() == CatalystRules.Problem.NOT_BETTER, "an equal level is refused");
        CatalystRules.Plan upgrade = CatalystRules.apply(iron, List.of(better));
        h.assertTrue(upgrade.ok() && Catalysts.of(iron).slots().size() == 2 && Catalysts.of(iron).slots().get(0).level() == 3 && upgrade.changes().get(0).oldLevel() == 2, "level 3 replaces level 2 in place");
        h.assertTrue(CatalystRules.remove(iron, 0) && Catalysts.of(iron).slots().size() == 1, "taking a catalyst out frees the slot");
        h.assertTrue(CatalystRules.remove(iron, 0) && iron.get(CatalystComponents.CATALYSTS.get()) == null && iron.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "an emptied item drops the component and the glint flag");

        // ranged hosts take the graded catalyst; the grade is part of "better"
        ItemStack bow = new ItemStack(Items.BOW);
        ItemStack rp1 = CatalystItems.stack(def("ranged_power"), 2, 3);
        ItemStack rp2 = CatalystItems.stack(def("ranged_power"), 2, 6);
        h.assertTrue(CatalystRules.apply(bow, List.of(rp1)).ok(), "a bow takes Menzil Gücü");
        h.assertTrue(CatalystRules.apply(bow, List.of(rp2)).ok() && Catalysts.of(bow).slots().get(0).grade() == 6, "a higher grade at the same level replaces");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the DoD example: Hız IV + Kritik Şansı III + Buz II
    private static void example(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_example");
        ServerLevel level = h.getLevel();
        // T8 diamond sword (3 slots). The DoD text says "iron sword", which has 2 slots (docs/10 B9); the iron case is the slot test above.
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack speed = catalyst("attack_speed", 4), crit = catalyst("crit_chance", 3), frost = catalyst("frost", 2);
        CatalystRules.Plan plan = CatalystRules.apply(sword, List.of(speed, crit, frost));
        h.assertTrue(plan.ok(), "Hız IV + Kritik Şansı III + Buz II must fit a diamond sword: " + plan.problem());
        h.assertTrue(speed.isEmpty() && crit.isEmpty() && frost.isEmpty(), "the three catalysts are consumed");
        Catalysts applied = Catalysts.of(sword);
        h.assertValueEqual(applied.slots().size(), 3, "three slots filled");

        // component survives save/load
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        ItemStack reloaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, sword).getOrThrow()).getOrThrow();
        h.assertValueEqual(Catalysts.of(reloaded), applied, "save/load keeps the catalysts");

        // expected numbers from the CSV (Python model), measured on the real stack
        double wantSpeed = value("attack_speed", "weapon", 8, 4), wantCrit = value("crit_chance", "weapon", 8, 3), wantFrost = value("frost", "weapon", 8, 2);
        double gotSpeed = catalystModifier(reloaded, EquipmentSlotGroup.MAINHAND, "minecraft:attack_speed");
        double gotCrit = catalystModifier(reloaded, EquipmentSlotGroup.MAINHAND, "theoria:crit_chance");
        h.assertTrue(Math.abs(gotSpeed - wantSpeed) < EPS, "attack speed modifier " + gotSpeed + " vs csv " + wantSpeed);
        h.assertTrue(Math.abs(gotCrit - wantCrit) < EPS, "crit chance modifier " + gotCrit + " vs csv " + wantCrit);
        Holder<Enchantment> frostHolder = theoria(level, "frost");
        int p = reloaded.getEnchantmentLevel(frostHolder);
        h.assertValueEqual(p, CatalystRules.encode(8, 2), "Frost II on a T8 host is virtual enchantment level P = 1 + 5·8 + 1");
        h.assertTrue(Math.abs(CatalystData.table().value("frost", "weapon", CatalystRules.decodeTier(p), CatalystRules.decodeLevel(p)) - wantFrost) < EPS, "frost value via P");
        h.assertTrue(EnchantmentHelper.getTagEnchantmentLevel(frostHolder, reloaded) == 0, "the virtual enchantment is not in the real component");
        h.assertTrue(reloaded.getTagEnchantments().isEmpty(), "nothing is written to the vanilla enchantments component");

        // expected DPS multiplier (tools/balance/catalysts.py example: ×1.34) from the live numbers
        double sword8 = swordDamage(8);
        double mult = (1 + wantFrost / sword8) * (1 + gotSpeed / 1.6) * (1 + gotCrit * 0.5);
        r.line("T8 diamond sword (%.1f dmg): speed %+.2f, crit chance %.2f, frost %.2f per hit → DPS ×%.3f", sword8, gotSpeed, gotCrit, wantFrost, mult);
        h.assertTrue(mult > 1.30 && mult < 1.38, "expected DPS ×1.34 (catalysts.py --report), got ×" + mult);
        r.write();
        h.succeed();
    }

    static double value(String id, String target, int idx, int level) {
        for (var row : Csv.read("balance/catalyst_values.csv")) {
            if (row.get("id").equals(id) && row.get("target").equals(target) && Integer.parseInt(row.get("idx")) == idx) return csvDouble(row.get("l" + level));
        }
        throw new IllegalStateException("no csv row " + id + "/" + target + "/T" + idx);
    }

    static double swordDamage(int idx) {
        for (var row : Csv.read("balance/tiers.csv")) if (Integer.parseInt(row.get("idx")) == idx) return csvDouble(row.get("sword"));
        throw new IllegalStateException("tier " + idx);
    }

    // ------------------------------------------------------------------------------------------------ every attribute catalyst × every host × every level, measured on real stacks
    private static void attributes(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_attributes");
        CatalystData.Table t = CatalystData.table();
        int checked = 0;
        for (Map.Entry<Item, CatalystHosts.Host> entry : CatalystHosts.all().entrySet()) {
            Item hostItem = entry.getKey();
            CatalystHosts.Host host = entry.getValue();
            for (CatalystData.Def def : t.defs().values()) {
                if (def.kind() != CatalystData.Kind.ATTRIBUTE) continue;
                List<String> targets = host.matching(def);
                if (targets.isEmpty() || (!host.tierless() && host.idx() < def.minIdx())) continue;
                for (int lv = 1; lv <= 5; lv++) {
                    ItemStack stack = withCatalysts(new ItemStack(hostItem), slot(def.id(), lv));
                    for (String target : targets) {
                        String attribute = def.effects().get(target);
                        EquipmentSlotGroup group = switch (target) { case "head" -> EquipmentSlotGroup.HEAD; case "chest" -> EquipmentSlotGroup.CHEST; case "legs" -> EquipmentSlotGroup.LEGS;
                            case "feet" -> EquipmentSlotGroup.FEET; default -> EquipmentSlotGroup.MAINHAND; };
                        double got = catalystModifier(stack, group, attribute);
                        double want = value(def.id(), target, host.idx(), lv);
                        h.assertTrue(Math.abs(got - want) < EPS, BuiltInRegistries.ITEM.getKey(hostItem) + " " + def.id() + " L" + lv + " " + attribute + ": item gives " + got + ", csv " + want);
                        checked++;
                    }
                }
            }
        }
        // a host with no catalysts contributes nothing (the listener returns on the first line)
        h.assertTrue(catalystModifier(new ItemStack(Items.DIAMOND_SWORD), EquipmentSlotGroup.MAINHAND, "minecraft:attack_speed") == 0.0, "a plain sword has no catalyst modifier");
        r.line("%d (host, catalyst, level, target) cells: the attribute an item reports equals catalyst_values.csv", checked);
        r.write();
        h.assertTrue(checked > 1000, "expected thousands of cells, checked " + checked);
        h.succeed();
    }
}
