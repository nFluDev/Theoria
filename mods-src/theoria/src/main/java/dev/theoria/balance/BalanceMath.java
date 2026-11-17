package dev.theoria.balance;

/** The balance formulas as pure functions. Mirror of tools/balance/tiers.py ({@code dr}); {@code sim_cases.csv} keeps them in step. */
public final class BalanceMath {
    private BalanceMath() {}

    /**
     * Share of a hit that armor removes, before the weapon's {@code armor_effectiveness} step (docs/06 §6):
     * {@code min(cap, A / (A + k1 + k2·H·tau))}, {@code tau = half / (half + T)}.
     *
     * @param armor     {@code LivingEntity#getArmorValue()} (already rounded down)
     * @param toughness the {@code armor_toughness} attribute
     * @param hit       H = damage before armor (ADR-003: after shield/helmet/freeze, only the part above i-frame damage)
     */
    public static double armorFraction(CombatParams c, int armor, double toughness, double hit) {
        if (armor <= 0 || hit <= 0) return 0.0;
        double tau = c.toughnessHalf() / (c.toughnessHalf() + Math.max(0.0, toughness));
        return Math.min(c.armorCap(), armor / (armor + c.armorK1() + c.armorK2() * hit * tau));
    }
}
