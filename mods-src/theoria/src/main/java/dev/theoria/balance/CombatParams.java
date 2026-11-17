package dev.theoria.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Damage-reduction constants (docs/06 §6, §8). Loaded from {@code data/theoria/balance/combat/default.json}, which
 * {@code tools/balance/tiers.py} generates from data/balance/combat.csv — never edit the numbers here or in the JSON.
 */
public record CombatParams(double armorK1, double armorK2, double armorCap, double toughnessHalf, double enchScale) {
    private static final Codec<Double> POSITIVE = Codec.doubleRange(1.0e-9, 1.0e9);
    private static final Codec<Double> FRACTION = Codec.doubleRange(1.0e-9, 1.0);

    public static final Codec<CombatParams> CODEC = RecordCodecBuilder.create(i -> i.group(
            POSITIVE.fieldOf("armor_k1").forGetter(CombatParams::armorK1),
            POSITIVE.fieldOf("armor_k2").forGetter(CombatParams::armorK2),
            FRACTION.fieldOf("armor_cap").forGetter(CombatParams::armorCap),
            POSITIVE.fieldOf("toughness_half").forGetter(CombatParams::toughnessHalf),
            FRACTION.fieldOf("ench_scale").forGetter(CombatParams::enchScale)).apply(i, CombatParams::new));
}
