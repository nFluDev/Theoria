package dev.theoria.mobs;

import dev.theoria.balance.MobScaling;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.MobCategory;

/**
 * One row of {@code data/mobs.csv} as the game sees it ({@code theoria/mobs.json}, generator/mobs.py). {@code hp/dmg/armor} are the tier-0
 * equivalent base values: the species' {@code tier} picks the three multipliers ({@link MobScaling}, docs/06 §4).
 */
public record Species(String id, String family, Archetype archetype, String rig, int tier, String cls, boolean boss,
        double hp, double dmg, double armor, double speed, double follow, double kbRes, double scale, float width, float height,
        int color, int accent, List<String> biomes, int minY, int maxY, int weight, int groupMin, int groupMax,
        Map<String, String> params, int xp, List<Drop> drops, String en, String tr) {

    /** One loot row; {@code looting} rows grow with the Looting enchantment. */
    public record Drop(String item, double chance, int min, int max, boolean looting) {}

    public boolean flag(String key) {
        return "1".equals(params.get(key));
    }

    public double num(String key, double fallback) {
        String v = params.get(key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    public int integer(String key, int fallback) {
        String v = params.get(key);
        return v == null ? fallback : (int) Double.parseDouble(v);
    }

    public String text(String key) {
        return params.getOrDefault(key, "");
    }

    /** Neutral species only fight back (and are CREATURE, so they spawn by day and survive Peaceful). */
    public boolean neutral() {
        return flag("neutral");
    }

    public boolean flying() {
        return flag("flying");
    }

    public MobCategory category() {
        return neutral() ? MobCategory.CREATURE : MobCategory.MONSTER;
    }

    public String translationKey() {
        return "entity.theoria." + id;
    }
}
