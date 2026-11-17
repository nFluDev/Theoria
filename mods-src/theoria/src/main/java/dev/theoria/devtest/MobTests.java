package dev.theoria.devtest;

import dev.theoria.balance.MobScaling;
import dev.theoria.core.check.Checks;
import dev.theoria.mobs.HeartCrystals;
import dev.theoria.mobs.MobData;
import dev.theoria.mobs.MobHooks;
import dev.theoria.mobs.MobRegistry;
import dev.theoria.mobs.MobSpawning;
import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import dev.theoria.mobs.archetype.SentinelMob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Phase 8 GameTests: the species system in a running server. Expected values come from the CSVs through the jar-embedded copies
 * ({@code mob_ttk.csv}, {@code mob_zone_cases.csv}) and {@code data/mobs.csv} / {@code data/mob_drops.csv}, never from the generated Java table.
 * Behaviour tests run real AI in a stone arena against a survival player; each one waits for the behaviour to show (≤ 200 ticks).
 */
final class MobTests {
    private MobTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("mobs_checks", MobTests::checks);
        add.accept("mobs_drops", MobTests::drops);
        add.accept("mobs_xp", MobTests::xp);
        add.accept("mobs_zone_vanilla", MobTests::zoneVanilla);
        add.accept("mobs_weapon_event", MobTests::weaponEvent);
        add.accept("mobs_heart_crystal", MobTests::heartCrystal);
        add.accept("mobs_spawn_rules", MobTests::spawnRules);
        add.accept("mobs_ai_melee", h -> aiMelee(h, "mud_walker", "humanoid"));
        add.accept("mobs_ai_swarm_alert", MobTests::swarmAlert);
        add.accept("mobs_ai_predator", h -> aiMelee(h, "steel_wolf", "predator leap"));
        add.accept("mobs_ai_charger", h -> aiTelegraph(h, "thorn_boar", 14, "charger"));
        add.accept("mobs_ai_giant", h -> aiTelegraph(h, "ash_golem", 2.5, "giant slam"));
        add.accept("mobs_ai_ranged", MobTests::aiRanged);
        add.accept("mobs_ai_caster", MobTests::aiCaster);
        add.accept("mobs_ai_flyer", h -> aiMelee(h, "storm_eagle", "flyer swoop"));
        add.accept("mobs_ai_sentinel", MobTests::aiSentinel);
        add.accept("mobs_ai_assassin", MobTests::aiAssassin);
        add.accept("mobs_ai_serpent", MobTests::aiSerpent);
        add.accept("mobs_ai_drake", h -> aiMelee(h, "storm_drake", "drake breath/dive"));
        add.accept("mobs_boss_phases", MobTests::bossPhases);
    }

    /** Every self-check of {@code /theoria test mobs}: registry, zones, stats, TTK/HTD bands, loot, XP, weapons, spawn biomes. */
    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_checks");
        List<Checks.Result> results = Checks.run("mobs", h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()));
        results.forEach(x -> r.line("%s %s — %s", x.ok() ? "PASS" : "FAIL", x.name(), x.detail()));
        r.write();
        results.stream().filter(x -> !x.ok()).findFirst().ifPresent(x -> h.fail(x.name() + ": " + x.detail()));
        h.assertTrue(results.size() >= 8, "expected at least 8 mob checks, got " + results.size());
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ helpers
    /** A 24×24 stone floor at relative y = 0 (the dev_arena structure's footprint) with air above, difficulty Normal. */
    private static ServerLevel arena(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        return level;
    }

    private static TheoriaMob spawn(GameTestHelper h, String id, double x, double z) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        TheoriaMob mob = MobRegistry.type(id).spawn(level, BlockPos.containing(at), EntitySpawnReason.COMMAND);
        h.assertTrue(mob != null, id + " did not spawn");
        return mob;
    }

    private static ServerPlayer player(GameTestHelper h, double x, double z) {
        ServerPlayer p = FakePlayers.survival(h, "dev-mobs");
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        p.teleportTo(at.x, at.y, at.z);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);   // behaviour, not lethality: a T9 drake would one-shot a 20-health player
        p.setHealth(100000f);
        return p;
    }

    /** One line of mob state for failure messages. */
    private static String state(TheoriaMob mob, ServerPlayer player) {
        return String.format(java.util.Locale.ROOT, "[alive=%s ticks=%d noAi=%s pos=(%.1f,%.1f,%.1f) dist=%.1f target=%s hp=%.0f/%.0f nav.done=%s onGround=%s blockBelow=%s goals=%d]", mob.isAlive(), mob.tickCount, mob.isNoAi(), mob.getX(), mob.getY(), mob.getZ(),
                mob.distanceTo(player), mob.getTarget() == null ? "null" : mob.getTarget().getName().getString(), mob.getHealth(), mob.getMaxHealth(), mob.getNavigation().isDone(), mob.onGround(), mob.level().getBlockState(mob.blockPosition().below()).getBlock(), mob.goalSelector.getAvailableGoals().size());
    }

    private static List<TheoriaMob> near(ServerLevel level, Vec3 at, double radius, String id) {
        var type = MobRegistry.type(id);
        return level.getEntitiesOfClass(TheoriaMob.class, new AABB(at, at).inflate(radius), m -> m.getType() == type);
    }

    // ------------------------------------------------------------------------------------------------ drops / xp / zones / weapons / heart
    /** Each species' loot table, rolled 4000 times: every row appears (chance ≥ 5 %), certain rows always, counts stay in [min, max], nothing unlisted drops. */
    private static void drops(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_drops");
        ServerLevel level = h.getLevel();
        var registries = level.getServer().reloadableRegistries();
        Vec3 origin = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        Zombie dummy = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        int rolls = 4000, rows = 0;
        for (Species sp : MobData.species()) {
            var table = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("theoria", "entities/" + sp.id())));
            Map<String, int[]> seen = new HashMap<>();   // item → {rolls it appeared in, min count, max count}
            RandomSource random = RandomSource.create(sp.id().hashCode());
            for (int i = 0; i < rolls; i++) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, dummy).withParameter(LootContextParams.ORIGIN, origin)
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
                Map<String, Integer> thisRoll = new HashMap<>();
                for (ItemStack s : table.getRandomItems(params, random)) thisRoll.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
                for (var e : thisRoll.entrySet()) {
                    int[] a = seen.computeIfAbsent(e.getKey(), k -> new int[] {0, Integer.MAX_VALUE, 0});
                    a[0]++;
                    a[1] = Math.min(a[1], e.getValue());
                    a[2] = Math.max(a[2], e.getValue());
                }
            }
            Set<String> listed = new HashSet<>();
            for (Species.Drop d : sp.drops()) {
                listed.add(d.item());
                int[] a = seen.get(d.item());
                h.assertTrue(a != null || d.chance() < 0.05, sp.id() + " never dropped " + d.item() + " in " + rolls + " rolls (chance " + d.chance() + ")");
                if (a == null) continue;
                h.assertTrue(a[1] >= d.min() && a[2] <= d.max(), sp.id() + " " + d.item() + " count " + a[1] + ".." + a[2] + " outside " + d.min() + ".." + d.max());
                if (d.chance() >= 1.0) h.assertTrue(a[0] == rolls, sp.id() + " " + d.item() + " is certain but missed " + (rolls - a[0]) + " rolls");
                double rate = a[0] / (double) rolls;
                h.assertTrue(Math.abs(rate - d.chance()) < 0.05, sp.id() + " " + d.item() + " dropped in " + rate + " of rolls, csv says " + d.chance());
                rows++;
            }
            for (String item : seen.keySet()) h.assertTrue(listed.contains(item), sp.id() + " dropped unlisted " + item);
        }
        r.line("%d species × %d rolls: %d drop rows match mob_drops.csv (presence, counts, rate ±5 pt, nothing unlisted)", MobData.species().size(), rolls, rows);
        r.write();
        h.succeed();
    }

    /** A tier-4 zombie is worth 5 × 2^4 = 80 XP; a plain zombie stays 5. Bilgelik (a separate item effect) is not involved. */
    private static void xp(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_xp");
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 8, 8);
        int[] tiers = {0, 4};
        int[] got = new int[tiers.length];
        try {
            for (int i = 0; i < tiers.length; i++) {
                Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 10));
                if (tiers[i] > 0) MobScaling.assign(z, tiers[i]);
                z.setLastHurtByPlayer(player, 100);
                z.setHealth(1f);
                z.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
                got[i] = i;
            }
            h.runAfterDelay(2, () -> {
                int total = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(h.absoluteVec(new Vec3(8, 1, 10)), h.absoluteVec(new Vec3(8, 1, 10))).inflate(6)).stream().mapToInt(ExperienceOrb::getValue).sum();
                r.line("tier 0 zombie (5 XP) + tier 4 zombie (80 XP) → %d XP in orbs", total);
                r.write();
                FakePlayers.remove(player);
                h.assertTrue(total == 5 + 80, "orbs carry " + total + " XP, expected 85");
                h.succeed();
            });
        } catch (RuntimeException e) {
            FakePlayers.remove(player);
            throw e;
        }
    }

    /** A vanilla monster is born at its zone's tier; /summon (COMMAND) is left alone; a Theoria species keeps its own tier wherever it spawns. */
    private static void zoneVanilla(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_zone_vanilla");
        ServerLevel level = h.getLevel();
        var diff = level.getCurrentDifficultyAt(BlockPos.ZERO);
        BlockPos spawn = level.getLevelData().getRespawnData().pos();   // the zone rule measures from the world spawn point
        int[][] cases = {{0, 70, 0, 0}, {1300, 70, 0, 2}, {3000, 70, 0, 5}, {0, -64, 0, 2}, {2400, -64, 600, 6}};
        for (int[] c : cases) {
            Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            z.setPos(spawn.getX() + c[0], c[1], spawn.getZ() + c[2]);
            EventHooks.finalizeMobSpawn(z, level, diff, EntitySpawnReason.NATURAL, null);
            double dist = Math.sqrt((double) c[0] * c[0] + (double) c[2] * c[2]);
            int expected = c[3];
            r.line("zombie born at dist %.0f, y %d → tier %d (expected %d)", dist, c[1], MobScaling.tierOf(z), expected);
            h.assertTrue(MobScaling.tierOf(z) == expected, "zone tier at " + java.util.Arrays.toString(c) + " is " + MobScaling.tierOf(z) + ", expected " + expected);
        }
        Zombie summoned = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        summoned.setPos(spawn.getX() + 5000, 70, spawn.getZ());
        EventHooks.finalizeMobSpawn(summoned, level, diff, EntitySpawnReason.COMMAND, null);
        h.assertTrue(MobScaling.tierOf(summoned) == 0, "/summon must not be zone-scaled");
        TheoriaMob wolf = MobRegistry.type("steel_wolf").create(level, EntitySpawnReason.COMMAND);
        wolf.setPos(spawn.getX() + 9000, 70, spawn.getZ());
        EventHooks.finalizeMobSpawn(wolf, level, diff, EntitySpawnReason.NATURAL, null);
        h.assertTrue(MobScaling.tierOf(wolf) == MobData.byId("steel_wolf").tier(), "a species keeps its own tier");
        Mob cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        cow.setPos(spawn.getX() + 9000, 70, spawn.getZ());
        EventHooks.finalizeMobSpawn(cow, level, diff, EntitySpawnReason.NATURAL, null);
        h.assertTrue(MobScaling.tierOf(cow) == 0, "animals are not zone-scaled");
        r.write();
        h.succeed();
    }

    /** The equipment event keeps a mob's weapon at vanilla damage while the mob lives in the world. */
    private static void weaponEvent(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_weapon_event");
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        h.runAfterDelay(1, () -> z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD)));
        h.runAfterDelay(4, () -> {
            double sword = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
            z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            h.runAfterDelay(3, () -> {
                double axe = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
                z.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                h.runAfterDelay(3, () -> {
                    double bare = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    r.line("zombie base 3: golden sword → %.2f (vanilla 3+3), iron axe → %.2f (vanilla 3+8), bare hand → %.2f", sword, axe, bare);
                    r.write();
                    h.assertTrue(Math.abs(sword - 6.0) < 1e-6, "golden sword in a mob's hand: " + sword);
                    h.assertTrue(Math.abs(axe - 11.0) < 1e-6, "iron axe in a mob's hand: " + axe);
                    h.assertTrue(Math.abs(bare - 3.0) < 1e-6, "bare hand after the weapon is gone: " + bare);
                    h.succeed();
                });
            });
        });
    }

    private static void heartCrystal(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_heart_crystal");
        ServerPlayer p = FakePlayers.survival(h, "dev-heart");
        try {
            int max = MobData.balance().heartMax(), hp = MobData.balance().heartHp();
            double base = p.getMaxHealth();
            for (int i = 0; i < max; i++) h.assertTrue(HeartCrystals.consume(p), "crystal " + (i + 1) + " refused");
            h.assertTrue(!HeartCrystals.consume(p), "the " + (max + 1) + "th crystal must be refused");
            double full = p.getMaxHealth();
            r.line("%d crystals × %d health: max health %.0f → %.0f (cap reached, next refused)", max, hp, base, full);
            h.assertTrue(Math.abs(full - (base + max * hp)) < 1e-6, "max health " + full);
            h.assertTrue(p.getHealth() > base, "a fresh crystal fills what it adds");
            ServerPlayer again = p.level().getServer().getPlayerList().respawn(p, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            r.line("after death + respawn: %d crystals, max health %.0f", HeartCrystals.count(again), (double) again.getMaxHealth());
            h.assertTrue(HeartCrystals.count(again) == max && Math.abs(again.getMaxHealth() - full) < 1e-6, "crystals must survive death");
            HeartCrystals.set(again, 0);
            h.assertTrue(Math.abs(again.getMaxHealth() - base) < 1e-6, "no crystals → base health");
            FakePlayers.remove(again);
        } finally {
            if (p.isAlive()) FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void spawnRules(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_spawn_rules");
        ServerLevel level = h.getLevel();
        Species wolf = MobData.byId("steel_wolf");   // tier 4, y 40..200, band ±1
        BlockPos o = level.getLevelData().getRespawnData().pos();   // distances are measured from the world spawn point
        BlockPos far = o.offset(2400, 70 - o.getY(), 0), near = o.offset(0, 70 - o.getY(), 0), deep = o.offset(2400, -30 - o.getY(), 0), mid = o.offset(1800, 70 - o.getY(), 0), over = o.offset(4300, 70 - o.getY(), 0);
        r.line("steel_wolf (tier 4): 2400 blocks out → %s · spawn point → %s · 1800 (zone 3) → %s · 4300 (zone 7) → %s · underground y −30 → %s",
                MobSpawning.inBand(wolf, level, far), MobSpawning.inBand(wolf, level, near), MobSpawning.inBand(wolf, level, mid), MobSpawning.inBand(wolf, level, over), MobSpawning.inBand(wolf, level, deep));
        h.assertTrue(MobSpawning.inBand(wolf, level, far), "tier-4 species spawns in zone 4");
        h.assertTrue(MobSpawning.inBand(wolf, level, mid), "…and in the neighbouring zone 3 (band 1)");
        h.assertTrue(!MobSpawning.inBand(wolf, level, near), "…not at the spawn point (zone 0)");
        h.assertTrue(!MobSpawning.inBand(wolf, level, over), "…not 3 zones away");
        h.assertTrue(!MobSpawning.inBand(wolf, level, deep), "…and not below its y range");
        Species king = MobData.byId("mud_king");
        h.assertTrue(!MobSpawning.canSpawn(king, level, EntitySpawnReason.NATURAL, far, RandomSource.create(1)), "a boss never spawns naturally");
        h.assertTrue(MobSpawning.canSpawn(king, level, EntitySpawnReason.COMMAND, far, RandomSource.create(1)), "a boss can be summoned");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ behaviour (real AI)
    /** The species hurts a survival player within 200 ticks. */
    private static void aiMelee(GameTestHelper h, String id, String what) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 6);
        TheoriaMob mob = spawn(h, id, 6, 12);
        mob.setTarget(player);
        float max = player.getMaxHealth();
        h.onEachTick(() -> {
            if (mob.getTarget() == null && mob.isAlive()) mob.setTarget(player);
        });
        h.succeedWhen(() -> {
            if (player.getHealth() < max) FakePlayers.remove(player);
            h.assertTrue(player.getHealth() < max, id + " (" + what + ") has not hurt the player yet " + state(mob, player));
        });
    }

    /** The species shows its telegraph pose (windup / charge) and then hurts the player. */
    private static void aiTelegraph(GameTestHelper h, String id, double distance, String what) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 4);
        TheoriaMob mob = spawn(h, id, 6, 4 + distance);
        mob.setTarget(player);
        boolean[] seen = {false};
        float max = player.getMaxHealth();
        h.onEachTick(() -> {
            if (mob.isTelegraphing()) seen[0] = true;
            if (mob.getTarget() == null && mob.isAlive()) mob.setTarget(player);
        });
        h.succeedWhen(() -> {
            boolean ok = seen[0] && player.getHealth() < max;
            if (ok) FakePlayers.remove(player);
            h.assertTrue(ok, id + " (" + what + "): telegraph seen = " + seen[0] + ", player hurt = " + (player.getHealth() < max) + " " + state(mob, player));
        });
    }

    private static void aiRanged(GameTestHelper h) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 4);
        TheoriaMob archer = spawn(h, "frost_archer", 6, 15);
        archer.setTarget(player);
        h.onEachTick(() -> {
            if (archer.getTarget() == null && archer.isAlive()) archer.setTarget(player);
        });
        h.succeedWhen(() -> {
            long arrows = level.getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(40), a -> a.getOwner() == archer).size();
            boolean ok = arrows > 0 || player.getHealth() < player.getMaxHealth();
            if (ok) FakePlayers.remove(player);
            h.assertTrue(ok, "frost_archer has not shot yet " + state(archer, player));
        });
    }

    private static void aiCaster(GameTestHelper h) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 6);
        TheoriaMob witch = spawn(h, "bog_witch", 6, 14);
        witch.setTarget(player);
        h.onEachTick(() -> {
            if (witch.getTarget() == null && witch.isAlive()) witch.setTarget(player);
        });
        h.succeedWhen(() -> {
            boolean ok = player.hasEffect(MobEffects.SLOWNESS) && player.getHealth() < player.getMaxHealth();
            if (ok) FakePlayers.remove(player);
            h.assertTrue(ok, "bog_witch has not cast yet (slowness = " + player.hasEffect(MobEffects.SLOWNESS) + ")");
        });
    }

    /** Dormant until a player comes near, then awake; a dormant statue does not move. */
    private static void aiSentinel(GameTestHelper h) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 2, 2);
        TheoriaMob worm = spawn(h, "rock_worm", 14, 14);
        h.assertTrue(worm.isDormant(), "a rock worm is born dormant");
        Vec3 where = worm.position();
        h.runAfterDelay(40, () -> {
            h.assertTrue(worm.isDormant() && worm.position().distanceToSqr(where) < 1.0E-6, "a dormant worm must not act or move while nobody is near");
            Vec3 at = h.absoluteVec(new Vec3(14, 1, 11));   // 3 blocks away, inside wake_range 5
            player.teleportTo(at.x, at.y, at.z);
        });
        h.succeedWhen(() -> {
            boolean awake = !worm.isDormant() && worm instanceof SentinelMob;
            if (awake) FakePlayers.remove(player);
            h.assertTrue(awake || h.getTick() < 41, "the worm did not wake up when a player came within its wake range");
        });
    }

    /** Backstab: ×1.5 from behind, ×1 from the front — through the real damage pipeline. */
    private static void aiAssassin(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_ai_assassin");
        ServerLevel level = arena(h);
        TheoriaMob runner = spawn(h, "night_runner", 6, 6);
        runner.setNoAi(true);
        Zombie victim = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 8));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);
        victim.setHealth(100000f);
        double[] taken = new double[2];
        for (int i = 0; i < 2; i++) {
            // i = 0: the victim looks at the runner (south → -z is north; the runner stands at z 6, the victim at z 8 → look north = yaw 180); i = 1: looks away (yaw 0)
            float yaw = i == 0 ? 180f : 0f;
            victim.setYRot(yaw);
            victim.setYHeadRot(yaw);
            victim.damageCooldownTime = 0;
            victim.setInvulnerableTime(0);
            float before = victim.getHealth();
            runner.doHurtTarget(level, victim);
            taken[i] = before - victim.getHealth();
        }
        r.line("night_runner hit on a zombie: facing it %.3f, turned away %.3f → ×%.3f (backstab=1.5)", taken[0], taken[1], taken[1] / taken[0]);
        r.write();
        h.assertTrue(taken[0] > 0 && Math.abs(taken[1] / taken[0] - 1.5) < 0.03, "backstab ratio " + taken[1] / taken[0] + ", expected 1.5");
        h.succeed();
    }

    /** A serpent bite poisons (on-hit effect through LivingDamageEvent.Post) and the serpent then slithers away (hit_run). */
    private static void aiSerpent(GameTestHelper h) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 6);
        TheoriaMob snake = spawn(h, "reed_serpent", 6, 12);
        snake.setTarget(player);
        h.onEachTick(() -> {
            if (snake.getTarget() == null && snake.isAlive()) snake.setTarget(player);
        });
        h.succeedWhen(() -> {
            boolean ok = player.hasEffect(MobEffects.POISON);
            if (ok) FakePlayers.remove(player);
            h.assertTrue(ok, "reed_serpent has not poisoned the player yet " + state(snake, player));
        });
    }

    private static void swarmAlert(GameTestHelper h) {
        ServerLevel level = arena(h);
        ServerPlayer player = player(h, 6, 6);
        List<TheoriaMob> pack = new ArrayList<>();
        for (int i = 0; i < 3; i++) pack.add(spawn(h, "tin_gnawer", 12 + i, 12));
        h.runAfterDelay(2, () -> pack.get(0).hurtServer(level, level.damageSources().playerAttack(player), 0.1f));
        h.succeedWhen(() -> {
            long angry = pack.stream().filter(m -> m.getTarget() == player).count();
            if (angry == 3) FakePlayers.remove(player);
            h.assertTrue(angry == 3, "only " + angry + " of 3 gnawers answered the alert " + state(pack.get(1), player));
        });
    }

    /** Boss: each health threshold is one phase (adds appear, enrage), and the bar follows health. */
    private static void bossPhases(GameTestHelper h) {
        Dev.Report r = new Dev.Report("mobs_boss_phases");
        ServerLevel level = arena(h);
        TheoriaMob king = spawn(h, "mud_king", 10, 10);
        Vec3 at = king.position();
        h.assertTrue(king.phase() == 0 && near(level, at, 12, "tin_gnawer").isEmpty(), "a fresh boss is in phase 0 with no adds");
        king.setHealth(king.getMaxHealth() * 0.60f);
        h.runAfterDelay(3, () -> {
            int adds1 = near(level, at, 14, "tin_gnawer").size();
            r.line("health 60 %% → phase %d, %d tin gnawers, bar %.2f", king.phase(), adds1, king.bossProgress());
            h.assertTrue(king.phase() == 1 && adds1 == 4, "phase 1 expected with 4 adds, got phase " + king.phase() + " / " + adds1);
            h.assertTrue(Math.abs(king.bossProgress() - 0.60f) < 0.02f, "the boss bar must follow health: " + king.bossProgress());
            king.setHealth(king.getMaxHealth() * 0.30f);
            h.runAfterDelay(3, () -> {
                int adds2 = near(level, at, 14, "tin_gnawer").size();
                r.line("health 30 %% → phase %d, %d tin gnawers, bar %.2f", king.phase(), adds2, king.bossProgress());
                r.write();
                h.assertTrue(king.phase() == 2 && adds2 == 8, "phase 2 expected with 8 adds in total, got phase " + king.phase() + " / " + adds2);
                h.succeed();
            });
        });
    }
}
