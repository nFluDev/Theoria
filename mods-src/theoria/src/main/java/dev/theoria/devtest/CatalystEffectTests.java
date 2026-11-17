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
 * Phase 7b GameTests, second half: Netherite, virtual and innate enchantments, Bilgelik, the temporary material sources and the tooltip text.
 * Same rules as {@link CatalystTests}: expected values from the CSVs the Python model writes. (Split in two files for docs/03 §6: no file over 600 lines.)
 */
final class CatalystEffectTests {
    private static final double EPS = 2.0e-3;

    private CatalystEffectTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("catalyst_netherite", CatalystEffectTests::netherite);
        add.accept("catalyst_virtual", CatalystEffectTests::virtual);
        add.accept("catalyst_erudition", CatalystEffectTests::erudition);
        add.accept("catalyst_loot", CatalystEffectTests::loot);
        add.accept("catalyst_tooltip", CatalystEffectTests::tooltip);
    }

    // shared helpers live in CatalystTests
    // ------------------------------------------------------------------------------------------------ Netherite
    private static void netherite(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_netherite");
        ServerLevel level = h.getLevel();
        Holder<Enchantment> sharp = CatalystTests.ench(level, Enchantments.SHARPNESS);
        Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        DamageSource source = level.damageSources().generic();

        // DoD: Sharpness V → VI with one Netherite ingot
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharp, 5);
        ItemStack ingot = new ItemStack(Items.NETHERITE_INGOT, 1);
        float before = EnchantmentHelper.modifyDamage(level, sword, zombie, source, 0f);
        CatalystRules.Plan first = CatalystRules.apply(sword, List.of(ingot));
        h.assertTrue(first.ok() && ingot.isEmpty() && first.gains().size() == 1, "one ingot upgrades Sharpness: " + first.problem());
        float after = EnchantmentHelper.modifyDamage(level, sword, zombie, source, 0f);
        h.assertValueEqual(sword.getEnchantmentLevel(sharp), 6, "Sharpness V + Netherite is effective VI");
        h.assertValueEqual(EnchantmentHelper.getTagEnchantmentLevel(sharp, sword), 5, "the component keeps V");
        h.assertTrue(Math.abs(before - 3.0f) < EPS && Math.abs(after - 3.5f) < EPS, "damage +3.0 → +3.5, got " + before + " → " + after);
        h.assertTrue(sword.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "Netherite alone does not set the catalyst glint flag");
        List<Component> tip = new ArrayList<>();
        tip.add(Enchantment.getFullname(sharp, 5));
        CatalystTooltip.boostEnchantLines(sword, tip);
        h.assertTrue(tip.get(0).getString().contains("6") || !tip.get(0).equals(Enchantment.getFullname(sharp, 5)), "tooltip line shows the boosted level: " + tip.get(0).getString());
        r.line("Sharpness V +Netherite: effective %d, damage %.2f → %.2f, tooltip '%s'", sword.getEnchantmentLevel(sharp), before, after, tip.get(0).getString());

        // costs 1, 2, 4, 6, 9 and at most five applications; Sharpness cap is 5 so five applications reach X
        String[] costs = Csv.keyValue("balance/catalyst_rules.csv").get("netherite_cost").split(" ");
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(sharp, 5);
        for (int n = 0; n < 5; n++) {
            int cost = Integer.parseInt(costs[n]);
            ItemStack short1 = new ItemStack(Items.NETHERITE_INGOT, cost - 1);
            if (cost > 1) {
                h.assertTrue(CatalystRules.apply(axe, List.of(short1)).problem() == CatalystRules.Problem.NETHERITE_INGOTS && short1.getCount() == cost - 1, "application " + (n + 1) + " needs " + cost + " ingots; one short is refused untouched");
            }
            ItemStack stack = new ItemStack(Items.NETHERITE_INGOT, cost + 3);
            CatalystRules.Plan plan = CatalystRules.apply(axe, List.of(stack));
            h.assertTrue(plan.ok() && stack.getCount() == 3, "application " + (n + 1) + " takes exactly " + cost + " ingots, left " + stack.getCount());
            h.assertValueEqual(axe.getEnchantmentLevel(sharp), 5 + n + 1, "Sharpness after " + (n + 1) + " applications");
        }
        h.assertTrue(CatalystRules.plan(axe, List.of(new ItemStack(Items.NETHERITE_INGOT, 64))).problem() == CatalystRules.Problem.NETHERITE_FULL, "a sixth application is refused");

        // per-enchantment ceilings (enchant_caps.csv): Protection +1 only, Lure/Mending/Silk never, unknown multi-level modded enchantment +1
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        Holder<Enchantment> protection = CatalystTests.ench(level, Enchantments.PROTECTION);
        chest.enchant(protection, 4);
        h.assertTrue(CatalystRules.apply(chest, List.of(new ItemStack(Items.NETHERITE_INGOT, 1))).ok() && chest.getEnchantmentLevel(protection) == 5, "Protection IV → V");
        h.assertTrue(CatalystRules.plan(chest, List.of(new ItemStack(Items.NETHERITE_INGOT, 2))).problem() == CatalystRules.Problem.NETHERITE_NO_GAIN, "Protection's ceiling is +1: a second application gains nothing and is refused");
        // Lure is not on any host item (a fishing rod carries no tier), so the ceiling is checked on the rule itself: VI would stop fish biting (ADR-004).
        h.assertValueEqual(CatalystRules.netheriteBonus(CatalystTests.ench(level, Enchantments.LURE), 3, 5), 0, "Lure never gains");
        h.assertValueEqual(CatalystRules.netheriteBonus(CatalystTests.ench(level, Enchantments.RIPTIDE), 3, 5), 0, "Riptide never gains");
        h.assertValueEqual(CatalystRules.netheriteBonus(CatalystTests.ench(level, Enchantments.FORTUNE), 3, 5), 2, "Fortune is capped at +2");
        ItemStack mended = new ItemStack(Items.DIAMOND_PICKAXE);
        mended.enchant(CatalystTests.ench(level, Enchantments.MENDING), 1);
        h.assertTrue(CatalystRules.plan(mended, List.of(new ItemStack(Items.NETHERITE_INGOT, 1))).problem() == CatalystRules.Problem.NETHERITE_NO_GAIN, "single-level Mending never gains");
        h.assertTrue(CatalystRules.plan(new ItemStack(Items.DIAMOND_SWORD), List.of(new ItemStack(Items.NETHERITE_INGOT, 1))).problem() == CatalystRules.Problem.NETHERITE_NO_ENCHANT, "no enchantments → refused");

        // the 255 ceiling and the "real enchantments only" rule
        ItemStack high = new ItemStack(Items.DIAMOND_SWORD);
        high.set(DataComponents.ENCHANTMENTS, new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY) {{ set(sharp, 255); }}.toImmutable());
        high.set(CatalystComponents.CATALYSTS.get(), new Catalysts(List.of(), 5));
        h.assertValueEqual(high.getEnchantmentLevel(sharp), 255, "effective level never exceeds 255");
        h.assertValueEqual(CatalystRules.netheriteBonus(sharp, 0, 5), 0, "no raw level, no bonus");

        // grindstone-like removal: the counter stays but a removed enchantment is simply gone (bonus follows the real enchantment)
        ItemStack stripped = new ItemStack(Items.DIAMOND_SWORD);
        stripped.enchant(sharp, 5);
        stripped.set(CatalystComponents.CATALYSTS.get(), new Catalysts(List.of(), 2));
        h.assertValueEqual(stripped.getEnchantmentLevel(sharp), 7, "two applications: V + 2");
        EnchantmentHelper.updateEnchantments(stripped, m -> m.removeIf(e -> true));
        h.assertValueEqual(stripped.getEnchantmentLevel(sharp), 0, "after the enchantment is removed the bonus is inert");
        EnchantmentHelper.updateEnchantments(stripped, m -> m.set(sharp, 3));
        h.assertValueEqual(stripped.getEnchantmentLevel(sharp), 5, "an enchantment added later is boosted too (counter, not a map)");

        // enchant_caps.csv ids exist (ADR-006 Karar 7)
        var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var row : Csv.read("balance/enchant_caps.csv")) {
            h.assertTrue(lookup.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(row.get("enchant")))).isPresent(), "enchant_caps.csv names an enchantment that is not in the registry: " + row.get("enchant"));
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ virtual enchantments and innate traits
    private static void virtual(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_virtual");
        ServerLevel level = h.getLevel();

        // encoding P = 1 + 5g + (L-1) for every element catalyst, every host tier it fits and every level, against the CSV value
        int cells = 0;
        for (CatalystData.Def def : CatalystData.table().defs().values()) {
            if (def.kind() != CatalystData.Kind.ENCHANT) continue;
            Holder<Enchantment> holder = CatalystTests.theoria(level, def.id());
            for (var host : CatalystHosts.all().entrySet()) {
                if (!host.getValue().accepts(def) || (!host.getValue().tierless() && host.getValue().idx() < def.minIdx())) continue;
                for (int lv = 1; lv <= 5; lv++) {
                    ItemStack stack = CatalystTests.withCatalysts(new ItemStack(host.getKey()), CatalystTests.slot(def.id(), lv));
                    int p = stack.getEnchantmentLevel(holder);
                    // an innate trait of the same enchantment (Orikalkum's Restoration II) adds its level to the catalyst's
                    int innateLevel = 0;
                    for (var trait : dev.theoria.catalyst.EffectiveLevels.traitsOf(host.getKey())) if (trait.key().equals(holder.unwrapKey().orElseThrow())) innateLevel = trait.level();
                    int expectedLevel = Math.min(5, lv + innateLevel);
                    h.assertValueEqual(p, CatalystRules.encode(host.getValue().idx(), expectedLevel), BuiltInRegistries.ITEM.getKey(host.getKey()) + " " + def.id() + " L" + lv);
                    h.assertTrue(p >= 1 && p <= 255, "P within the enchantment ceiling");
                    cells++;
                }
            }
        }
        r.line("%d (host, element catalyst, level) cells encode P correctly", cells);

        // Menzil Gücü through the real damage pipeline: a bow with the graded catalyst gains the CSV value for its grade (the datapack lookup is keyed by P = 1 + 5·grade + L − 1)
        Zombie target = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        int rangedCells = 0;
        for (int grade : new int[] {0, 1, 8, 13}) {
            for (int lv = 1; lv <= 5; lv++) {
                ItemStack bow = new ItemStack(grade % 2 == 0 ? Items.BOW : Items.CROSSBOW);
                bow.set(CatalystComponents.CATALYSTS.get(), new Catalysts(List.of(new Catalysts.Slot(Theoria.id("ranged_power"), lv, grade)), 0));
                float added = EnchantmentHelper.modifyDamage(level, bow, target, level.damageSources().generic(), 0f);
                double want = CatalystTests.value("ranged_power", "ranged", grade, lv);
                h.assertTrue(Math.abs(added - want) < EPS, "Menzil Gücü grade " + grade + " L" + lv + ": pipeline +" + added + ", csv " + want);
                rangedCells++;
            }
        }
        r.line("%d Menzil Gücü (grade, level) cells through EnchantmentHelper.modifyDamage agree with the CSV", rangedCells);

        // Netherite does not touch virtual levels; the real part is unaffected by catalysts
        ItemStack sword = CatalystTests.withCatalysts(new ItemStack(Items.DIAMOND_SWORD), CatalystTests.slot("frost", 3));
        sword.set(CatalystComponents.CATALYSTS.get(), new Catalysts(Catalysts.of(sword).slots(), 4));
        h.assertValueEqual(sword.getEnchantmentLevel(CatalystTests.theoria(level, "frost")), CatalystRules.encode(8, 3), "Netherite leaves a virtual P alone");

        // innate traits (innate.csv): every row yields the stated effective level on every item it names
        int innateChecks = 0;
        for (var row : Csv.read("balance/innate.csv")) {
            String tier = row.get("tier"), host = row.get("host"), enchant = row.get("enchant");
            int want = Integer.parseInt(row.get("level"));
            Holder<Enchantment> holder = CatalystTests.ench(level, ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(enchant)));
            List<Item> items = new ArrayList<>();
            for (var e : CatalystHosts.all().entrySet()) {
                CatalystHosts.Host hostRecord = e.getValue();
                if (hostRecord.tierless() || hostRecord.idx() != CatalystTests.idxOf(tier) || !hostRecord.isClass(host)) continue;
                String itemId = BuiltInRegistries.ITEM.getKey(e.getKey()).getPath();
                if (itemId.startsWith(CatalystTests.prefix(tier) + "_")) items.add(e.getKey());
            }
            if (items.isEmpty()) {
                // Bronze, Steel and Titanium are armor-only tiers (tools.csv has no row for them): a tool/weapon trait for them can never apply. Reported in docs/10.
                r.line("inert innate row (the tier has no %s items): %s %s %s", host, tier, host, enchant);
                h.assertTrue(List.of("bronze", "steel", "titanium").contains(tier) && !host.equals("armor"), "only armor-only tiers may have inert tool/weapon rows, but " + tier + "/" + host + " has no items");
                continue;
            }
            for (Item item : items) {
                int got = new ItemStack(item).getEnchantmentLevel(holder);
                int expected = enchant.startsWith("theoria:") ? CatalystRules.encode(CatalystTests.idxOf(tier), want) : want;
                h.assertValueEqual(got, expected, BuiltInRegistries.ITEM.getKey(item) + " innate " + enchant);
                innateChecks++;
            }
        }
        // an innate trait adds to a real enchantment and is not in the component (Gold: Looting I on the sword)
        ItemStack gold = new ItemStack(Items.GOLDEN_SWORD);
        Holder<Enchantment> looting = CatalystTests.ench(level, Enchantments.LOOTING);
        h.assertValueEqual(gold.getEnchantmentLevel(looting), 1, "golden sword: innate Looting I");
        gold.enchant(looting, 2);
        h.assertValueEqual(gold.getEnchantmentLevel(looting), 3, "innate Looting I + real II");
        h.assertValueEqual(EnchantmentHelper.getTagEnchantmentLevel(looting, gold), 2, "innate is never written to the component");
        h.assertTrue(new ItemStack(Items.GOLDEN_SWORD).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "innate traits do not glow");
        // full armor set: Steel Protection I x 4 = EPF 4 (= ref_epf(5)); the damage-protection computation sees it
        Zombie wearer = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        for (String piece : GearData.ARMOR_PIECES) {
            EquipmentSlot slot = switch (piece) { case "helmet" -> EquipmentSlot.HEAD; case "chestplate" -> EquipmentSlot.CHEST; case "leggings" -> EquipmentSlot.LEGS; default -> EquipmentSlot.FEET; };
            wearer.setItemSlot(slot, new ItemStack(CatalystTests.item("theoria:steel_" + piece)));
        }
        float epf = EnchantmentHelper.getDamageProtection(level, wearer, level.damageSources().generic());
        h.assertTrue(Math.abs(epf - 4.0f) < EPS, "a steel set protects as Protection I ×4 = EPF 4, got " + epf);
        // restoration (Orikalkum) is P-encoded with the host tier
        ItemStack orichalcum = new ItemStack(CatalystTests.item("theoria:orichalcum_pickaxe"));
        h.assertValueEqual(orichalcum.getEnchantmentLevel(CatalystTests.theoria(level, "restoration")), CatalystRules.encode(12, 2), "orichalcum pickaxe: innate Restoration II");
        // innate + catalyst of the same encoded enchantment add their LEVELS (II + III = V), capped at the catalyst level count
        CatalystTests.withCatalysts(orichalcum, CatalystTests.slot("restoration", 4));
        h.assertValueEqual(orichalcum.getEnchantmentLevel(CatalystTests.theoria(level, "restoration")), CatalystRules.encode(12, 5), "Restoration II (innate) + IV (catalyst) caps at V");
        r.line("%d innate item checks; steel set EPF %.1f", innateChecks, epf);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ Bilgelik
    private static void erudition(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_erudition");
        ServerLevel level = h.getLevel();
        Holder<Enchantment> erudition = CatalystTests.theoria(level, "erudition");

        // percentages: I-V from the table, VI = V + step
        int[] expected = {100, 300, 600, 1000, 1600, 2000};
        for (int lv = 1; lv <= 6; lv++) h.assertValueEqual(Erudition.percent(lv), expected[lv - 1], "Bilgelik " + lv + " percent");
        h.assertValueEqual(Erudition.scaled(10, 1600), 170, "10 XP at +1600% = 170");

        // real enchantment, catalyst, Netherite
        ItemStack real = new ItemStack(Items.DIAMOND_SWORD);
        real.enchant(erudition, 5);
        h.assertValueEqual(real.getEnchantmentLevel(erudition), 5, "real Bilgelik V");
        CatalystTests.withCatalysts(real, CatalystTests.slot("erudition", 5));
        real.set(CatalystComponents.CATALYSTS.get(), new Catalysts(Catalysts.of(real).slots(), 1));
        h.assertValueEqual(real.getEnchantmentLevel(erudition), 6, "real V + Netherite = VI (the bonus is the real part's)");
        ItemStack virtualOnly = CatalystTests.withCatalysts(new ItemStack(Items.DIAMOND_SWORD), CatalystTests.slot("erudition", 4));
        virtualOnly.set(CatalystComponents.CATALYSTS.get(), new Catalysts(Catalysts.of(virtualOnly).slots(), 1));
        h.assertValueEqual(virtualOnly.getEnchantmentLevel(erudition), 4, "a catalyst-only Bilgelik IV gets no Netherite bonus (nothing real to boost)");
        h.assertTrue(EnchantmentHelper.getTagEnchantmentLevel(erudition, virtualOnly) == 0, "catalyst Bilgelik is not in the component");
        // table cap says Netherite adds at most +1 to Bilgelik
        h.assertValueEqual(CatalystData.table().enchantCaps().get("theoria:erudition"), 1, "Netherite ceiling of Bilgelik");

        // summed over mainhand + 4 armor pieces, capped at 2400 %; offhand does not count
        ServerPlayer player = FakePlayers.survival(h, "dev-erudition");
        ItemStack hand = new ItemStack(Items.DIAMOND_SWORD);
        hand.enchant(erudition, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, hand);
        h.assertValueEqual(Erudition.totalPercent(player), 1600, "one piece: V = +1600 %");
        ItemStack cap = new ItemStack(Items.DIAMOND_HELMET);
        cap.enchant(erudition, 3);
        player.setItemSlot(EquipmentSlot.HEAD, cap);
        h.assertValueEqual(Erudition.totalPercent(player), 2200, "V + III = 1600 + 600 = 2200 (summed, not multiplied)");
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.enchant(erudition, 5);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        h.assertValueEqual(Erudition.totalPercent(player), 2400, "capped at +2400 %");
        ItemStack offhand = new ItemStack(Items.DIAMOND_SWORD);
        offhand.enchant(erudition, 5);
        player.setItemSlot(EquipmentSlot.OFFHAND, offhand);
        h.assertValueEqual(Erudition.totalPercent(player), 2400, "offhand is excluded (cap already reached either way)");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.assertValueEqual(Erudition.totalPercent(player), 0, "nothing worn, no bonus");
        player.setItemSlot(EquipmentSlot.MAINHAND, hand);

        // the two XP sources it touches: a kill and a broken block
        Zombie victim = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        LivingExperienceDropEvent kill = new LivingExperienceDropEvent(victim, player, 10);
        NeoForge.EVENT_BUS.post(kill);
        h.assertValueEqual(kill.getDroppedExperience(), 170, "10 XP kill with Bilgelik V: 10 × 17");
        LivingExperienceDropEvent accident = new LivingExperienceDropEvent(victim, null, 10);
        NeoForge.EVENT_BUS.post(accident);
        h.assertValueEqual(accident.getDroppedExperience(), 10, "no player killer, no bonus");
        BlockPos at = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(at, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        var drops = new net.neoforged.neoforge.event.level.BlockDropsEvent(level, at, level.getBlockState(at), null, new ArrayList<>(), player, new ItemStack(Items.IRON_PICKAXE));
        int vanillaXp = drops.getDroppedExperience();
        NeoForge.EVENT_BUS.post(drops);
        h.assertValueEqual(drops.getDroppedExperience(), Erudition.scaled(vanillaXp, 1600), "block XP scaled (diamond ore gave " + vanillaXp + ")");
        r.line("kill 10 → %d, diamond ore %d → %d, totals as designed", kill.getDroppedExperience(), vanillaXp, drops.getDroppedExperience());
        // not a source: the grindstone/XP bottle are never listened to (the only XP listeners are LivingExperienceDropEvent and BlockDropsEvent)
        FakePlayers.remove(player);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ material sources (temporary loot, ADR-006 Karar 11)
    private static void loot(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_loot");
        ServerLevel level = h.getLevel();
        var registries = level.getServer().reloadableRegistries();
        Vec3 origin = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        Item dust = CatalystItems.DUST.get(), essence = CatalystItems.ESSENCE.get(), core = CatalystItems.CORE.get();

        var diamondOre = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:blocks/diamond_ore")));
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(CatalystTests.ench(level, Enchantments.SILK_TOUCH), 1);
        int withPick = 0, withSilk = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack tool : List.of(pick, silk)) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin)
                        .withParameter(LootContextParams.BLOCK_STATE, Blocks.DIAMOND_ORE.defaultBlockState()).withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK);
                int count = 0;
                for (ItemStack s : diamondOre.getRandomItems(params)) if (s.is(dust)) count += s.getCount();
                if (tool == pick) withPick += count;
                else withSilk += count;
            }
        }
        r.line("diamond ore, 400 breaks: %d dust with an iron pickaxe, %d with silk touch", withPick, withSilk);
        h.assertTrue(withPick > 100 && withPick < 700, "dust from ore: 35% × 1-2 per break over 400 breaks, got " + withPick);
        h.assertValueEqual(withSilk, 0, "silk touch takes the ore block itself: no dust");

        Zombie dummy = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        int essences = 0, cores = 0, bossCores = 0;
        var zombieTable = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:entities/zombie")));
        var witherTable = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("minecraft:entities/wither")));
        for (int i = 0; i < 600; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, dummy).withParameter(LootContextParams.ORIGIN, origin)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
            for (ItemStack s : zombieTable.getRandomItems(params)) {
                if (s.is(essence)) essences += s.getCount();
                if (s.is(core)) cores += s.getCount();
            }
            if (i < 20) for (ItemStack s : witherTable.getRandomItems(params)) if (s.is(core)) bossCores += s.getCount();
        }
        r.line("zombie, 600 kills: %d essence, %d core; wither, 20 kills: %d core", essences, cores, bossCores);
        h.assertTrue(essences > 20 && essences < 200, "essence from a zombie (12 %): got " + essences);
        h.assertValueEqual(cores, 0, "a zombie never drops a core");
        h.assertTrue(bossCores >= 20, "a boss always drops 1-2 cores, got " + bossCores + " in 20");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ tooltip text (keys: the server has no translations)
    private static void tooltip(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_tooltip");
        // a catalyst item says what it fits, its minimum tier, its forge cost and where it is applied
        Set<String> keys = CatalystTests.keysOf(CatalystTooltip.forStack(CatalystTests.catalyst("crit_chance", 3)));
        for (String key : List.of("theoria.catalyst.tooltip.fits", "theoria.catalyst.tooltip.min_tier", "theoria.catalyst.tooltip.forge_cost", "theoria.catalyst.tooltip.applied_at", "theoria.catalyst.host.weapon", "theoria.catalyst.cost.dust")) {
            h.assertTrue(keys.contains(key), "catalyst tooltip lacks " + key + " (has " + keys + ")");
        }
        h.assertTrue(CatalystTests.keysOf(CatalystTooltip.forStack(CatalystTests.catalyst("frost", 1))).contains("theoria.catalyst.desc.frost"), "element catalysts carry their description");
        Set<String> graded = CatalystTests.keysOf(CatalystTooltip.forStack(CatalystItems.stack(CatalystTests.def("ranged_power"), 2, 8)));
        h.assertTrue(graded.contains("theoria.catalyst.tooltip.grade") && graded.contains("theoria.catalyst.tier.8"), "graded catalyst names its grade: " + graded);
        // a host shows slots, each catalyst with its value, the Netherite counter and innate traits
        ItemStack sword = CatalystTests.withCatalysts(new ItemStack(Items.GOLDEN_SWORD), CatalystTests.slot("attack_speed", 4));
        sword.set(CatalystComponents.CATALYSTS.get(), new Catalysts(Catalysts.of(sword).slots(), 2));
        List<Component> lines = CatalystTooltip.forStack(sword);
        Set<String> hostKeys = CatalystTests.keysOf(lines);
        h.assertTrue(hostKeys.contains("theoria.catalyst.tooltip.slots") && hostKeys.contains("theoria.catalyst.tooltip.netherite") && hostKeys.contains("theoria.catalyst.tooltip.innate"), "host tooltip keys: " + hostKeys);
        boolean valueShown = CatalystTooltip.plain(lines).stream().anyMatch(l -> l.contains("+0.32") || l.contains("+0,32"));
        h.assertTrue(valueShown, "the slot line shows the live value (+0.32): " + CatalystTooltip.plain(lines));
        // the forge preview: green plan, or a red problem
        ItemStack target = new ItemStack(Items.DIAMOND_SWORD);
        List<Component> ok = CatalystTooltip.preview(target, List.of(CatalystTests.catalyst("attack_speed", 4)));
        h.assertTrue(CatalystTests.keysOf(ok).contains("theoria.catalyst.line.add"), "preview of a valid application: " + CatalystTests.keysOf(ok));
        List<Component> refused = CatalystTooltip.preview(new ItemStack(Items.STONE_SWORD), List.of(CatalystTests.catalyst("crit_chance", 1)));
        h.assertTrue(CatalystTests.keysOf(refused).contains(CatalystRules.Problem.TIER_TOO_LOW.langKey()), "preview of a refused application names the reason: " + CatalystTests.keysOf(refused));
        List<Component> forging = CatalystTooltip.preview(ItemStack.EMPTY, List.of(new ItemStack(Items.BLAZE_POWDER, 1), new ItemStack(CatalystItems.DUST.get(), 3)));
        h.assertTrue(CatalystTests.keysOf(forging).contains("theoria.catalyst.line.make"), "preview of a forge: " + CatalystTests.keysOf(forging));
        r.line("tooltip lines for the golden sword: %s", CatalystTooltip.plain(lines));
        r.write();
        h.succeed();
    }
}
