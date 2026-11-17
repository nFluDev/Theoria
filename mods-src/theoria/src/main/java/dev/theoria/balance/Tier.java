package dev.theoria.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One rung of the tier ladder (docs/06 §2). One datapack file per tier: {@code data/theoria/balance/tier/<id>.json},
 * generated from data/balance/tiers.csv by tools/balance/tiers.py. Adding a tier = adding a row there.
 * Derived columns of the CSV (set total, expected mob HP/damage, hits-to-die…) are not stored: compute them.
 */
public record Tier(String id, int idx, Armor armor, double toughness, int armorDurability, double swordDamage,
        int toolDurability, Mob mob, Ref ref) {

    /** Armor points per piece; the set's armor is their sum. */
    public record Armor(int helmet, int chestplate, int leggings, int boots) {
        private static final Codec<Integer> POINTS = Codec.intRange(1, 1_000_000);
        static final MapCodec<Armor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                POINTS.fieldOf("helmet").forGetter(Armor::helmet),
                POINTS.fieldOf("chestplate").forGetter(Armor::chestplate),
                POINTS.fieldOf("leggings").forGetter(Armor::leggings),
                POINTS.fieldOf("boots").forGetter(Armor::boots)).apply(i, Armor::new));

        public int total() {
            return helmet + chestplate + leggings + boots;
        }
    }

    /** The three multipliers a mob of this tier gets (docs/06 §4). */
    public record Mob(double hpMult, double dmgMult, double bypassMult) {
        private static final Codec<Double> MULT = Codec.doubleRange(1.0, 1.0e6);
        static final MapCodec<Mob> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                MULT.fieldOf("mob_hp_mult").forGetter(Mob::hpMult),
                MULT.fieldOf("mob_dmg_mult").forGetter(Mob::dmgMult),
                MULT.fieldOf("mob_bypass_mult").forGetter(Mob::bypassMult)).apply(i, Mob::new));
    }

    /** The reference ("expected") player budget at this tier (docs/06 §3) — the target Phases 7 and 10 must hit. */
    public record Ref(int health, double bonus, int epf) {
        static final MapCodec<Ref> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 1_000_000).fieldOf("ref_health").forGetter(Ref::health),
                Codec.doubleRange(0.0, 10.0).fieldOf("ref_bonus").forGetter(Ref::bonus),
                Codec.intRange(0, 255).fieldOf("ref_epf").forGetter(Ref::epf)).apply(i, Ref::new));
    }

    public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Tier::id),
            Codec.intRange(0, 1000).fieldOf("idx").forGetter(Tier::idx),
            Armor.CODEC.forGetter(Tier::armor),
            Codec.doubleRange(0.0, 1.0e6).fieldOf("toughness").forGetter(Tier::toughness),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("armor_durability").forGetter(Tier::armorDurability),
            Codec.doubleRange(0.0, 1.0e6).fieldOf("sword_damage").forGetter(Tier::swordDamage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("tool_durability").forGetter(Tier::toolDurability),
            Mob.CODEC.forGetter(Tier::mob),
            Ref.CODEC.forGetter(Tier::ref)).apply(i, Tier::new));
}
