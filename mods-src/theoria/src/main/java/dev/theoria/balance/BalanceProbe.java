package dev.theoria.balance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * In-game measurement of the damage pipeline against {@code data/balance/sim_cases.csv} (the simulator's expected values,
 * shipped in the jar as {@code /theoria/balance/sim_cases.csv}). Used by {@code /theoria test balance} and the GameTests.
 * <p>
 * One measurement = one real {@code hurtServer} call, so the whole chain is exercised: mob damage multiplier (tiered
 * attacker), armor/toughness attributes, Resistance, Protection, the {@code Reduction.ARMOR}/{@code ENCHANTMENTS} modifiers.
 * Everything the victim is given is taken away again; a throw-away victim ({@link #tempVictim}) never enters the world.
 */
public final class BalanceProbe {
    private static final String CASES_RESOURCE = "/theoria/balance/sim_cases.csv";
    /** Above any single hit in sim_cases.csv (max ≈ 200), small enough for float precision (ulp 0,00006 at 1000: 0,1 damage ±0,06 %). */
    private static final float VICTIM_HEALTH = 1000f;

    /**
     * One sim_cases.csv row. {@code tier > 0}: the attacker is a zombie of that tier and {@code hit} is the vanilla base
     * damage — the tier multiplier must produce {@code raw}; {@code tier = 0}: {@code hit} is delivered as is.
     */
    public record Case(String name, int tier, double hit, int armor, double toughness, int epf, int resistance,
            boolean bypassArmor, double raw, double afterArmor, double expected, String note) {}

    private BalanceProbe() {}

    public static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        try (InputStream in = BalanceProbe.class.getResourceAsStream(CASES_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + CASES_RESOURCE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> header = splitCsv(reader.readLine());
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank()) continue;
                List<String> v = splitCsv(line);
                if (v.size() != header.size()) throw new IllegalStateException("sim_cases.csv: bad row " + line);
                cases.add(new Case(v.get(col(header, "case")), Integer.parseInt(v.get(col(header, "tier"))),
                        Double.parseDouble(v.get(col(header, "hit"))), Integer.parseInt(v.get(col(header, "armor"))),
                        Double.parseDouble(v.get(col(header, "toughness"))), Integer.parseInt(v.get(col(header, "epf"))),
                        Integer.parseInt(v.get(col(header, "resistance"))), Boolean.parseBoolean(v.get(col(header, "bypass_armor"))),
                        Double.parseDouble(v.get(col(header, "raw"))), Double.parseDouble(v.get(col(header, "after_armor"))),
                        Double.parseDouble(v.get(col(header, "final"))), v.get(col(header, "note"))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cases;
    }

    public static Case caseNamed(String name) {
        return cases().stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sim case '" + name + "'"));
    }

    /** A silent, AI-less pig that is <em>not</em> added to the world — safe to hurt in a live survival world. */
    public static LivingEntity tempVictim(ServerLevel level, Vec3 at) {
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        if (pig == null) throw new IllegalStateException("cannot create the probe victim");
        pig.setNoAi(true);
        pig.setSilent(true);
        pig.setPos(at);
        return pig;
    }

    /**
     * Hits {@code victim} once the way {@code c} says and returns the health it lost. The victim must not have Resistance.
     * Attributes, head slot, effects, health and the i-frame counter are restored afterwards.
     */
    public static double measure(ServerLevel level, LivingEntity victim, Case c) {
        if (victim.hasEffect(MobEffects.RESISTANCE)) throw new IllegalStateException("probe victim already has Resistance");
        AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
        AttributeInstance toughness = victim.getAttribute(Attributes.ARMOR_TOUGHNESS);
        AttributeInstance maxHealth = victim.getAttribute(Attributes.MAX_HEALTH);
        double oldArmor = armor.getBaseValue(), oldToughness = toughness.getBaseValue(), oldMax = maxHealth.getBaseValue();
        float oldHealth = victim.getHealth();
        ItemStack oldHead = victim.getItemBySlot(EquipmentSlot.HEAD);
        int oldCooldown = victim.damageCooldownTime;
        Zombie attacker = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (attacker == null) throw new IllegalStateException("cannot create the probe attacker");
        try {
            armor.setBaseValue(c.armor());
            toughness.setBaseValue(c.toughness());
            maxHealth.setBaseValue(VICTIM_HEALTH);
            victim.setHealth(VICTIM_HEALTH);
            // Protection works from any armor slot, so a stick carries the EPF without adding armor points.
            ItemStack head = ItemStack.EMPTY;
            if (c.epf() > 0) {
                head = new ItemStack(Items.STICK);
                head.enchant(protection(level), c.epf());
            }
            victim.setItemSlot(EquipmentSlot.HEAD, head);
            if (c.resistance() > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 2000, c.resistance() - 1, false, false, false));
            }
            if (c.tier() > 0) MobScaling.assign(attacker, c.tier());
            DamageSource source = c.bypassArmor() ? level.damageSources().indirectMagic(attacker, attacker) : level.damageSources().mobAttack(attacker);
            victim.damageCooldownTime = 0;
            victim.setInvulnerableTime(0);
            float before = victim.getHealth();
            victim.hurtServer(level, source, (float) c.hit());
            if (victim.isDeadOrDying()) throw new IllegalStateException("probe victim died on case " + c.name() + ": raise VICTIM_HEALTH");
            return before - victim.getHealth();
        } finally {
            victim.removeEffect(MobEffects.RESISTANCE);
            victim.setItemSlot(EquipmentSlot.HEAD, oldHead);
            armor.setBaseValue(oldArmor);
            toughness.setBaseValue(oldToughness);
            maxHealth.setBaseValue(oldMax);
            victim.setHealth(Math.min(oldHealth, victim.getMaxHealth()));
            victim.damageCooldownTime = oldCooldown;
        }
    }

    /** |measured − expected| relative to expected (absolute for expected ≈ 0). */
    public static double relativeError(double measured, double expected) {
        return Math.abs(measured - expected) / Math.max(Math.abs(expected), 1.0e-6);
    }

    private static Holder<Enchantment> protection(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
    }

    private static int col(List<String> header, String name) {
        int i = header.indexOf(name);
        if (i < 0) throw new IllegalStateException("sim_cases.csv: no column '" + name + "'");
        return i;
    }

    /** Minimal CSV line split with double-quoted fields (the note column has commas). */
    public static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
