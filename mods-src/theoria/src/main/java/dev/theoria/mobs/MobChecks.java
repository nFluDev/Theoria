package dev.theoria.mobs;

import dev.theoria.balance.BalanceProbe;
import dev.theoria.balance.MobScaling;
import dev.theoria.balance.Tier;
import dev.theoria.balance.TierRegistry;
import dev.theoria.core.check.Checks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Area "mobs" of {@code /theoria test}: the species table measured in the running game. Every check uses throw-away entities that never enter the
 * world, so it is safe in a survival world. Expected values are the jar-embedded copies of {@code data/balance/mob_ttk.csv} and
 * {@code mob_zone_cases.csv} (tools/balance/mobs.py): the point is that the generated table, the registered types, the loaded datapack and the
 * balance core all agree with the single source.
 */
public final class MobChecks {
    private static final double TOLERANCE = 0.03;   // mob_ttk.csv rounds to 2 decimals

    private MobChecks() {}

    static void init() {
        Checks.register("mobs", "data", source -> {
            List<Species> all = MobData.species();
            long bosses = all.stream().filter(Species::boss).count();
            Checks.require(all.size() >= 30, "need ≥ 30 species, have " + all.size());
            Checks.require(bosses >= 2, "need ≥ 2 bosses, have " + bosses);
            Set<Archetype> archetypes = all.stream().map(Species::archetype).collect(Collectors.toSet());
            Checks.require(archetypes.size() == Archetype.values().length, "archetypes used: " + archetypes.size() + "/12");
            Set<String> families = all.stream().map(Species::family).collect(Collectors.toSet());
            Checks.require(families.size() == 8, "families used: " + families.size() + "/8");
            return all.size() + " species (" + bosses + " bosses), " + archetypes.size() + " archetypes, " + families.size() + " families, tiers "
                    + all.stream().mapToInt(Species::tier).min().getAsInt() + "–" + all.stream().mapToInt(Species::tier).max().getAsInt();
        });
        Checks.register("mobs", "registry", MobChecks::registry);
        Checks.register("mobs", "zones", source -> {
            int n = 0;
            for (Map<String, String> row : csv("/theoria/balance/mob_zone_cases.csv")) {
                MobZones.Dimension dim = MobZones.Dimension.valueOf(row.get("dim").toUpperCase(Locale.ROOT));
                int tier = MobZones.tier(dim, Double.parseDouble(row.get("dist")), Integer.parseInt(row.get("y")));
                Checks.require(tier == Integer.parseInt(row.get("tier")), "zone " + row + " → " + tier);
                n++;
            }
            return n + " zone cases agree with tools/balance/mobs.py";
        });
        Checks.register("mobs", "stats", MobChecks::stats);
        Checks.register("mobs", "ttk", MobChecks::ttk);
        Checks.register("mobs", "loot", MobChecks::loot);
        Checks.register("mobs", "xp", source -> {
            Checks.require(MobHooks.scaledExperience(5, 0) == 5, "tier 0 XP must stay vanilla");
            int t13 = MobHooks.scaledExperience(5, 13);
            Checks.require(t13 == 40960, "tier 13 normal-class XP = " + t13 + ", expected 40960");
            return "tier 0: 5 · tier 4: " + MobHooks.scaledExperience(5, 4) + " · tier 13: " + t13;
        });
        Checks.register("mobs", "weapons", MobChecks::weapons);
        Checks.register("mobs", "spawn_biomes", MobChecks::spawnBiomes);
    }

    // ------------------------------------------------------------------------------------------------ registry
    private static String registry(net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int n = 0;
        for (Species sp : MobData.species()) {
            var type = MobRegistry.type(sp.id());
            Checks.require(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals(sp.id()), sp.id() + " not registered under its id");
            Checks.require(DefaultAttributes.hasSupplier(type), sp.id() + " has no attribute supplier");
            Checks.require(MobRegistry.EGGS.containsKey(sp.id()) && BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("theoria", sp.id() + "_spawn_egg")), sp.id() + " has no spawn egg");
            TheoriaMob mob = type.create(level, EntitySpawnReason.COMMAND);
            Checks.require(mob != null, sp.id() + " cannot be created");
            Checks.require(mob.getClass().getSimpleName().toLowerCase(Locale.ROOT).startsWith(sp.archetype().name().toLowerCase(Locale.ROOT)), sp.id() + " is a " + mob.getClass().getSimpleName() + ", expected the " + sp.archetype() + " archetype");
            Checks.require(Math.abs(mob.getAttributeBaseValue(Attributes.SCALE) - sp.scale()) < 1e-6, sp.id() + " scale attribute");
            mob.discard();
            n++;
        }
        TagKey<net.minecraft.world.entity.EntityType<?>> species = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("theoria", "species"));
        long tagged = BuiltInRegistries.ENTITY_TYPE.get(Identifier.fromNamespaceAndPath("theoria", MobData.species().get(0).id())).map(h -> h.is(species) ? 1 : 0).orElse(0);
        Checks.require(tagged == 1, "#theoria:species does not contain " + MobData.species().get(0).id());
        return n + " entity types, spawn eggs, attribute suppliers and archetype classes";
    }

    // ------------------------------------------------------------------------------------------------ stats / ttk
    private static String stats(net.minecraft.commands.CommandSourceStack source) {
        Checks.require(TierRegistry.loaded(), "balance data not loaded");
        ServerLevel level = source.getLevel();
        int n = 0;
        for (Map<String, String> row : csv("/theoria/balance/mob_ttk.csv")) {
            Species sp = MobData.byId(row.get("id"));
            Checks.require(sp != null, "mob_ttk.csv names unknown species " + row.get("id"));
            TheoriaMob mob = MobRegistry.type(sp.id()).create(level, EntitySpawnReason.COMMAND);
            MobScaling.assign(mob, sp.tier());
            Checks.require(near(mob.getMaxHealth(), Double.parseDouble(row.get("hp")), 0.005), sp.id() + " max health " + mob.getMaxHealth() + " vs " + row.get("hp"));
            Checks.require(near(mob.getAttributeValue(Attributes.ARMOR), Double.parseDouble(row.get("armor")), 0.005), sp.id() + " armor " + mob.getAttributeValue(Attributes.ARMOR) + " vs " + row.get("armor"));
            Checks.require(near(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), sp.dmg(), 1e-6), sp.id() + " attack damage attribute is the base value");
            Checks.require(MobScaling.tierOf(mob) == sp.tier(), sp.id() + " tier");
            mob.discard();
            n++;
        }
        return n + " species: tier-scaled max health and armor match mob_ttk.csv; base attack damage kept";
    }

    /**
     * The balance bands measured in the game: the reference player of the species' tier hits the mob (hits to kill) and the mob hits the reference
     * player (hits to die), through the real damage pipeline. Must agree with {@code tools/balance/mobs.py} (and so with docs/06 §1 bands).
     */
    private static String ttk(net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        LivingEntity victim = BalanceProbe.tempVictim(level, source.getPosition());
        int n = 0;
        double worst = 0;
        for (Map<String, String> row : csv("/theoria/balance/mob_ttk.csv")) {
            Species sp = MobData.byId(row.get("id"));
            Tier t = TierRegistry.tier(sp.tier());
            // kill side: ref attack = tier sword × (1 + ref bonus), full charge, no crit; the mob keeps its own armor and health
            TheoriaMob mob = MobRegistry.type(sp.id()).create(level, EntitySpawnReason.COMMAND);
            MobScaling.assign(mob, sp.tier());
            var attacker = new net.minecraft.world.entity.monster.zombie.Zombie(EntityTypes.ZOMBIE, level);   // tier-0 stand-in: the source needs a living entity, not a player
            float attack = (float) (t.swordDamage() * (1 + t.ref().bonus()));
            double maxHp = mob.getMaxHealth();
            double taken = taken(level, mob, level.damageSources().mobAttack(attacker), attack);
            double hitsToKill = maxHp / taken;
            mob.discard();
            // die side: the species' base hit, tier multiplier by the pipeline, against the ref loadout (armor, toughness, EPF, health)
            boolean caster = Boolean.parseBoolean(row.get("bypass")) || "1".equals(row.get("bypass"));
            int refArmor = (int) Math.floor(t.armor().total() * (1 + t.ref().bonus()));
            double lost = BalanceProbe.measure(level, victim, new BalanceProbe.Case("mob:" + sp.id(), sp.tier(), sp.dmg(), refArmor, t.toughness(), t.ref().epf(), 0, caster, 0, 0, 0, ""));
            // measure() uses a 1000-health victim; the ref health is what the player has
            double hitsToDie = t.ref().health() / lost;
            double expectKill = Double.parseDouble(row.get("htk")), expectDie = Double.parseDouble(row.get("htd"));
            worst = Math.max(worst, Math.max(Math.abs(hitsToKill - expectKill) / expectKill, Math.abs(hitsToDie - expectDie) / expectDie));
            Checks.require(Math.abs(hitsToKill - expectKill) / expectKill <= TOLERANCE, sp.id() + " hits to kill " + fmt(hitsToKill) + ", mob_ttk.csv " + expectKill);
            Checks.require(Math.abs(hitsToDie - expectDie) / expectDie <= TOLERANCE, sp.id() + " hits to die " + fmt(hitsToDie) + ", mob_ttk.csv " + expectDie);
            n++;
        }
        return String.format(Locale.ROOT, "%d species: hits to kill / die within %.1f %% of mob_ttk.csv (worst %.2f %%)", n, TOLERANCE * 100, worst * 100);
    }

    /** Health the hit takes from {@code mob}, which keeps its own (huge, for bosses) health. */
    private static double taken(ServerLevel level, LivingEntity mob, DamageSource source, float amount) {
        mob.damageCooldownTime = 0;
        mob.setInvulnerableTime(0);
        float before = mob.getHealth();
        mob.hurtServer(level, source, amount);
        return before - mob.getHealth();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------------------------------------ loot / weapons / biomes
    private static String loot(net.minecraft.commands.CommandSourceStack source) {
        var registries = source.getServer().reloadableRegistries();
        int rows = 0;
        for (Species sp : MobData.species()) {
            var table = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("theoria", "entities/" + sp.id())));
            Checks.require(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "loot table theoria:entities/" + sp.id() + " missing");
            for (Species.Drop d : sp.drops()) {
                Checks.require(BuiltInRegistries.ITEM.containsKey(Identifier.parse(d.item())), sp.id() + " drops unknown item " + d.item());
                rows++;
            }
        }
        return MobData.species().size() + " loot tables, " + rows + " drop rows with registered items";
    }

    private static String weapons(net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Map<net.minecraft.world.item.Item, Double> expect = new HashMap<>();
        expect.put(Items.GOLDEN_SWORD, 3.0 + 3.0);   // zombie base 3 + (vanilla golden sword total 4 − 1)
        expect.put(Items.IRON_SWORD, 3.0 + 5.0);
        expect.put(Items.IRON_AXE, 3.0 + 8.0);
        expect.put(Items.GOLDEN_AXE, 3.0 + 6.0);
        expect.put(Items.DIAMOND_SWORD, 3.0 + 6.0);
        StringBuilder out = new StringBuilder();
        for (var e : expect.entrySet()) {
            Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            ItemStack stack = new ItemStack(e.getKey());
            z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
            // an entity outside the world never ticks: apply the item's modifiers the way LivingEntity#detectEquipmentUpdates would
            stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                AttributeInstance instance = z.getAttribute(attribute);
                if (instance != null) instance.addOrReplacePermanentModifier(modifier);
            });
            double raw = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
            MobHooks.normalizeWeapon(z);
            double normalized = z.getAttributeValue(Attributes.ATTACK_DAMAGE);
            Checks.require(near(normalized, e.getValue(), 1e-6), BuiltInRegistries.ITEM.getKey(e.getKey()) + ": mob hit " + normalized + ", vanilla " + e.getValue() + " (item table would give " + raw + ")");
            out.append(BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).append(' ').append(fmt(raw)).append("→").append(fmt(normalized)).append(' ');
            z.discard();
        }
        return "weapon in a mob's hand deals vanilla damage: " + out.toString().trim();
    }

    private static String spawnBiomes(net.minecraft.commands.CommandSourceStack source) {
        var biomes = source.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        int n = 0;
        List<String> missing = new ArrayList<>();
        for (Species sp : MobData.species()) {
            if (sp.biomes().isEmpty()) continue;
            var type = MobRegistry.type(sp.id());
            // the generated tag #theoria:spawn/<id> must resolve to biomes, and the neoforge:add_spawns modifier must have put the species there
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("theoria", "spawn/" + sp.id()));
            var holders = biomes.get(tag);
            if (holders.isEmpty() || holders.get().size() == 0) {
                missing.add(sp.id() + " (no biome in #theoria:spawn/" + sp.id() + ")");
                continue;
            }
            boolean added = holders.get().stream().anyMatch(h -> spawnsOf(h.value(), sp).stream().anyMatch(d -> d.value().type() == type));
            if (!added) missing.add(sp.id() + " (biome modifier not applied)");
            n++;
        }
        Checks.require(missing.isEmpty(), "spawn setup broken: " + missing);
        return n + " species spawn naturally in their biome tag (add_spawns applied)";
    }

    /** 26.3: a biome's natural spawns are the {@code NATURAL_MOB_SPAWNS} environment attribute; NeoForge's biome modifiers write into the modified map. */
    private static List<net.minecraft.util.random.Weighted<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData>> spawnsOf(Biome biome, Species sp) {
        var entry = biome.getModifiedEnvironmentAttributes().get(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        if (entry == null) return List.of();
        return ((net.minecraft.world.level.biome.MobSpawnSettings) entry.argument()).getMobsToSpawn(sp.category()).unwrap();
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static boolean near(double a, double b, double rel) {
        return Math.abs(a - b) <= Math.max(1e-9, rel * Math.max(Math.abs(a), Math.abs(b)));
    }

    /** First row is the header; {@code resource} is a jar-embedded CSV. */
    static List<Map<String, String>> csv(String resource) {
        try (InputStream in = MobChecks.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> header = BalanceProbe.splitCsv(reader.readLine());
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank()) continue;
                List<String> cells = BalanceProbe.splitCsv(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
