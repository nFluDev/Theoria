package dev.theoria.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's lifetime progress, as stored in the {@code theoria:stats} attachment (saved, synced to the client, copied on death):
 * <ul>
 * <li>{@code xp} — lifetime raw XP (docs/04 §5). It only grows: enchanting, the anvil and {@code /xp add -N levels} spend VANILLA levels, never this;</li>
 * <li>{@code points} — skill points the player has put into each stat. The level and the points to spend are derived (4 per level above the first,
 * minus what is spent), so a rebalanced curve or points-per-level reaches existing players.</li>
 * </ul>
 */
public record PlayerStats(long xp, Map<String, Integer> points) {
    public static final PlayerStats NEW = new PlayerStats(0L, Map.of());
    public static final MapCodec<PlayerStats> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(PlayerStats::xp),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("points", Map.of()).forGetter(PlayerStats::points)).apply(i, PlayerStats::of));
    /** A request or a save never carries more than a handful of stats; the bound keeps a hostile payload from allocating. */
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_ID_LENGTH = 32;
    public static final StreamCodec<ByteBuf, Map<String, Integer>> POINTS_CODEC = ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
            HashMap::new, ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), ByteBufCodecs.VAR_INT, MAX_ENTRIES);
    public static final StreamCodec<ByteBuf, PlayerStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlayerStats::xp, POINTS_CODEC, PlayerStats::points, PlayerStats::of);

    /** Clamps what a save or a packet says: XP into [0, cap], points to known stats and positive amounts. */
    public static PlayerStats of(long xp, Map<String, Integer> points) {
        Map<String, Integer> clean = new HashMap<>();
        points.forEach((id, n) -> {
            if (n != null && n > 0 && StatsData.stat(id) != null) clean.put(id, n);
        });
        return new PlayerStats(Math.max(0L, Math.min(xp, LevelCurve.maxXp())), Map.copyOf(clean));
    }

    public int level() {
        return LevelCurve.levelOf(xp);
    }

    public int pointOf(String stat) {
        return points.getOrDefault(stat, 0);
    }

    public int spent() {
        int sum = 0;
        for (int n : points.values()) sum += n;
        return sum;
    }

    /** Points still to be spent (never negative: a lowered curve leaves a player with fewer points than he spent, he just cannot add). */
    public int available() {
        return Math.max(0, LevelCurve.totalPoints(level()) - spent());
    }

    public PlayerStats withXp(long newXp) {
        return of(newXp, points);
    }

    public PlayerStats addXp(long amount) {
        // overflow-safe: xp ≤ 1.74e10, an int amount never reaches long's edge, but a /theoria stats command could ask for anything
        long sum = amount > 0 && xp > Long.MAX_VALUE - amount ? Long.MAX_VALUE : xp + amount;
        return of(sum, points);
    }

    /** {@code add} on top of what is there; the caller has validated the amounts against {@link #available()}. */
    public PlayerStats allocate(Map<String, Integer> add) {
        Map<String, Integer> merged = new HashMap<>(points);
        add.forEach((id, n) -> merged.merge(id, n, Integer::sum));
        return of(xp, merged);
    }

    /** Back to no allocated points; the level stays (Orb of Oblivion). */
    public PlayerStats cleared() {
        return new PlayerStats(xp, Map.of());
    }
}
