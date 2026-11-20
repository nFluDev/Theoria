package dev.theoria.stats;

import dev.theoria.Theoria;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What a player's allocated points DO (docs/04 §5): flat bonuses, no multipliers on top of gear. Attribute effects are permanent modifiers named
 * {@code theoria:stat/<stat>/<effect>}, derived from the attachment and re-applied on login, respawn, clone and dimension change (HeartCrystals' pattern), so a
 * rebalanced table reaches existing players. Custom attributes ({@code theoria:crit_chance}, {@code crit_damage}, {@code food_saturation}) are the same ones the
 * catalysts feed — one pipeline, one soft cap ({@code agg_caps}, ADR-006 Karar 5). Two effects are not attributes: hunger drain
 * ({@link #exhaustionReduction}, applied by {@link StatsServer#onTick}) and the XP bonus ({@link #xpPercent}, summed into Bilgelik by {@code Erudition}).
 */
public final class StatsEffects {
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = new ConcurrentHashMap<>();

    private StatsEffects() {}

    public static Identifier modifierId(String stat, String effect) {
        return Theoria.id("stat/" + stat + "/" + effect);
    }

    public static @Nullable Holder<Attribute> attribute(String id) {
        return ATTRIBUTES.computeIfAbsent(id, key -> BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(key)).orElse(null));
    }

    /** Re-derives every attribute modifier from the attachment (idempotent). Changes in max health also move the current health the way heart crystals do. */
    public static void apply(Player player) {
        PlayerStats stats = StatsModule.of(player);
        float oldMax = player.getMaxHealth();
        for (StatsData.Stat stat : StatsData.stats()) {
            int points = stats.pointOf(stat.id());
            for (StatsData.Effect effect : stat.effects()) {
                if (effect.kind() != StatsData.Kind.ATTRIBUTE) continue;
                Holder<Attribute> holder = attribute(effect.attribute());
                AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
                if (instance == null) continue;
                Identifier id = modifierId(stat.id(), effect.id());
                double value = effect.value(points);
                if (value <= 0.0) {
                    instance.removeModifier(id);
                } else {
                    instance.addOrReplacePermanentModifier(new AttributeModifier(id, value, effect.op()));
                }
            }
        }
        float newMax = player.getMaxHealth();
        if (newMax > oldMax) player.setHealth(Math.min(newMax, player.getHealth() + (newMax - oldMax))); // spending a point also fills what it added
        else if (player.getHealth() > newMax) player.setHealth(newMax);
    }

    /** Sum of an effect kind over all stats, for the player's allocated points. */
    private static double sum(Player player, StatsData.Kind kind) {
        PlayerStats stats = StatsModule.of(player);
        double sum = 0.0;
        for (StatsData.Stat stat : StatsData.stats()) {
            int points = stats.pointOf(stat.id());
            if (points <= 0) continue;
            for (StatsData.Effect effect : stat.effects()) if (effect.kind() == kind) sum += effect.value(points);
        }
        return sum;
    }

    /** Share (0..1) of the exhaustion the player's body does NOT pay: Satiety. */
    public static double exhaustionReduction(Player player) {
        return Math.min(1.0, sum(player, StatsData.Kind.EXHAUSTION)); // a share cannot pass 100 %; the real ceiling is the stat's own soft cap (stats.py → stats.json)
    }

    /** Percentage points Wisdom adds to the Bilgelik (Erudition) sum — whole percent like the enchantment's own values. */
    public static int xpPercent(Player player) {
        return (int) Math.round(sum(player, StatsData.Kind.XP_PERCENT));
    }
}
