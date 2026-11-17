package dev.theoria.devtest;

import dev.theoria.Theoria;
import dev.theoria.balance.BalanceMath;
import dev.theoria.balance.BalanceProbe;
import dev.theoria.balance.CombatParams;
import dev.theoria.balance.MobScaling;
import dev.theoria.balance.TechnicalCaps;
import dev.theoria.balance.Tier;
import dev.theoria.balance.TierRegistry;
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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 2b GameTests: the balance core measured against the simulator ({@code data/balance/sim_cases.csv}), plus the
 * pipeline details ADR-003 left as [kaynak]/[çıkarım]: Breach, i-frames, damage-source paths, difficulty order, save/load.
 * Tolerance is {@link #EXACT} (float rounding only) — the ±10 % of the Phase 2 criterion is {@code /theoria test balance}.
 */
final class BalanceTests {
    /** The formulas are mirrored one to one; only float32 rounding separates game and simulator. */
    private static final double EXACT = 0.003;
    private static final double TOL = 1.0e-3;

    private BalanceTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("balance_data", BalanceTests::data);
        add.accept("balance_attribute_caps", BalanceTests::attributeCaps);
        add.accept("balance_sim_cases_mob", BalanceTests::simCasesMob);
        add.accept("balance_sim_cases_player", BalanceTests::simCasesPlayer);
        add.accept("balance_mob_scaling", BalanceTests::mobScaling);
        add.accept("balance_kill_hits", BalanceTests::killHits);
        add.accept("balance_mob_damage_paths", BalanceTests::mobDamagePaths);
        add.accept("balance_difficulty_order", BalanceTests::difficultyOrder);
        add.accept("balance_heal_scaling", BalanceTests::healScaling);
        add.accept("balance_breach", BalanceTests::breach);
        add.accept("balance_iframes", BalanceTests::iFrames);
        add.accept("balance_command", BalanceTests::command);
    }

    // ------------------------------------------------------------ data: datapack JSON == CSV (the single source)
    private static void data(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_data");
        Path dir = Path.of(System.getProperty("theoria.dev.data", "data/balance"));
        Map<String, String> combat = new HashMap<>();
        for (List<String> row : csv(dir.resolve("combat.csv"))) combat.put(row.get(0), row.get(1));
        CombatParams c = TierRegistry.combat();
        h.assertTrue(Math.abs(c.armorK1() - Double.parseDouble(combat.get("armor_k1"))) < 1e-9, "armor_k1 datapack ≠ combat.csv");
        h.assertTrue(Math.abs(c.armorK2() - Double.parseDouble(combat.get("armor_k2"))) < 1e-9, "armor_k2 datapack ≠ combat.csv");
        h.assertTrue(Math.abs(c.armorCap() - Double.parseDouble(combat.get("armor_cap"))) < 1e-9, "armor_cap datapack ≠ combat.csv");
        h.assertTrue(Math.abs(c.toughnessHalf() - Double.parseDouble(combat.get("toughness_half"))) < 1e-9, "toughness_half datapack ≠ combat.csv");
        h.assertTrue(Math.abs(c.enchScale() - Double.parseDouble(combat.get("ench_scale"))) < 1e-9, "ench_scale datapack ≠ combat.csv");
        TechnicalCaps.Caps caps = TechnicalCaps.caps();
        h.assertTrue(caps.armor() == Double.parseDouble(combat.get("attr_max_armor")), "technical armor cap ≠ combat.csv");
        h.assertTrue(caps.armorToughness() == Double.parseDouble(combat.get("attr_max_armor_toughness")), "technical toughness cap ≠ combat.csv");
        h.assertTrue(caps.health() == Double.parseDouble(combat.get("attr_max_health")), "technical health cap ≠ combat.csv");
        h.assertTrue(caps.attackDamage() == Double.parseDouble(combat.get("attr_max_attack_damage")), "technical attack cap ≠ combat.csv");

        List<List<String>> rows = csv(dir.resolve("tiers.csv"));
        List<String> header = rows.remove(0);
        h.assertValueEqual(TierRegistry.tierCount(), rows.size(), "tier count (datapack vs tiers.csv)");
        for (List<String> row : rows) {
            Tier t = TierRegistry.tier(Integer.parseInt(row.get(header.indexOf("idx"))));
            h.assertValueEqual(t.id(), row.get(header.indexOf("id")), "tier id");
            h.assertValueEqual(t.armor().total(), Integer.parseInt(row.get(header.indexOf("total"))), t.id() + " armor total");
            h.assertValueEqual(t.armor().chestplate(), Integer.parseInt(row.get(header.indexOf("chest"))), t.id() + " chestplate");
            h.assertValueEqual(t.armorDurability(), Integer.parseInt(row.get(header.indexOf("adur"))), t.id() + " armor durability");
            h.assertTrue(near(t.toughness(), num(row, header, "tough")), t.id() + " toughness");
            h.assertTrue(near(t.swordDamage(), num(row, header, "sword")), t.id() + " sword");
            h.assertTrue(near(t.mob().hpMult(), num(row, header, "mob_hp_mult")), t.id() + " mob_hp_mult");
            h.assertTrue(near(t.mob().dmgMult(), num(row, header, "mob_dmg_mult")), t.id() + " mob_dmg_mult");
            h.assertTrue(near(t.mob().bypassMult(), num(row, header, "mob_bypass_mult")), t.id() + " mob_bypass_mult");
            h.assertTrue(near(t.ref().health(), num(row, header, "exphp")), t.id() + " ref health");
            h.assertTrue(near(t.ref().bonus(), num(row, header, "ref_bonus")), t.id() + " ref bonus");
            h.assertTrue(near(t.ref().epf(), num(row, header, "ref_epf")), t.id() + " ref epf");
        }
        r.line("datapack JSON matches data/balance/{combat,tiers}.csv: %d tiers, 5 combat constants, 4 technical caps", rows.size());
        r.line("tier 0 = vanilla multipliers: hp x%s dmg x%s bypass x%s", TierRegistry.tier(0).mob().hpMult(),
                TierRegistry.tier(0).mob().dmgMult(), TierRegistry.tier(0).mob().bypassMult());
        r.write();
        h.assertTrue(TierRegistry.tier(0).mob().hpMult() == 1.0 && TierRegistry.tier(0).mob().dmgMult() == 1.0, "tier 0 must be vanilla");
        h.succeed();
    }

    // ------------------------------------------------------------ attribute ceilings (ADR-003 Karar 2)
    private static void attributeCaps(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_attribute_caps");
        TechnicalCaps.Caps caps = TechnicalCaps.caps();
        h.assertTrue(max(Attributes.ARMOR) >= caps.armor(), "armor ceiling not lifted by the mod constructor");
        h.assertTrue(max(Attributes.ARMOR_TOUGHNESS) >= caps.armorToughness(), "toughness ceiling not lifted");
        h.assertTrue(max(Attributes.MAX_HEALTH) >= caps.health(), "max health ceiling not lifted");
        h.assertTrue(max(Attributes.ATTACK_DAMAGE) >= caps.attackDamage(), "attack damage ceiling not lifted");
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        z.getAttribute(Attributes.ARMOR).setBaseValue(300);
        z.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(500);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4000);
        // gear works through modifiers, not base values (docs/04 §3)
        Zombie gear = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        gear.getAttribute(Attributes.ARMOR).setBaseValue(30);
        gear.getAttribute(Attributes.ARMOR).addTransientModifier(
                new AttributeModifier(Theoria.id("dev_armor"), 270, AttributeModifier.Operation.ADD_VALUE));
        r.line("ceilings: armor %.0f, toughness %.0f, max health %.0f, attack %.0f", max(Attributes.ARMOR), max(Attributes.ARMOR_TOUGHNESS),
                max(Attributes.MAX_HEALTH), max(Attributes.ATTACK_DAMAGE));
        r.line("base 300 armor -> getArmorValue=%d; base 30 + modifier 270 -> %d; toughness 500 -> %.0f; max health 5000 -> %.0f; attack 4000 -> %.0f",
                z.getArmorValue(), gear.getArmorValue(), z.getAttributeValue(Attributes.ARMOR_TOUGHNESS), (double) z.getMaxHealth(),
                z.getAttributeValue(Attributes.ATTACK_DAMAGE));
        r.write();
        h.assertValueEqual(z.getArmorValue(), 300, "armor 300");
        h.assertValueEqual(gear.getArmorValue(), 300, "armor via modifier");
        h.assertTrue(z.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 500, "toughness 500");
        h.assertTrue(z.getMaxHealth() == 5000f, "max health 5000");
        h.assertTrue(z.getAttributeValue(Attributes.ATTACK_DAMAGE) == 4000, "attack damage 4000");
        h.succeed();
    }

    // ------------------------------------------------------------ the 26 simulator cases, through the real pipeline
    private static void simCasesMob(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_sim_cases_mob");
        ServerLevel level = h.getLevel();
        LivingEntity inWorld = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(1, 1, 1));
        LivingEntity temp = BalanceProbe.tempVictim(level, h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        runCases(h, r, level, "world pig", inWorld);
        runCases(h, r, level, "temp pig (what /theoria test balance uses)", temp);
        r.write();
        h.succeed();
    }

    private static void simCasesPlayer(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_sim_cases_player");
        ServerLevel level = h.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer player = FakePlayers.survival(h, "dev-balance");
        try {
            runCases(h, r, level, "survival player, NORMAL", player);
        } finally {
            FakePlayers.remove(player);
        }
        r.write();
        h.succeed();
    }

    private static void runCases(GameTestHelper h, Dev.Report r, ServerLevel level, String who, LivingEntity victim) {
        double worst = 0;
        String worstName = "";
        for (BalanceProbe.Case c : BalanceProbe.cases()) {
            double measured = BalanceProbe.measure(level, victim, c);
            double error = BalanceProbe.relativeError(measured, c.expected());
            r.line("%-40s %-24s tier %-2d armor %-4d T %-3.0f epf %-2d res %d -> measured %9.4f expected %9.4f (%.4f%%)", who, c.name(), c.tier(),
                    c.armor(), c.toughness(), c.epf(), c.resistance(), measured, c.expected(), error * 100);
            if (error > worst) {
                worst = error;
                worstName = c.name();
            }
            h.assertTrue(error < EXACT, who + " / " + c.name() + ": measured " + measured + ", simulator expects " + c.expected());
        }
        r.line("%s: worst deviation %.4f%% (%s)", who, worst * 100, worstName);
    }

    // ------------------------------------------------------------ mob tiers: attributes, save/load, command
    private static void mobScaling(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_mob_scaling");
        ServerLevel level = h.getLevel();
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        double baseHp = z.getAttributeBaseValue(Attributes.MAX_HEALTH), baseArmor = z.getAttributeValue(Attributes.ARMOR);
        z.setHealth((float) baseHp / 2f); // wounded: the fraction must carry over
        Tier t8 = TierRegistry.tier(8);
        MobScaling.assign(z, 8);
        r.line("tier 8 zombie: max health %.1f -> %.2f (x%s), armor %.1f -> %.3f (x%s), health %.2f (half)", baseHp, (double) z.getMaxHealth(),
                t8.mob().hpMult(), baseArmor, z.getAttributeValue(Attributes.ARMOR), t8.mob().dmgMult(), (double) z.getHealth());
        h.assertTrue(Math.abs(z.getMaxHealth() - baseHp * t8.mob().hpMult()) < 0.01, "tier 8 max health");
        h.assertTrue(Math.abs(z.getAttributeValue(Attributes.ARMOR) - baseArmor * t8.mob().dmgMult()) < 1e-6, "tier 8 mob armor");
        h.assertTrue(Math.abs(z.getHealth() - z.getMaxHealth() / 2) < 0.01, "a wounded mob keeps its health fraction");
        h.assertValueEqual(MobScaling.tierOf(z), 8, "tier attachment");

        // save → load into a fresh zombie: tier, attachment and (persistent) modifiers come back
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        z.saveWithoutId(out);
        Zombie loaded = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        r.line("after save/load: tier %d, max health %.2f, armor %.3f", MobScaling.tierOf(loaded), (double) loaded.getMaxHealth(), loaded.getAttributeValue(Attributes.ARMOR));
        h.assertValueEqual(MobScaling.tierOf(loaded), 8, "tier survives save/load");
        h.assertTrue(Math.abs(loaded.getMaxHealth() - baseHp * t8.mob().hpMult()) < 0.01, "max health survives save/load");
        h.assertTrue(Math.abs(loaded.getAttributeValue(Attributes.ARMOR) - baseArmor * t8.mob().dmgMult()) < 1e-6, "armor survives save/load");

        // re-assigning is idempotent (join-time resync) and tier 0 is fully vanilla again
        MobScaling.assign(z, 8);
        h.assertTrue(Math.abs(z.getMaxHealth() - baseHp * t8.mob().hpMult()) < 0.01, "assign twice must not stack");
        MobScaling.assign(z, 0);
        h.assertTrue(z.getMaxHealth() == baseHp && z.getAttributeValue(Attributes.ARMOR) == baseArmor && MobScaling.tierOf(z) == 0
                && !MobScaling.isTiered(z), "tier 0 restores vanilla and leaves no attachment behind");
        boolean threw = false;
        try {
            MobScaling.assign(z, 99);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        h.assertTrue(threw, "unknown tier must be rejected");
        r.write();
        h.succeed();
    }

    /**
     * The kill side of the ladder: a reference player (tier sword × (1 + ref bonus), full charge, no crit) hits an
     * equal-tier zombie. Hits to kill must match tiers.csv {@code kills} — this checks the mob HP and mob armor scaling
     * and the armor formula on a mob victim in the running game (docs/06 §1: 4,5 – 7 hits).
     */
    private static void killHits(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_kill_hits");
        ServerLevel level = h.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        List<List<String>> rows = csv(Path.of(System.getProperty("theoria.dev.data", "data/balance")).resolve("tiers.csv"));
        List<String> header = rows.remove(0);
        ServerPlayer player = FakePlayers.survival(h, "dev-kills");
        try {
            for (List<String> row : rows) {
                int p = Integer.parseInt(row.get(header.indexOf("idx")));
                Tier t = TierRegistry.tier(p);
                Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
                MobScaling.assign(z, p);
                float attack = (float) (t.swordDamage() * (1 + t.ref().bonus()));
                float maxHp = z.getMaxHealth();
                double taken = hit(level, z, level.damageSources().playerAttack(player), attack, false);
                double hits = maxHp / taken;
                double expected = num(row, header, "kills");
                r.line("tier %2d %-10s zombie class %6.1f hp, armor %3d; ref attack %7.2f -> %7.3f per hit -> %.2f hits to kill (tiers.csv kills: %.1f)",
                        p, t.id(), (double) maxHp, z.getArmorValue(), (double) attack, taken, hits, expected);
                h.assertTrue(Math.abs(hits - expected) <= 0.07, "tier " + p + " hits to kill " + hits + ", tiers.csv says " + expected);
                z.discard();
            }
        } finally {
            FakePlayers.remove(player);
        }
        r.write();
        h.succeed();
    }

    /** Each damage source kind the mob multiplier must catch (docs/06 §4): melee, arrow, explosion, armor-bypassing magic. */
    private static void mobDamagePaths(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_mob_damage_paths");
        ServerLevel level = h.getLevel();
        Tier t8 = TierRegistry.tier(8);
        LivingEntity victim = BalanceProbe.tempVictim(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
        Zombie plain = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        MobScaling.assign(skeleton, 8);
        Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
        var src = level.damageSources();
        double melee = hit(level, victim, src.mobAttack(skeleton), 3f);
        double shot = hit(level, victim, src.arrow(arrow, skeleton), 2f);           // direct entity = arrow, causing entity = skeleton
        double blast = hit(level, victim, src.explosion(skeleton, skeleton), 10f);
        double potion = hit(level, victim, src.indirectMagic(skeleton, skeleton), 6f); // #bypasses_armor
        double untiered = hit(level, victim, src.mobAttack(plain), 3f);
        r.line("tier 8 attacker (hp x%s dmg x%s bypass x%s), unarmored victim: melee 3 -> %.4f, arrow 2 -> %.4f, explosion 10 -> %.4f, magic 6 -> %.4f; untiered melee 3 -> %.4f",
                t8.mob().hpMult(), t8.mob().dmgMult(), t8.mob().bypassMult(), melee, shot, blast, potion, untiered);
        r.write();
        h.assertTrue(rel(melee, 3 * t8.mob().dmgMult()) < EXACT, "melee x mob_dmg_mult");
        h.assertTrue(rel(shot, 2 * t8.mob().dmgMult()) < EXACT, "arrow (causing entity = skeleton) x mob_dmg_mult");
        h.assertTrue(rel(blast, 10 * t8.mob().dmgMult()) < EXACT, "explosion x mob_dmg_mult");
        h.assertTrue(rel(potion, 6 * t8.mob().bypassMult()) < EXACT, "armor-bypassing magic x mob_bypass_mult");
        h.assertTrue(rel(untiered, 3) < EXACT, "an untiered mob deals vanilla damage");
        h.succeed();
    }

    /** ADR-003 Faz 2a (6): the player's difficulty scale runs before the tier multiplier (Easy: min(d/2+1, d) on the BASE hit). */
    private static void difficultyOrder(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_difficulty_order");
        ServerLevel level = h.getLevel();
        Tier t8 = TierRegistry.tier(8);
        ServerPlayer player = FakePlayers.survival(h, "dev-difficulty");
        Zombie t8zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        MobScaling.assign(t8zombie, 8);
        double easy, normal, hard;
        Difficulty before = level.getDifficulty();
        try {
            level.getServer().setDifficulty(Difficulty.EASY, true);
            easy = hit(level, player, level.damageSources().mobAttack(t8zombie), 3f);
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            normal = hit(level, player, level.damageSources().mobAttack(t8zombie), 3f);
            level.getServer().setDifficulty(Difficulty.HARD, true);
            hard = hit(level, player, level.damageSources().mobAttack(t8zombie), 3f);
        } finally {
            level.getServer().setDifficulty(before, true);
            FakePlayers.remove(player);
        }
        double mult = t8.mob().dmgMult();
        r.line("player (no armor) hit by a tier-8 zombie, base 3: EASY %.4f (scale-first %.4f, mult-first %.4f), NORMAL %.4f, HARD %.4f (x1.5 = %.4f)",
                easy, Math.min(3 / 2.0 + 1, 3) * mult, Math.min(3 * mult / 2 + 1, 3 * mult), normal, hard, 3 * mult * 1.5);
        r.write();
        h.assertTrue(rel(easy, Math.min(3 / 2.0 + 1, 3) * mult) < EXACT, "Easy: difficulty scale first, tier multiplier after");
        h.assertTrue(rel(normal, 3 * mult) < EXACT, "Normal: x1");
        h.assertTrue(rel(hard, 3 * mult * 1.5) < EXACT, "Hard: x1.5");
        h.succeed();
    }

    // ------------------------------------------------------------ healing scales with max health
    private static void healScaling(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_heal_scaling");
        ServerPlayer player = FakePlayers.survival(h, "dev-heal");
        try {
            player.setHealth(10f);
            player.heal(1f);
            float vanilla = player.getHealth();
            // 214 max health = the tier-13 reference budget (docs/06 §3), added the way gear and stats will: ADD_VALUE modifiers
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(
                    new AttributeModifier(Theoria.id("dev_health"), 194, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(10f);
            player.heal(1f);
            float big = player.getHealth() - 10f;
            player.setHealth(10f);
            player.heal(0.5f); // fractional food-regen sized heal
            float small = player.getHealth() - 10f;
            // natural regeneration (FoodData.tick → heal): saturated fast regen heals saturation/6 every 10 ticks, slow regen 1 every 80
            var food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(6f);
            player.setHealth(10f);
            for (int i = 0; i < 10; i++) food.tick(player);
            float fast = player.getHealth() - 10f;
            food.setFoodLevel(20); // exhaustion left by the fast regen costs a food point on the first tick; 20 -> 19 is still >= 18
            food.setSaturation(0f);
            player.setHealth(10f);
            for (int i = 0; i < 80; i++) food.tick(player);
            float slow = player.getHealth() - 10f;
            r.line("max health 20: heal(1) 10 -> %.4f; max health %.0f: heal(1) -> +%.4f (expect %.4f), heal(0.5) -> +%.4f", vanilla,
                    (double) player.getMaxHealth(), big, 214 / 20.0, small);
            r.line("natural regen at max health 214: fast (10 ticks, saturated) +%.4f, slow (80 ticks, no saturation) +%.4f; vanilla at 20 = +1 each (%.2f%% of max health)",
                    fast, slow, 100.0 / 20);
            r.write();
            h.assertTrue(Math.abs(fast - 10.7f) < 1e-3, "saturated natural regen scales with max health");
            h.assertTrue(Math.abs(slow - 10.7f) < 1e-3, "slow natural regen scales with max health");
            h.assertTrue(Math.abs(vanilla - 11f) < 1e-4, "at 20 max health healing is vanilla");
            h.assertTrue(Math.abs(big - 10.7f) < 1e-3, "heal(1) at 214 max health = +10.7");
            h.assertTrue(Math.abs(small - 5.35f) < 1e-3, "heal(0.5) at 214 max health = +5.35");
        } finally {
            FakePlayers.remove(player);
        }
        h.succeed();
    }

    // ------------------------------------------------------------ Breach (armor_effectiveness) survives the replacement
    private static void breach(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_breach");
        ServerLevel level = h.getLevel();
        CombatParams c = TierRegistry.combat();
        LivingEntity victim = BalanceProbe.tempVictim(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        victim.getAttribute(Attributes.ARMOR).setBaseValue(2000); // fraction ≈ 0,82: Breach IV (-0,6) must not clamp it to 0
        Zombie attacker = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        ItemStack mace = new ItemStack(Items.MACE);
        mace.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BREACH), 4);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, mace);
        DamageSource source = level.damageSources().mobAttack(attacker);
        h.assertTrue(source.getWeaponItem() == mace || (source.getWeaponItem() != null && ItemStack.matches(source.getWeaponItem(), mace)),
                "the mob's main hand must be the damage source's weapon");
        float delta = EnchantmentHelper.modifyArmorEffectiveness(level, mace, victim, source, 0.5f) - 0.5f; // what Breach IV does to the fraction
        double fraction = BalanceMath.armorFraction(c, 2000, 0, 50);
        double expectedBreach = 50 * (1 - Math.max(0, Math.min(1, fraction + delta)));
        double expectedPlain = 50 * (1 - fraction);
        double withBreach = hit(level, victim, source, 50f);
        double without = hit(level, victim, level.damageSources().mobAttack(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND)), 50f);
        r.line("armor 2000 vs 50 dmg: armor fraction %.4f; Breach IV moves it by %.3f; with mace+Breach took %.4f (expect %.4f), same hit without weapon %.4f (expect %.4f)",
                fraction, delta, withBreach, expectedBreach, without, expectedPlain);
        r.write();
        h.assertTrue(delta < -0.1, "Breach IV must lower the armor fraction (got " + delta + ")");
        h.assertTrue(fraction + delta > 0.1 && withBreach > without * 2, "the test must not hide behind the clamp at 0");
        h.assertTrue(rel(withBreach, expectedBreach) < EXACT, "Breach is applied after our formula, like vanilla");
        h.assertTrue(rel(without, expectedPlain) < EXACT, "no weapon: plain formula");
        h.succeed();
    }

    // ------------------------------------------------------------ i-frames: only the part above the first hit is armored
    private static void iFrames(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_iframes");
        ServerLevel level = h.getLevel();
        CombatParams c = TierRegistry.combat();
        LivingEntity victim = BalanceProbe.tempVictim(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        victim.setHealth(1000f);
        victim.getAttribute(Attributes.ARMOR).setBaseValue(100);
        Zombie attacker = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        DamageSource source = level.damageSources().mobAttack(attacker);
        victim.damageCooldownTime = 0;
        victim.setInvulnerableTime(0);
        float h0 = victim.getHealth();
        victim.hurtServer(level, source, 10f);
        double first = h0 - victim.getHealth();
        float h1 = victim.getHealth();
        victim.hurtServer(level, source, 25f);          // stronger hit inside the i-frame window: only 25 - 10 = 15 lands
        double second = h1 - victim.getHealth();
        float h2 = victim.getHealth();
        victim.hurtServer(level, source, 20f);          // weaker than the 25 already taken: ignored
        double third = h2 - victim.getHealth();
        double e1 = 10 * (1 - BalanceMath.armorFraction(c, 100, 0, 10));
        double e2 = 15 * (1 - BalanceMath.armorFraction(c, 100, 0, 15));
        r.line("armor 100: hit 10 -> %.4f (expect %.4f); then hit 25 inside i-frames -> %.4f (expect %.4f = armor on the 15 above); then hit 20 -> %.4f (expect 0)",
                first, e1, second, e2, third);
        r.write();
        h.assertTrue(rel(first, e1) < EXACT, "first hit");
        h.assertTrue(rel(second, e2) < EXACT, "H is the excess over the first hit (ADR-003 definition)");
        h.assertTrue(third == 0, "a weaker hit inside the i-frame window does nothing");
        h.succeed();
    }

    // ------------------------------------------------------------ the commands, end to end
    private static void command(GameTestHelper h) {
        Dev.Report r = new Dev.Report("balance_command");
        var server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-balance-cmd");
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        List<String> out = new ArrayList<>();
        int balance = DevGameTests.runCommand(server, player, "theoria test balance", out);
        int tier = DevGameTests.runCommand(server, player, "theoria tier " + z.getStringUUID() + " 5", out);
        int tierOfZombie = MobScaling.tierOf(z);
        int unknown = DevGameTests.runCommand(server, player, "theoria tier " + z.getStringUUID() + " 99", out);
        int onPlayer = DevGameTests.runCommand(server, player, "theoria tier " + player.getStringUUID() + " 3", out);
        int back = DevGameTests.runCommand(server, player, "theoria tier " + z.getStringUUID() + " 0", out);
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("test balance -> %d passed; tier 5 -> %d (zombie tier %d); tier 99 -> %d; tier on a player -> %d; tier 0 -> %d (zombie tier %d)",
                balance, tier, tierOfZombie, unknown, onPlayer, back, MobScaling.tierOf(z));
        r.write();
        h.assertTrue(balance >= 6, "/theoria test balance should pass all its checks, got " + balance);
        h.assertValueEqual(tier, 1, "/theoria tier result");
        h.assertValueEqual(tierOfZombie, 5, "tier assigned");
        h.assertValueEqual(unknown, -1, "unknown tier must fail");
        h.assertTrue(onPlayer <= 0, "players are never tiered");
        h.assertValueEqual(MobScaling.tierOf(z), 0, "tier 0 clears");
        h.succeed();
    }

    // ------------------------------------------------------------ helpers
    /** One hit on a victim given 1000 health (any mob or player would die from a tier-8 hit); returns the health it lost. */
    private static double hit(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        return hit(level, victim, source, amount, true);
    }

    /** {@code roomy = false}: keep the victim's own health (the hit must not kill it). */
    private static double hit(ServerLevel level, LivingEntity victim, DamageSource source, float amount, boolean roomy) {
        if (roomy) {
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            victim.setHealth(1000f);
        }
        victim.damageCooldownTime = 0;
        victim.setInvulnerableTime(0);
        float before = victim.getHealth();
        victim.hurtServer(level, source, amount);
        return before - victim.getHealth();
    }

    private static double rel(double measured, double expected) {
        return BalanceProbe.relativeError(measured, expected);
    }

    private static double max(Holder<Attribute> attribute) {
        return ((RangedAttribute) attribute.value()).getMaxValue();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1e-6 * Math.max(1.0, Math.abs(b));
    }

    private static double num(List<String> row, List<String> header, String column) {
        return Double.parseDouble(row.get(header.indexOf(column)));
    }

    private static List<List<String>> csv(Path path) {
        try {
            List<List<String>> rows = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank()) rows.add(BalanceProbe.splitCsv(line));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path + " (is -Dtheoria.dev.data set?)", e);
        }
    }
}
