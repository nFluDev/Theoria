package dev.theoria.balance;

import dev.theoria.core.check.Checks;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.phys.Vec3;

/**
 * Area "balance" of {@code /theoria test}: the balance core measured in the running game. Every check uses throw-away
 * entities that never enter the world and restores what it touches, so it is safe in a real survival world.
 * Expected values come from sim_cases.csv; the tolerance is the Phase 2 criterion (±10 %).
 */
public final class BalanceChecks {
    /** Phase 2 DoD: in-game measurement within ±10 % of the simulation. */
    static final double TOLERANCE = 0.10;

    private BalanceChecks() {}

    public static void init() {
        Checks.register("balance", "data", source -> {
            Checks.require(TierRegistry.loaded(), "balance data not loaded");
            CombatParams c = TierRegistry.combat();
            return String.format(Locale.ROOT, "%d tiers, armor k1=%s k2=%s cap=%s, toughness half=%s, ench scale=%s",
                    TierRegistry.tierCount(), c.armorK1(), c.armorK2(), c.armorCap(), c.toughnessHalf(), c.enchScale());
        });
        Checks.register("balance", "attribute_caps", source -> {
            TechnicalCaps.Caps caps = TechnicalCaps.caps();
            requireCap("armor", Attributes.ARMOR.value(), caps.armor());
            requireCap("armor_toughness", Attributes.ARMOR_TOUGHNESS.value(), caps.armorToughness());
            requireCap("max_health", Attributes.MAX_HEALTH.value(), caps.health());
            requireCap("attack_damage", Attributes.ATTACK_DAMAGE.value(), caps.attackDamage());
            return String.format(Locale.ROOT, "armor ≤ %.0f, toughness ≤ %.0f, max health ≤ %.0f, attack damage ≤ %.0f",
                    caps.armor(), caps.armorToughness(), caps.health(), caps.attackDamage());
        });
        // The Phase 2 criterion on its own line: armor 300 victim, tier-8 zombie hit → 26.019 raw → 11.514
        Checks.register("balance", "armor300_vs_t8", source -> measureCases(source.getLevel(), source.getPosition(),
                List.of(BalanceProbe.caseNamed("armor300_vs_t8"))));
        Checks.register("balance", "sim_cases", source -> measureCases(source.getLevel(), source.getPosition(), BalanceProbe.cases()));
        Checks.register("balance", "mob_scaling", source -> {
            ServerLevel level = source.getLevel();
            LivingEntity mob = BalanceProbe.tempVictim(level, source.getPosition());
            double baseHealth = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
            int tierIdx = Math.min(8, TierRegistry.tierCount() - 1);
            Tier tier = TierRegistry.tier(tierIdx);
            MobScaling.assign(mob, tierIdx);
            Checks.require(near(mob.getMaxHealth(), baseHealth * tier.mob().hpMult()),
                    "tier " + tierIdx + " max health " + mob.getMaxHealth() + " ≠ " + baseHealth * tier.mob().hpMult());
            Checks.require(near(mob.getHealth(), mob.getMaxHealth()), "a mob tiered at full health must stay at full health");
            Checks.require(MobScaling.tierOf(mob) == tierIdx, "tier attachment not set");
            String detail = String.format(Locale.ROOT, "tier %d: max health %.0f → %.0f (×%s)", tierIdx, baseHealth, (double) mob.getMaxHealth(), tier.mob().hpMult());
            MobScaling.assign(mob, 0);
            Checks.require(near(mob.getMaxHealth(), baseHealth) && MobScaling.tierOf(mob) == 0, "tier 0 must restore the vanilla mob");
            return detail;
        });
        Checks.register("balance", "heal_scaling", source -> {
            LivingEntity mob = BalanceProbe.tempVictim(source.getLevel(), source.getPosition());
            double baseHealth = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
            mob.setHealth(1.0f);
            mob.heal(1.0f);
            Checks.require(near(mob.getHealth(), 2.0f), "an untiered mob must heal exactly what it is given, got " + mob.getHealth());
            int tierIdx = Math.min(8, TierRegistry.tierCount() - 1);
            MobScaling.assign(mob, tierIdx);
            float before = mob.getHealth();
            mob.heal(1.0f);
            float healed = mob.getHealth() - before;
            double expected = mob.getMaxHealth() / baseHealth;
            Checks.require(Math.abs(healed - expected) / expected < 0.005, "tier " + tierIdx + " heal(1) gave " + healed + ", expected " + expected);
            return String.format(Locale.ROOT, "heal(1): ×1 untiered, ×%.2f at tier %d (= max health / base)", expected, tierIdx);
        });
    }

    private static String measureCases(ServerLevel level, Vec3 at, List<BalanceProbe.Case> cases) {
        LivingEntity victim = BalanceProbe.tempVictim(level, at);
        StringBuilder bad = new StringBuilder();
        double worst = 0;
        BalanceProbe.Case worstCase = cases.get(0);
        double worstMeasured = 0;
        for (BalanceProbe.Case c : cases) {
            double measured = BalanceProbe.measure(level, victim, c);
            double error = BalanceProbe.relativeError(measured, c.expected());
            if (error >= worst) {
                worst = error;
                worstCase = c;
                worstMeasured = measured;
            }
            if (error > TOLERANCE) {
                bad.append(String.format(Locale.ROOT, "%s: measured %.4f, expected %.4f (%.1f%%); ", c.name(), measured, c.expected(), error * 100));
            }
        }
        Checks.require(bad.isEmpty(), bad.toString().strip());
        if (cases.size() == 1) {
            return String.format(Locale.ROOT, "%s: measured %.4f, expected %.4f (±%.0f%%)", worstCase.name(), worstMeasured, worstCase.expected(), TOLERANCE * 100);
        }
        return String.format(Locale.ROOT, "%d cases within ±%.0f%%, worst %s %.3f%%", cases.size(), TOLERANCE * 100, worstCase.name(), worst * 100);
    }

    private static void requireCap(String name, Object attribute, double cap) {
        double max = ((RangedAttribute) attribute).getMaxValue();
        Checks.require(max >= cap, name + " ceiling is " + max + ", expected ≥ " + cap + " (technical caps not applied)");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1.0e-3 * Math.max(1.0, Math.abs(b));
    }
}
