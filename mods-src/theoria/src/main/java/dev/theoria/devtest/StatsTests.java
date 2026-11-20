package dev.theoria.devtest;

import com.mojang.serialization.JsonOps;
import dev.theoria.catalyst.CatalystComponents;
import dev.theoria.catalyst.Erudition;
import dev.theoria.mobs.MobHooks;
import dev.theoria.stats.LevelCurve;
import dev.theoria.stats.PlayerStats;
import dev.theoria.stats.StatsData;
import dev.theoria.stats.StatsEffects;
import dev.theoria.stats.StatsModule;
import dev.theoria.stats.StatsServer;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Phase 10 GameTests, headless: the lifetime XP and level (L2 = 55 raw XP; spending vanilla XP never lowers the level), the point rules, and each of the
 * eight stats' effects ONE BY ONE against the generator's numbers. What a headless test cannot see — the screen, the key, the HUD text — is the
 * unattended client scenario ({@code runDevClient -PdevClient=stats}); how it LOOKS is the user's approval.
 */
final class StatsTests {
    private static final double EPS = 1e-6;

    private StatsTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("stats_checks", StatsTests::checks);
        add.accept("stats_level_curve", StatsTests::levelCurve);
        add.accept("stats_xp_spend", StatsTests::xpSpend);
        add.accept("stats_allocate_rules", StatsTests::allocateRules);
        add.accept("stats_codecs", StatsTests::codecs);
        add.accept("stats_effect_strength", h -> effect(h, "strength", StatsTests::strength));
        add.accept("stats_effect_vitality", h -> effect(h, "vitality", StatsTests::vitality));
        add.accept("stats_effect_endurance", h -> effect(h, "endurance", StatsTests::endurance));
        add.accept("stats_effect_agility", h -> effect(h, "agility", StatsTests::agility));
        add.accept("stats_effect_satiety", h -> effect(h, "satiety", StatsTests::satiety));
        add.accept("stats_effect_precision", h -> effect(h, "precision", StatsTests::precision));
        add.accept("stats_effect_fortune", h -> effect(h, "fortune", StatsTests::fortune));
        add.accept("stats_effect_wisdom", h -> effect(h, "wisdom", StatsTests::wisdom));
        add.accept("stats_survive_death", StatsTests::surviveDeath);
        add.accept("stats_respec_orb", StatsTests::respecOrb);
        add.accept("stats_orb_recipe", StatsTests::orbRecipe);
        add.accept("stats_mob_xp", StatsTests::mobXp);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    @FunctionalInterface
    private interface EffectTest {
        void run(GameTestHelper h, ServerPlayer p, int points, Dev.Report r);
    }

    private static final int POINTS = 120;
    /** Points per level come from the data (tools/balance/stats.py), never from a literal here. */
    private static int per() {
        return StatsData.leveling().pointsPerLevel();
    }

    private static int levelFor(int points) {
        return 1 + points / per();
    }


    /** A fresh survival player at the level that gives exactly {@link #POINTS} points, all put into {@code stat}; {@code body} checks the effect. */
    private static void effect(GameTestHelper h, String stat, EffectTest body) {
        Dev.Report r = new Dev.Report("stats_effect_" + stat);
        ServerPlayer p = FakePlayers.survival(h, "dev-stat-" + stat);
        try {
            h.assertTrue(StatsModule.of(p).level() == 1 && StatsModule.of(p).available() == 0, "a fresh player is level 1 with no points");
            StatsServer.setXp(p, LevelCurve.totalXp(levelFor(POINTS)));
            h.assertTrue(StatsModule.of(p).available() == POINTS, "level " + levelFor(POINTS) + " must grant " + POINTS + " points, got " + StatsModule.of(p).available());
            StatsServer.Result result = StatsServer.allocate(p, Map.of(stat, POINTS));
            h.assertTrue(result == StatsServer.Result.OK, "allocation refused: " + result);
            h.assertTrue(StatsModule.of(p).pointOf(stat) == POINTS && StatsModule.of(p).available() == 0, "points not booked");
            body.run(h, p, POINTS, r);
            // every OTHER attribute this stat does not own stays at its base: the stat touches only its own effects
            StatsData.Stat own = StatsData.stat(stat);
            for (StatsData.Stat other : StatsData.stats()) {
                if (other == own) continue;
                for (StatsData.Effect e : other.effects()) {
                    if (e.kind() != StatsData.Kind.ATTRIBUTE) continue;
                    boolean shared = own.effects().stream().anyMatch(o -> o.attribute().equals(e.attribute()));
                    Holder<Attribute> holder = StatsEffects.attribute(e.attribute());
                    h.assertTrue(shared || p.getAttribute(holder).getModifier(StatsEffects.modifierId(other.id(), e.id())) == null, stat + " must not feed " + other.id() + "." + e.id());
                }
            }
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static StatsData.Effect effectOf(String stat, String id) {
        return StatsData.stat(stat).effects().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static void near(GameTestHelper h, String what, double got, double want, Dev.Report r) {
        r.line("%-34s got %.6f  want %.6f", what, got, want);
        h.assertTrue(Math.abs(got - want) <= EPS * Math.max(1.0, Math.abs(want)), what + ": got " + got + ", expected " + want);
    }

    // ------------------------------------------------------------------------------------------------ the eight stats
    private static void strength(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "attack damage (base 1 + stat)", p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + effectOf("strength", "attack_damage").value(n), r);
        near(h, "mining efficiency (base 0 + stat)", p.getAttributeValue(Attributes.MINING_EFFICIENCY), effectOf("strength", "mining_efficiency").value(n), r);
    }

    private static void vitality(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "max health (20 + stat)", p.getMaxHealth(), 20.0 + effectOf("vitality", "max_health").value(n), r);
        h.assertTrue(p.getHealth() > 20.0f + 1e-3, "spending a point also fills what it adds: health " + p.getHealth());
    }

    private static void endurance(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "armor (0 + stat)", p.getAttributeValue(Attributes.ARMOR), effectOf("endurance", "armor").value(n), r);
        near(h, "armor toughness (0 + stat)", p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), effectOf("endurance", "armor_toughness").value(n), r);
        near(h, "knockback resistance (0 + stat)", p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), effectOf("endurance", "knockback_resistance").value(n), r);
    }

    private static void agility(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "movement speed (0.1 × (1 + stat))", p.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.1 * (1.0 + effectOf("agility", "movement_speed").value(n)), r);
    }

    private static void satiety(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "food saturation attribute", p.getAttributeValue(CatalystComponents.FOOD_SATURATION), effectOf("satiety", "food_saturation").value(n), r);
        double reduction = effectOf("satiety", "exhaustion").value(n);
        near(h, "exhaustion reduction share", StatsEffects.exhaustionReduction(p), reduction, r);
        // the hook: exhaustion the body gained between two ticks is partly given back; what vanilla spent is left alone
        p.getFoodData().exhaustionLevel = 0.0F;
        PlayerTickEvent.Post tick = new PlayerTickEvent.Post(p);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(tick);        // records "0" as the last exhaustion
        p.getFoodData().addExhaustion(2.0F);                                  // a vanilla source (sprinting, jumping, healing) adds 2.0
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(tick);
        near(h, "exhaustion after +2.0", p.getFoodData().exhaustionLevel, 2.0 * (1.0 - reduction), r);
        p.getFoodData().exhaustionLevel = 0.5F;                               // vanilla spends exhaustion (FoodData#tick lowers it): a fall is never "reduced"
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(tick);
        near(h, "exhaustion after a fall to 0.5", p.getFoodData().exhaustionLevel, 0.5, r);
    }

    private static void precision(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "crit chance attribute", p.getAttributeValue(CatalystComponents.CRIT_CHANCE), effectOf("precision", "crit_chance").value(n), r);
        near(h, "crit damage attribute", p.getAttributeValue(CatalystComponents.CRIT_DAMAGE), effectOf("precision", "crit_damage").value(n), r);
    }

    private static void fortune(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        near(h, "luck (0 + stat)", p.getAttributeValue(Attributes.LUCK), effectOf("fortune", "luck").value(n), r);
    }

    private static void wisdom(GameTestHelper h, ServerPlayer p, int n, Dev.Report r) {
        double want = effectOf("wisdom", "xp_percent").value(n);
        int total = Erudition.totalPercent(p);
        r.line("Bilgelik sum without gear: %d %% (stat %.4f)", total, want);
        h.assertTrue(total == Math.round(want), "Wisdom must be the whole Bilgelik sum with no gear: " + total + " vs " + want);
        int raw = 200;
        h.assertTrue(Erudition.scaled(raw, total) == Math.round(raw * (100.0 + total) / 100.0), "scaled XP follows the shared formula");
        h.assertTrue(StatsEffects.xpPercent(p) == total, "the helper and the enchantment's sum agree");
    }

    // ------------------------------------------------------------------------------------------------ rules
    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_checks");
        ServerPlayer player = FakePlayers.survival(h, "dev-stats-checks");
        List<String> out = new ArrayList<>();
        int passed = DevGameTests.runCommand(h.getLevel().getServer(), player, "theoria test stats", out);
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("/theoria test stats -> %d checks passed", passed);
        r.write();
        h.assertTrue(passed >= 7, "/theoria test stats should pass >= 7 checks, got " + passed + " " + out);
        h.succeed();
    }

    private static void levelCurve(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_level_curve");
        h.assertTrue(LevelCurve.totalXp(2) == 55, "L2 needs exactly 55 raw XP, got " + LevelCurve.totalXp(2));
        h.assertTrue(LevelCurve.levelOf(54) == 1 && LevelCurve.levelOf(55) == 2, "54 XP = L1, 55 XP = L2");
        for (long[] c : StatsData.table().cases().xp()) {
            h.assertTrue(Math.abs(LevelCurve.totalXp((int) c[0]) - c[1]) <= 1, "R(" + c[0] + ") = " + LevelCurve.totalXp((int) c[0]) + " vs " + c[1]);
        }
        h.assertTrue(LevelCurve.levelOf(Long.MAX_VALUE) == 9999, "the level never passes 9999");
        h.assertTrue(PlayerStats.NEW.addXp(Long.MAX_VALUE).xp() == LevelCurve.maxXp() && PlayerStats.NEW.addXp(Long.MAX_VALUE).level() == 9999, "lifetime XP clamps at the L9999 total");
        h.assertTrue(PlayerStats.NEW.addXp(Long.MAX_VALUE).addXp(1).xp() == LevelCurve.maxXp(), "no overflow past the cap");
        r.line("L2=%d L3=%d L10=%d L200=%d L9999=%,d", LevelCurve.totalXp(2), LevelCurve.totalXp(3), LevelCurve.totalXp(10), LevelCurve.totalXp(200), LevelCurve.maxXp());
        r.write();
        h.succeed();
    }

    /** DoD: spent XP never lowers the level. Vanilla levels are spent by enchanting / the anvil / commands; none of it touches the lifetime total. */
    private static void xpSpend(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_xp_spend");
        ServerPlayer p = FakePlayers.survival(h, "dev-xp");
        try {
            p.giveExperiencePoints(54);
            h.assertTrue(StatsModule.of(p).level() == 1 && StatsModule.of(p).xp() == 54, "54 XP is still level 1, got " + StatsModule.of(p).level());
            p.giveExperiencePoints(1);
            h.assertTrue(StatsModule.of(p).level() == 2 && StatsModule.of(p).xp() == 55, "55 XP is level 2");
            p.giveExperiencePoints(1_000_000);
            int level = StatsModule.of(p).level();
            long xp = StatsModule.of(p).xp();
            h.assertTrue(xp == 1_000_055 && level == LevelCurve.levelOf(1_000_055L) && level > 50, "1,000,055 XP → level " + level);
            int vanilla = p.experienceLevel;
            h.assertTrue(vanilla > 30, "vanilla level " + vanilla);
            p.giveExperienceLevels(-30);                               // an enchanting table's / anvil's price
            p.onEnchantmentPerformed(ItemStack.EMPTY, 3);               // the enchanting table's own spend
            p.giveExperienceLevels(-p.experienceLevel);                // all of it
            p.giveExperiencePoints(-500);                              // a negative change (a command) is ignored by the counter
            r.line("vanilla level %d → %d; lifetime XP %,d → %,d; Theoria level %d → %d", vanilla, p.experienceLevel, xp, StatsModule.of(p).xp(), level, StatsModule.of(p).level());
            h.assertTrue(StatsModule.of(p).xp() == xp && StatsModule.of(p).level() == level, "spending vanilla XP must not touch the lifetime XP or the level");
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void allocateRules(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_allocate_rules");
        ServerPlayer p = FakePlayers.survival(h, "dev-alloc");
        try {
            StatsServer.setXp(p, LevelCurve.totalXp(levelFor(40)));   // exactly 40 points
            PlayerStats s = StatsModule.of(p);
            h.assertTrue(s.level() == levelFor(40) && s.available() == 40, "level " + levelFor(40) + " = 40 points, got " + s.available());
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 41)) == StatsServer.Result.TOO_MANY, "41 of 40 points must be refused");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 20, "strength", 21)) == StatsServer.Result.TOO_MANY, "20 + 21 of 40 points must be refused as a whole");
            h.assertTrue(StatsModule.of(p).spent() == 0, "a refused request books nothing");
            h.assertTrue(StatsServer.allocate(p, Map.of("bogus", 1)) == StatsServer.Result.UNKNOWN_STAT, "an unknown stat is refused");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 10, "bogus", 1)) == StatsServer.Result.UNKNOWN_STAT && StatsModule.of(p).spent() == 0, "one bad entry refuses the whole request");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", -1)) == StatsServer.Result.UNKNOWN_STAT, "a negative amount is refused");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 0)) == StatsServer.Result.NOTHING, "zero points is nothing");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", Integer.MAX_VALUE)) == StatsServer.Result.TOO_MANY, "an enormous request is refused, not overflowed");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 10, "strength", 30)) == StatsServer.Result.OK, "10 + 30 of 40 points are fine");
            s = StatsModule.of(p);
            h.assertTrue(s.pointOf("vitality") == 10 && s.pointOf("strength") == 30 && s.available() == 0 && s.spent() == 40, "booked 10 + 30, none left");
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 1)) == StatsServer.Result.TOO_MANY, "no points left");
            // levelling up gives 4 more
            StatsServer.addXp(p, LevelCurve.totalXp(levelFor(40) + 1) - LevelCurve.totalXp(levelFor(40)));
            h.assertTrue(StatsModule.of(p).level() == levelFor(40) + 1 && StatsModule.of(p).available() == per(), "one more level = " + per() + " new point(s), got " + StatsModule.of(p).available());
            r.line("rules ok: all-or-nothing, no overflow, unknown stat refused, points per level from the data");
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void codecs(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_codecs");
        PlayerStats stats = PlayerStats.of(123_456_789_012L, Map.of("vitality", 7, "wisdom", 3));
        var json = PlayerStats.CODEC.codec().encodeStart(JsonOps.INSTANCE, stats).getOrThrow();
        PlayerStats fromJson = PlayerStats.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(fromJson.equals(stats), "JSON round trip: " + json);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerStats.STREAM_CODEC.encode(buf, stats);
        h.assertTrue(PlayerStats.STREAM_CODEC.decode(buf).equals(stats), "stream round trip");
        // hostile / stale saves: unknown stats and non-positive amounts are dropped, the XP is clamped
        PlayerStats dirty = PlayerStats.of(-5, Map.of("vitality", -3, "ghost", 9, "strength", 2));
        h.assertTrue(dirty.xp() == 0 && dirty.points().equals(Map.of("strength", 2)), "dirty input is cleaned: " + dirty);
        r.line("json=%s", json);
        r.write();
        h.succeed();
    }

    private static void surviveDeath(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_survive_death");
        ServerPlayer p = FakePlayers.survival(h, "dev-death");
        try {
            StatsServer.setXp(p, LevelCurve.totalXp(levelFor(120)));   // 120 points
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 80, "endurance", 40)) == StatsServer.Result.OK, "allocate");
            double hp = p.getMaxHealth();
            double armor = p.getAttributeValue(Attributes.ARMOR);
            ServerPlayer again = p.level().getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
            r.line("after death + respawn: level %d, vitality %d, max health %.4f → %.4f, armor %.4f → %.4f", StatsModule.of(again).level(), StatsModule.of(again).pointOf("vitality"),
                    hp, (double) again.getMaxHealth(), armor, again.getAttributeValue(Attributes.ARMOR));
            h.assertTrue(StatsModule.of(again).level() == levelFor(120) && StatsModule.of(again).pointOf("vitality") == 80 && StatsModule.of(again).pointOf("endurance") == 40, "levels and points are for life");
            h.assertTrue(Math.abs(again.getMaxHealth() - hp) < EPS && Math.abs(again.getAttributeValue(Attributes.ARMOR) - armor) < EPS, "the effects are derived again on the new entity");
            FakePlayers.remove(again);
        } finally {
            if (p.isAlive()) FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    /** Resetting points must cost something real (user, 2026-11-20): the orb is craftable, from an End Crystal (+ 4 ender eyes + 4 diamonds). */
    private static void orbRecipe(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_orb_recipe");
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, dev.theoria.Theoria.id("oblivion_orb"));
        var holder = h.getLevel().getServer().getRecipeManager().byKey(key);
        h.assertTrue(holder.isPresent(), "the Orb of Oblivion has a crafting recipe");
        h.assertTrue(holder.get().value() instanceof net.minecraft.world.item.crafting.ShapedRecipe, "it is a shaped recipe");
        r.line("recipe %s: %s", key.identifier(), holder.get().value().getClass().getSimpleName());
        r.write();
        h.succeed();
    }

    private static void respecOrb(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_respec_orb");
        ServerPlayer p = FakePlayers.survival(h, "dev-orb");
        try {
            ItemStack orb = new ItemStack(StatsModule.OBLIVION_ORB.get(), 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, orb);
            InteractionResult empty = orb.use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(!empty.consumesAction() && orb.getCount() == 2, "an orb is not used up when nothing was spent: " + empty);
            StatsServer.setXp(p, LevelCurve.totalXp(levelFor(80)));   // 80 points
            h.assertTrue(StatsServer.allocate(p, Map.of("vitality", 50, "fortune", 30)) == StatsServer.Result.OK, "allocate");
            h.assertTrue(p.getMaxHealth() > 20.5f && p.getAttributeValue(Attributes.LUCK) > 0.1, "effects are on");
            InteractionResult used = orb.use(p.level(), p, InteractionHand.MAIN_HAND);
            PlayerStats s = StatsModule.of(p);
            r.line("orb: %s, orb count %d, level %d, spent %d, available %d, max health %.1f, luck %.3f", used, orb.getCount(), s.level(), s.spent(), s.available(), (double) p.getMaxHealth(), p.getAttributeValue(Attributes.LUCK));
            h.assertTrue(used.consumesAction() && orb.getCount() == 1, "one orb is used");
            h.assertTrue(s.level() == levelFor(80) && s.spent() == 0 && s.available() == 80, "the level stays, every point comes back");
            h.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 1e-4 && Math.abs(p.getAttributeValue(Attributes.LUCK)) < 1e-9, "the effects are gone");
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void mobXp(GameTestHelper h) {
        Dev.Report r = new Dev.Report("stats_mob_xp");
        int t13 = MobHooks.scaledExperience(5, 13);
        r.line("zombie-class XP: T0 %d · T4 %d · T8 %d · T13 %d", MobHooks.scaledExperience(5, 0), MobHooks.scaledExperience(5, 4), MobHooks.scaledExperience(5, 8), t13);
        h.assertTrue(MobHooks.scaledExperience(5, 0) == 5, "tier 0 is vanilla");
        h.assertTrue(t13 >= 5_000 && t13 <= 15_000, "T13 zombie-class XP must stay in the Faz 10 band (5K-15K): " + t13);
        for (int t = 0; t < 13; t++) h.assertTrue(MobHooks.scaledExperience(5, t + 1) > MobHooks.scaledExperience(5, t), "XP must grow with the tier");
        r.write();
        h.succeed();
    }
}
