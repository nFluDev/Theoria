package dev.theoria.devtest;

import dev.theoria.curios.WornCurios;
import dev.theoria.relics.RelicData;
import dev.theoria.relics.RelicEffects;
import dev.theoria.relics.RelicItem;
import dev.theoria.relics.RelicItems;
import dev.theoria.relics.RelicProbe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Phase 12 GameTests, headless. Every relic has its own test ({@code relic_<id>}): the real item goes into a real Curios slot of a survival mock player and {@link RelicProbe} proves what its
 * kind promises and that taking it off undoes it. On top: the whole {@code /theoria test relic} command, the tick schedule, real damage events (immunity, surviving a lethal hit), all relics
 * worn at once, and the loot modifiers on the REAL target tables (a boss kill always drops exactly one relic; chests drop relics). What a headless test cannot see — the icons, the tooltip
 * LOOK, the creative tab — is the user's approval, like every Theoria visual.
 */
final class RelicTests {
    private static final int ROLLS = 3000;

    private RelicTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("relic_checks", RelicTests::commandChecks);
        for (RelicData.Def def : RelicData.all()) add.accept("relic_" + def.id(), h -> one(h, def));
        add.accept("relic_tick_schedule", RelicTests::tickSchedule);
        add.accept("relic_immune_events", RelicTests::immuneEvents);
        add.accept("relic_survive_event", RelicTests::surviveEvent);
        add.accept("relic_wear_all", RelicTests::wearAll);
        add.accept("relic_loot_modifiers", RelicTests::lootModifiers);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static void equip(ServerPlayer player, String id) {
        RelicData.Def def = RelicData.get(id);
        if (WornCurios.equip(player, def.slot(), RelicItems.stack(id)) < 0) throw new IllegalStateException("no free " + def.slot() + " slot for " + id);
    }

    private static void refresh(ServerPlayer player) {
        RelicEffects.refresh(player);
    }

    // ------------------------------------------------------------------------------------------------ per relic
    private static void one(GameTestHelper h, RelicData.Def def) {
        Dev.Report r = new Dev.Report("relic_" + def.id());
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-" + def.id());
        try {
            String detail = RelicProbe.run(player, def);
            r.line("%s: %s", def.id(), detail);
            // through the command too: the same probe, started the way a game master starts it
            List<String> out = new ArrayList<>();
            int passed = DevGameTests.runCommand(h.getLevel().getServer(), player, "theoria test relic " + def.id(), out);
            r.line("/theoria test relic %s -> %d checks passed %s", def.id(), passed, out);
            h.assertTrue(passed == 1, "/theoria test relic " + def.id() + " should pass exactly 1 check, got " + passed + " " + out);
        } finally {
            FakePlayers.remove(player);
            r.write();
        }
        h.succeed();
    }

    private static void commandChecks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_checks");
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-checks");
        List<String> out = new ArrayList<>();
        int passed = DevGameTests.runCommand(h.getLevel().getServer(), player, "theoria test relic", out);
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("/theoria test relic -> %d checks passed", passed);
        r.write();
        h.assertTrue(passed == RelicData.all().size() + 2, "/theoria test relic should pass " + (RelicData.all().size() + 2) + " checks (table + loot + one per relic), got " + passed + " " + out);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ schedule
    /** The real server tick path: nothing is called but {@code PlayerTickEvent.Post} at the right tick counts. */
    private static void tickSchedule(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_tick_schedule");
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-tick");
        try {
            equip(player, "night_vision_ring");
            equip(player, "wind_cloak");
            equip(player, "eternal_apple");
            player.getFoodData().setFoodLevel(10);
            double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            player.tickCount = 1;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "nothing is derived before the first refresh tick");
            player.tickCount = 20;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "night vision after the refresh tick");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base * 1.10) < 1.0E-6, "wind cloak: +10% movement speed (" + base + " → " + player.getAttributeValue(Attributes.MOVEMENT_SPEED) + ")");
            int food = player.getFoodData().getFoodLevel();
            player.tickCount = 100; // 100 % interval(100) == 0
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertTrue(player.getFoodData().getFoodLevel() == food + 1, "eternal apple: +1 hunger on its interval, " + food + " → " + player.getFoodData().getFoodLevel());
            r.line("night vision + 10%% speed after tick 20, +1 hunger at tick 100");
            // taking everything off: the next refresh removes both
            WornCurios.swap(player, "ring", 0, ItemStack.EMPTY);
            WornCurios.swap(player, "back", 0, ItemStack.EMPTY);
            player.tickCount = 40;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "night vision removed once the ring is off");
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0E-9, "speed back to " + base);
            r.line("everything off → effect and modifier gone at the next refresh tick");
        } finally {
            FakePlayers.remove(player);
            r.write();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ real damage events
    private static void immuneEvents(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_immune_events");
        ServerLevel level = h.getLevel();
        List<String> all = List.of("dragon_scale", "warding_bracelet", "storm_talisman");
        // baselines without relics (difficulty scaling, Theoria's armor model are whatever they are; the relics must take their SHARE of it)
        double lava = hit(h, List.of(), level.damageSources().lava(), 4.0F);
        double lightning = hit(h, List.of(), level.damageSources().lightningBolt(), 5.0F);
        double explosion = hit(h, List.of(), level.damageSources().explosion(null, null), 10.0F);
        double magic = hit(h, List.of(), level.damageSources().magic(), 4.0F);
        h.assertTrue(lava > 0 && lightning > 0 && explosion > 0 && magic > 0, "baseline: every damage type hurts (lava " + lava + ", lightning " + lightning + ", explosion " + explosion + ", magic " + magic + ")");
        h.assertTrue(hit(h, all, level.damageSources().lava(), 4.0F) == 0.0, "dragon scale: lava does nothing");
        h.assertTrue(hit(h, all, level.damageSources().lightningBolt(), 5.0F) == 0.0, "storm talisman: lightning does nothing");
        double reduced = hit(h, all, level.damageSources().explosion(null, null), 10.0F);
        h.assertTrue(Math.abs(reduced - explosion * 0.5) < 0.05, "warding bracelet halves explosion damage: " + explosion + " → " + reduced);
        double plain = hit(h, all, level.damageSources().magic(), 4.0F);
        h.assertTrue(Math.abs(plain - magic) < 1.0E-6, "unrelated (magic) damage is untouched: " + magic + " → " + plain);
        r.line("lava %.2f→0, lightning %.2f→0, explosion %.2f→%.2f, magic %.2f→%.2f (unrelated)", lava, lightning, explosion, reduced, magic, plain);
        r.write();
        h.succeed();
    }

    /** Health lost to ONE real hit on a fresh survival player wearing {@code relics} (a fresh player each time: i-frames are not resettable from outside). */
    private static double hit(GameTestHelper h, List<String> relics, DamageSource source, float amount) {
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-hit");
        try {
            for (String id : relics) equip(player, id);
            refresh(player);
            float health = player.getHealth();
            player.hurtServer(player.level(), source, amount);
            return health - player.getHealth();
        } finally {
            FakePlayers.remove(player);
        }
    }

    private static void surviveEvent(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_survive_event");
        ServerLevel level = h.getLevel();
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-survive");
        try {
            equip(player, "phoenix_feather");
            refresh(player);
            player.setHealth(3.0F);
            player.hurtServer(level, level.damageSources().generic(), 1000.0F); // the real chain: Incoming → armor → Pre → survive
            h.assertTrue(player.isAlive() && !player.isDeadOrDying(), "the Phoenix Feather turns a lethal hit into nothing");
            h.assertTrue(player.getHealth() >= 0.5F * player.getMaxHealth() - 0.01F, "restored to half of max health: " + player.getHealth() + " / " + player.getMaxHealth());
            h.assertTrue(player.getCooldowns().isOnCooldown(RelicEffects.wornStack(player, "phoenix_feather")), "the item cooldown shows in the inventory");
            r.line("survived a 1000 hit at 3 HP → %.1f / %.1f, cooldown on", player.getHealth(), player.getMaxHealth());
        } finally {
            FakePlayers.remove(player);
            r.write();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ all at once
    private static void wearAll(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_wear_all");
        ServerPlayer player = FakePlayers.survival(h, "dev-relic-all");
        try {
            Map<String, Integer> perSlot = new HashMap<>();
            for (RelicData.Def def : RelicData.all()) {
                perSlot.merge(def.slot(), 1, Integer::sum);
                h.assertTrue(WornCurios.equip(player, def.slot(), RelicItems.stack(def.id())) >= 0, "no room for " + def.id() + " in '" + def.slot() + "' (" + WornCurios.slotCount(player, def.slot()) + " slots)");
            }
            refresh(player);
            h.assertTrue(RelicEffects.worn(player).size() == RelicData.all().size(), "all " + RelicData.all().size() + " relics are worn at once: " + RelicEffects.worn(player).size());
            // the budget: every attribute relic at once stays small (data/relics.csv numbers; tools/check_relics.py bounds the same sums)
            double crit = player.getAttributeValue(BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("theoria:crit_chance")).orElseThrow());
            double steal = player.getAttributeValue(BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("theoria:life_steal")).orElseThrow());
            h.assertTrue(crit <= 0.10 && steal <= 0.10, "relic crit chance " + crit + ", life steal " + steal + " must stay below the catalyst per-piece caps");
            r.line("%d relics worn at once in %s; crit chance %.2f, life steal %.2f, speed %.4f, max health %.1f", RelicEffects.worn(player).size(), perSlot, crit, steal, player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getMaxHealth());
            // take every one off again
            for (RelicData.Def def : RelicData.all()) {
                for (int i = 0; i < WornCurios.slotCount(player, def.slot()); i++) WornCurios.swap(player, def.slot(), i, ItemStack.EMPTY);
            }
            refresh(player);
            h.assertTrue(RelicEffects.worn(player).isEmpty(), "nothing worn after taking all off");
            h.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) <= 20.0 + 1.0E-9, "max health back to 20: " + player.getMaxHealth());
            h.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "night vision gone");
        } finally {
            FakePlayers.remove(player);
            r.write();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ loot
    /** The real target tables (the ones structures and bosses roll), so the global loot modifiers are in the chain. */
    private static void lootModifiers(GameTestHelper h) {
        Dev.Report r = new Dev.Report("relic_loot_modifiers");
        ServerLevel level = h.getLevel();
        var registries = level.getServer().reloadableRegistries();
        Vec3 origin = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        h.assertTrue(zombie != null, "test zombie");
        DamageSource source = level.damageSources().generic();
        for (RelicData.SourceDef src : RelicData.table().sources()) {
            var table = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(src.target())));
            int rolls = src.boss() ? 200 : ROLLS;
            int relics = 0, killsWithRelic = 0;
            Map<String, Integer> seen = new HashMap<>();
            RandomSource random = RandomSource.create(7L + src.id().hashCode());
            for (int i = 0; i < rolls; i++) {
                LootParams params = src.boss()
                        ? new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, zombie).withParameter(LootContextParams.ORIGIN, origin)
                                .withParameter(LootContextParams.DAMAGE_SOURCE, source).create(LootContextParamSets.ENTITY)
                        : new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
                int here = 0;
                for (ItemStack s : table.getRandomItems(params, random)) {
                    if (s.getItem() instanceof RelicItem relic) {
                        here++;
                        seen.merge(relic.def().id(), 1, Integer::sum);
                        h.assertTrue(relic.def().sources().stream().anyMatch(x -> x.source().equals(src.id())), relic.def().id() + " came out of " + src.id() + " which does not list it");
                    }
                }
                relics += here;
                if (here > 0) killsWithRelic++;
                if (src.boss()) h.assertTrue(here == 1, src.id() + " roll " + i + " dropped " + here + " relics, a boss always drops exactly one");
            }
            h.assertTrue(relics > 0, "no relic ever came out of the real table " + src.target() + " in " + rolls + " rolls — the loot modifier is not applied");
            r.line("%s (%s): %d rolls → %d relics in %d rolls, %d different relics", src.id(), src.target(), rolls, relics, killsWithRelic, seen.size());
        }
        r.write();
        h.succeed();
    }
}
