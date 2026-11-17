package dev.theoria.devtest;

import dev.theoria.Theoria;
import dev.theoria.core.check.Checks;
import dev.theoria.gear.GearData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 6 GameTests. Expected values come from the CSVs ({@code data/balance/{tiers,tools,ores}.csv}, {@code data/*.csv}), never from
 * {@code theoria/gear.json}: the point is that the generated table, the registered items and the loaded datapack all agree with the single source.
 */
final class GearTests {
    private static final double EPS = 1.0e-3;

    private GearTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("gear_checks", GearTests::checks);
        add.accept("gear_ids", GearTests::ids);
        add.accept("gear_armor_csv", GearTests::armorCsv);
        add.accept("gear_tools_csv", GearTests::toolsCsv);
        add.accept("gear_gate_matrix", GearTests::gateMatrix);
        add.accept("gear_mining", GearTests::mining);
        add.accept("gear_innate", GearTests::innate);
        add.accept("gear_loot", GearTests::loot);
        add.accept("gear_recipes", GearTests::recipes);
        add.accept("gear_worldgen", GearTests::worldgen);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static Path dataDir() {
        return Path.of(System.getProperty("theoria.dev.data", "data/balance")).toAbsolutePath().getParent();
    }

    private static List<Map<String, String>> csv(String relative) {
        try {
            List<String> lines = Files.readAllLines(dataDir().resolve(relative));
            String[] header = lines.get(0).split(",", -1);
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue;
                List<String> cells = new ArrayList<>(); // minimal RFC-4180: quoted cells may contain commas
                StringBuilder cell = new StringBuilder();
                boolean quoted = false;
                for (char c : line.toCharArray()) {
                    if (c == '"') quoted = !quoted;
                    else if (c == ',' && !quoted) { cells.add(cell.toString()); cell.setLength(0); }
                    else cell.append(c);
                }
                cells.add(cell.toString());
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) row.put(header[i], i < cells.size() ? cells.get(i) : "");
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + relative, e);
        }
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("missing item " + id);
        return item;
    }

    private static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null || block == Blocks.AIR) throw new IllegalStateException("missing block " + id);
        return block;
    }

    private static double attr(Item item, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers mods = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        double total = 0;
        if (mods != null) for (var e : mods.modifiers()) if (e.attribute().equals(attribute)) total += e.modifier().amount();
        return total;
    }

    private static String vanillaPrefix(String id) {
        return switch (id) { case "wood" -> "wooden"; case "gold" -> "golden"; default -> id; };
    }

    private static boolean isVanilla(String id) {
        return List.of("wood", "stone", "leather", "copper", "iron", "gold", "diamond").contains(id);
    }

    private static String itemId(String material, String piece) {
        return (isVanilla(material) ? "minecraft:" + vanillaPrefix(material) : "theoria:" + material) + "_" + piece;
    }

    // ------------------------------------------------------------------------------------------------ /theoria test gear
    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_checks");
        var results = Checks.run("gear", h.getLevel().getServer().createCommandSourceStack());
        for (var res : results) r.line("%s %s — %s", res.ok() ? "PASS" : "FAIL", res.name(), res.detail());
        r.write();
        h.assertTrue(!results.isEmpty(), "no gear checks registered");
        for (var res : results) h.assertTrue(res.ok(), res.name() + ": " + res.detail());
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ D6: no new ids for vanilla materials
    private static void ids(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_ids");
        List<String> vanilla = List.of("wooden", "stone", "leather", "copper", "iron", "golden", "diamond", "netherite", "chainmail");
        int ours = 0;
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(Theoria.MODID)) continue;
            ours++;
            if (id.getPath().endsWith("_spawn_egg")) continue;   // Faz 8: a species may start with a material word (stone_guardian); eggs are not gear
            for (String v : vanilla) h.assertTrue(!id.getPath().startsWith(v + "_"), "theoria:" + id.getPath() + " duplicates a vanilla material id (D6: yeni id yok)");
        }
        for (Map<String, String> m : csv("materials.csv")) {
            boolean vanillaRow = m.get("vanilla").equals("1");
            Item sample = item(itemId(m.get("id"), m.get("armor").equals("1") ? "helmet" : "pickaxe"));
            h.assertTrue(BuiltInRegistries.ITEM.getKey(sample).getNamespace().equals(vanillaRow ? "minecraft" : Theoria.MODID), m.get("id") + " namespace");
        }
        r.line("%d theoria items, none reuses a vanilla material prefix; every vanilla material row resolves to a minecraft: item", ours);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ armor = tiers.csv
    private static void armorCsv(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_armor_csv");
        int sets = 0;
        for (Map<String, String> t : csv("balance/tiers.csv")) {
            String id = t.get("id");
            double armorTotal = 0, tough = 0;
            Map<String, String> columns = Map.of("helmet", "helm", "chestplate", "chest", "leggings", "legs", "boots", "boots");
            for (var piece : columns.entrySet()) {
                Item item = item(itemId(id, piece.getKey()));
                double points = attr(item, Attributes.ARMOR);
                h.assertTrue(points == Double.parseDouble(t.get(piece.getValue())), id + " " + piece.getKey() + " armor " + points + " ≠ " + t.get(piece.getValue()));
                armorTotal += points;
                tough += attr(item, Attributes.ARMOR_TOUGHNESS);
                int unit = switch (piece.getKey()) { case "helmet" -> 11; case "chestplate" -> 16; case "leggings" -> 15; default -> 13; };
                Integer durability = item.components().get(DataComponents.MAX_DAMAGE);
                int expected = (int) Math.round(Double.parseDouble(t.get("adur")) * unit / 16.0);
                h.assertTrue(durability != null && durability == expected, id + " " + piece.getKey() + " durability " + durability + " ≠ " + expected);
            }
            h.assertTrue(armorTotal == Double.parseDouble(t.get("total")), id + " set armor");
            h.assertTrue(Math.abs(tough - Double.parseDouble(t.get("tough"))) < EPS, id + " set toughness " + tough);
            r.line("%-11s set armor %3.0f toughness %4.1f chestplate durability %s", id, armorTotal, tough, t.get("adur"));
            sets++;
        }
        h.assertValueEqual(sets, 14, "armor tiers");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ tools = tools.csv
    private static void toolsCsv(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_tools_csv");
        int checked = 0;
        for (Map<String, String> row : csv("balance/tools.csv")) {
            String tool = row.get("tool"), kind = row.get("kind");
            Item item = item(itemId(tool, kind));
            double damage = 1.0 + attr(item, Attributes.ATTACK_DAMAGE);
            double speed = 4.0 + attr(item, Attributes.ATTACK_SPEED);
            Integer durability = item.components().get(DataComponents.MAX_DAMAGE);
            h.assertTrue(Math.abs(damage - Double.parseDouble(row.get("damage"))) < EPS, tool + " " + kind + " damage " + damage + " ≠ " + row.get("damage"));
            h.assertTrue(Math.abs(speed - Double.parseDouble(row.get("speed"))) < 2e-3, tool + " " + kind + " speed " + speed + " ≠ " + row.get("speed"));
            h.assertTrue(durability != null && durability == Integer.parseInt(row.get("durability")), tool + " " + kind + " durability " + durability);
            if (!row.get("mining_speed").isEmpty()) {
                Tool component = item.components().get(DataComponents.TOOL);
                h.assertTrue(component != null, tool + " " + kind + " has no tool component");
                float mining = component.rules().stream().filter(x -> x.speed().isPresent() && x.correctForDrops().orElse(false)).findFirst().orElseThrow().speed().get();
                h.assertTrue(Math.abs(mining - Double.parseDouble(row.get("mining_speed"))) < EPS, tool + " " + kind + " mining speed " + mining + " ≠ " + row.get("mining_speed"));
            }
            checked++;
        }
        r.line("%d tool/weapon items equal tools.csv (damage total, attack speed, durability, mining speed)", checked);
        r.write();
        h.assertValueEqual(checked, 66, "tool rows");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ gates (independent of gear.json: ores.csv × tools.csv)
    private static void gateMatrix(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_gate_matrix");
        List<Map<String, String>> tools = csv("balance/tools.csv");
        Map<String, Integer> toolIdx = new HashMap<>();
        for (Map<String, String> t : tools) toolIdx.put(t.get("tool"), Integer.parseInt(t.get("idx")));
        int pairs = 0, drops = 0;
        for (Map<String, String> ore : csv("balance/ores.csv")) {
            int need = Integer.parseInt(ore.get("tool_idx"));
            for (String id : ore.get("blocks").split(" ")) {
                BlockState state = block(id).defaultBlockState();
                for (var tool : toolIdx.entrySet()) {
                    Tool component = item(itemId(tool.getKey(), "pickaxe")).components().get(DataComponents.TOOL);
                    boolean expected = tool.getValue() >= need;
                    h.assertTrue(component.isCorrectForDrops(state) == expected,
                            tool.getKey() + " pickaxe on " + id + " (needs idx " + need + "): drops=" + component.isCorrectForDrops(state) + ", expected " + expected);
                    pairs++;
                    if (expected) drops++;
                }
            }
        }
        r.line("%d (pickaxe × gated block) pairs, %d drop, %d are denied", pairs, drops, pairs - drops);
        // docs/05 §7 "Sonuç": elmasa kobalt, platine orikalkum kazma lazım
        Tool cobalt = item("theoria:cobalt_pickaxe").components().get(DataComponents.TOOL);
        Tool iron = item("minecraft:iron_pickaxe").components().get(DataComponents.TOOL);
        Tool orichalcum = item("theoria:orichalcum_pickaxe").components().get(DataComponents.TOOL);
        Tool mithril = item("theoria:mithril_pickaxe").components().get(DataComponents.TOOL);
        h.assertTrue(cobalt.isCorrectForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) && !iron.isCorrectForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "diamond ore needs cobalt");
        h.assertTrue(orichalcum.isCorrectForDrops(block("theoria:deepslate_platinum_ore").defaultBlockState()) && !mithril.isCorrectForDrops(block("theoria:deepslate_platinum_ore").defaultBlockState()), "platinum needs orichalcum");
        h.assertTrue(Items.IRON_PICKAXE.components().get(DataComponents.TOOL).isCorrectForDrops(Blocks.REDSTONE_ORE.defaultBlockState()) , "iron pickaxe mines redstone");
        h.assertTrue(item("minecraft:copper_pickaxe").components().get(DataComponents.TOOL).isCorrectForDrops(Blocks.REDSTONE_ORE.defaultBlockState()), "copper pickaxe mines redstone (removed from needs_iron_tool)");
        h.assertTrue(!item("minecraft:stone_pickaxe").components().get(DataComponents.TOOL).isCorrectForDrops(Blocks.IRON_ORE.defaultBlockState()), "stone pickaxe cannot mine iron ore");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ real destroy progress through a survival player
    private static void mining(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_mining");
        ServerPlayer player = FakePlayers.survival(h, "dev-gear-mining");
        try {
            BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
            for (String[] c : new String[][] {{"minecraft:iron_ore", "minecraft:copper_pickaxe", "minecraft:wooden_pickaxe"},
                    {"theoria:tin_ore", "minecraft:stone_pickaxe", "minecraft:wooden_pickaxe"},
                    {"minecraft:diamond_ore", "theoria:cobalt_pickaxe", "minecraft:iron_pickaxe"},
                    {"theoria:deepslate_platinum_ore", "theoria:orichalcum_pickaxe", "theoria:mithril_pickaxe"}}) {
                BlockState state = block(c[0]).defaultBlockState();
                float hardness = state.getDestroySpeed(h.getLevel(), pos);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(c[1])));
                boolean correct = player.hasCorrectToolForDrops(state);
                float progressRight = state.getDestroyProgress(player, h.getLevel(), pos);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(c[2])));
                boolean wrong = player.hasCorrectToolForDrops(state);
                float progressWrong = state.getDestroyProgress(player, h.getLevel(), pos);
                h.assertTrue(correct && !wrong, c[0] + ": " + c[1] + " must be right, " + c[2] + " wrong");
                r.line("%-34s hardness %5.2f | %-28s %.4f/tick (%.1f ticks) | %-28s %.4f/tick (%.1f ticks)", c[0], hardness, c[1], progressRight,
                        1 / progressRight, c[2], progressWrong, 1 / progressWrong);
                h.assertTrue(progressRight > 0 && progressWrong > 0, "positive progress");
                h.assertTrue(progressRight > progressWrong, c[0] + ": the right tool must be faster than the wrong one");
            }
            // the penalty is vanilla's: 30 with the right tool, 100 without (progress = speed / hardness / penalty)
            BlockState iron = Blocks.IRON_ORE.defaultBlockState();
            float hardness = iron.getDestroySpeed(h.getLevel(), pos);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_PICKAXE));
            float expected = player.getDestroySpeed(iron) / hardness / 30.0F;
            h.assertTrue(Math.abs(iron.getDestroyProgress(player, h.getLevel(), pos) - expected) < 1e-6, "destroy progress = speed / hardness / 30");
            h.assertTrue(Math.abs(hardness - 3.6F) < 1e-4, "iron ore hardness is 3.6 (docs/05 §7), got " + hardness);
            r.write();
            h.succeed();
        } finally {
            FakePlayers.remove(player);
        }
    }

    // ------------------------------------------------------------------------------------------------ innate trait modifiers (attribute-type only; enchant-like traits → Faz 7)
    private static void innate(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_innate");
        Object[][] cases = {{"leather", 0.02, 0.0}, {"mithril", 0.04, 0.0}, {"titanium", 0.0, 0.2}, {"iron", 0.0, 0.0}};
        Zombie[] zombies = new Zombie[cases.length];
        double[] base = new double[cases.length], kbBase = new double[cases.length];
        for (int i = 0; i < cases.length; i++) {
            zombies[i] = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1 + i % 3, 1, 1 + i / 3));
            base[i] = zombies[i].getAttributeValue(Attributes.MOVEMENT_SPEED);
            kbBase[i] = zombies[i].getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            for (var slot : List.of(Map.entry(EquipmentSlot.HEAD, "helmet"), Map.entry(EquipmentSlot.CHEST, "chestplate"),
                    Map.entry(EquipmentSlot.LEGS, "leggings"), Map.entry(EquipmentSlot.FEET, "boots"))) {
                zombies[i].setItemSlot(slot.getKey(), new ItemStack(item(itemId((String) cases[i][0], slot.getValue()))));
            }
        }
        // equipment attribute modifiers are applied by the entity's own tick (detectEquipmentUpdates), not by setItemSlot
        h.runAfterDelay(3, () -> {
            for (int i = 0; i < cases.length; i++) {
                String m = (String) cases[i][0];
                double speed = zombies[i].getAttributeValue(Attributes.MOVEMENT_SPEED) / base[i] - 1.0;
                double kb = zombies[i].getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - kbBase[i];
                r.line("%-9s set speed %+.4f (expected %+.2f), knockback resistance %+.3f (expected %+.2f)", m, speed, cases[i][1], kb, cases[i][2]);
                h.assertTrue(Math.abs(speed - (double) cases[i][1]) < 1e-4, m + " speed bonus " + speed);
                h.assertTrue(Math.abs(kb - (double) cases[i][2]) < 1e-4, m + " knockback resistance " + kb);
            }
            r.write();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ loot: ore tables, carbon dust modifier, netherite filter
    private static void loot(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_loot");
        ServerLevel level = h.getLevel();
        Vec3 origin = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        var registries = level.getServer().reloadableRegistries();
        // ore tables: the tool decides — iron pickaxe → raw ore; silk touch → the ore block
        for (String[] c : new String[][] {{"tin_ore", "raw_tin"}, {"deepslate_cobalt_ore", "raw_cobalt"}, {"lonsdaleit_ore", "lonsdaleit"}}) {
            var table = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Theoria.id("blocks/" + c[0])));
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin)
                    .withParameter(LootContextParams.BLOCK_STATE, block("theoria:" + c[0]).defaultBlockState())
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_PICKAXE)).create(LootContextParamSets.BLOCK);
            // Phase 7b's catalyst-dust modifier adds a chance of dust to every ore table: the ore's own drop is what this test is about.
            var drops = table.getRandomItems(params).stream().filter(s -> !s.is(item("theoria:catalyst_dust"))).toList();
            h.assertTrue(drops.size() == 1 && drops.get(0).is(item("theoria:" + c[1])), c[0] + " should drop " + c[1] + ", got " + drops);
            r.line("%-22s → %s", c[0], drops.get(0));
        }
        // carbon dust: 5 mobs × many rolls; chance 0.5 → certain to appear in 200 rolls
        Zombie dummy = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        int dust = 0;
        var skeletonTable = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:entities/skeleton")));
        for (int i = 0; i < 200; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, dummy).withParameter(LootContextParams.ORIGIN, origin)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
            for (ItemStack s : skeletonTable.getRandomItems(params)) if (s.is(item("theoria:carbon_dust"))) dust += s.getCount();
        }
        r.line("skeleton loot, 200 rolls: %d carbon dust (global loot modifier)", dust);
        h.assertTrue(dust > 0, "carbon dust never dropped from the skeleton table — loot modifier not applied");
        // netherite gear never comes out of bastion chests
        int netherite = 0, stacks = 0;
        for (String chest : List.of("bastion_treasure", "bastion_other", "bastion_bridge", "bastion_hoglin_stable")) {
            var table = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:chests/" + chest)));
            for (int i = 0; i < 300; i++) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
                for (ItemStack s : table.getRandomItems(params, RandomSource.create(i))) {
                    stacks++;
                    String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                    if (GearData.table().removed().contains(id)) netherite++;
                }
            }
        }
        r.line("bastion chests, 1200 rolls: %d stacks, %d netherite gear/template stacks", stacks, netherite);
        h.assertTrue(stacks > 0, "bastion tables rolled nothing");
        h.assertTrue(netherite == 0, "netherite gear still comes out of bastion chests (" + netherite + ")");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ recipes
    private static void recipes(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_recipes");
        ServerLevel level = h.getLevel();
        var manager = level.getServer().getRecipeManager();
        List<String> expected = new ArrayList<>();
        for (Map<String, String> m : csv("materials.csv")) {
            if (m.get("vanilla").equals("1")) continue;
            if (m.get("armor").equals("1")) for (String p : List.of("helmet", "chestplate", "leggings", "boots")) expected.add(m.get("id") + "_" + p);
            if (m.get("tools").equals("1")) for (String p : List.of("sword", "axe", "pickaxe", "shovel", "hoe", "spear")) expected.add(m.get("id") + "_" + p);
        }
        for (Map<String, String> a : csv("alloys.csv")) expected.add(a.get("id"));
        for (String id : expected) h.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, Theoria.id(id))).isPresent(), "recipe theoria:" + id + " missing");
        // D6: netherite upgrades gone, the netherite ingot recipes stay (universal catalyst)
        for (String n : List.of("sword", "axe", "pickaxe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings", "boots", "horse_armor", "nautilus_armor")) {
            h.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:netherite_" + n + "_smithing"))).isEmpty(), "netherite_" + n + "_smithing still loaded");
        }
        h.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:netherite_ingot"))).isPresent(), "netherite_ingot recipe must stay");
        // real crafting: a cobalt sword, bronze (3 copper + tin → 4), steel (2 iron + dust → 2)
        craft(h, r, level, 3, 3, items(Items.AIR, item("theoria:cobalt_ingot"), Items.AIR, Items.AIR, item("theoria:cobalt_ingot"), Items.AIR, Items.AIR, Items.STICK, Items.AIR), "theoria:cobalt_sword", 1);
        craft(h, r, level, 2, 2, items(Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT, item("theoria:tin_ingot")), "theoria:bronze_ingot", 4);
        craft(h, r, level, 2, 2, items(Items.IRON_INGOT, Items.IRON_INGOT, item("theoria:carbon_dust"), Items.AIR), "theoria:steel_ingot", 2);
        craft(h, r, level, 2, 2, items(Items.DIAMOND, item("theoria:carbon_dust"), item("theoria:carbon_dust"), Items.AIR), "theoria:lonsdaleit", 1);
        r.line("%d recipes present, netherite upgrades absent, 4 crafts verified", expected.size());
        r.write();
        h.succeed();
    }

    private static List<ItemStack> items(Item... items) {
        List<ItemStack> out = new ArrayList<>();
        for (Item i : items) out.add(new ItemStack(i));
        return out;
    }

    private static void craft(GameTestHelper h, Dev.Report r, ServerLevel level, int w, int hgt, List<ItemStack> grid, String result, int count) {
        CraftingInput input = CraftingInput.of(w, hgt, grid);
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        h.assertTrue(recipe.isPresent(), "no crafting recipe for " + result);
        ItemStack out = recipe.get().value().assemble(input);
        h.assertTrue(out.is(item(result)) && out.getCount() == count, result + " x" + count + " expected, got " + out);
        r.line("craft → %s x%d (%s)", result, out.getCount(), recipe.get().id().identifier());
    }

    // ------------------------------------------------------------------------------------------------ ore generation
    private static void worldgen(GameTestHelper h) {
        Dev.Report r = new Dev.Report("gear_worldgen");
        ServerLevel level = h.getLevel();
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        var placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        List<Map<String, String>> ores = csv("ores.csv");
        BlockPos base = h.absolutePos(new BlockPos(0, 70, 0)); // far above the test floor: nobody else builds here
        for (Map<String, String> ore : ores) {
            String id = ore.get("id");
            var featureKey = ResourceKey.create(Registries.FEATURE, Theoria.id("ore_" + id));
            var placedKey = ResourceKey.create(Registries.PLACED_FEATURE, Theoria.id("ore_" + id));
            h.assertTrue(features.get(featureKey).isPresent(), "feature theoria:ore_" + id + " missing");
            h.assertTrue(placed.get(placedKey).isPresent(), "placed feature theoria:ore_" + id + " missing");
            boolean everywhere = true;
            for (var biome : biomes.listElements().toList()) {
                if (biome.is(net.minecraft.tags.BiomeTags.IS_OVERWORLD) && biome.value().getGenerationSettings().features().stream().noneMatch(set -> set.stream().anyMatch(p -> p.is(placedKey)))) everywhere = false;
            }
            h.assertTrue(everywhere, "every overworld biome should carry theoria:ore_" + id + " (biome modifier)");
            // place the real feature into a stone/deepslate block and count the ore blocks it makes
            Block host = ore.get("variants").contains("stone") ? Blocks.STONE : Blocks.DEEPSLATE;
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -8, -8), base.offset(8, 8, 8))) level.setBlock(p, host.defaultBlockState(), 2);
            RandomSource random = RandomSource.create(7);
            int made = 0;
            for (int i = 0; i < 60 && made == 0; i++) {
                features.getValueOrThrow(featureKey).place(level, level.getChunkSource().getGenerator(), random, base);
                for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -8, -8), base.offset(8, 8, 8))) {
                    if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getNamespace().equals(Theoria.MODID)) made++;
                }
            }
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-8, -8, -8), base.offset(8, 8, 8))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            r.line("%-11s feature ok, placed in every overworld biome, %d ore blocks from a test placement (size %s, y %s…%s)", id, made, ore.get("size"), ore.get("min_y"), ore.get("max_y"));
            h.assertTrue(made > 0, "theoria:ore_" + id + " placed no ore blocks");
        }
        r.write();
        h.succeed();
    }
}
