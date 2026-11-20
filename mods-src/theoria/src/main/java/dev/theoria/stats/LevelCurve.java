package dev.theoria.stats;

/**
 * The lifetime-XP curve (docs/06 §5) — pure functions, mirror of {@code tools/balance/tiers.py#xp_total}: {@code R(L)} is the raw XP needed to
 * REACH level L. L ≤ knee: a power law in (L−1) between data anchors (L2 = 55 = vanilla's 5th level; L25 ≈ 12 h, L50 ≈ 32 h, L75 ≈ 68 h of the reference pace); above it the cost of one level grows linearly with L.
 * The numbers are {@code stats.json}'s; Java and Python agree to within one XP (rounding of {@code pow}), which the self-check allows for.
 */
public final class LevelCurve {
    /** (xp → level) of the last lookup: the HUD asks every frame, the answer changes once per level. */
    private record Last(long xp, int level) {}

    private static volatile Last last = new Last(0L, 1);

    private LevelCurve() {}

    /** Raw XP at which level {@code level} is reached (L1 = 0). */
    public static long totalXp(int level) {
        StatsData.Leveling lv = StatsData.leveling();
        if (level <= 1) return 0L;
        int target = Math.min(level, lv.maxLevel());
        if (target <= lv.xpKnee()) return Math.round(poly(lv, target));
        double knee = lv.xpKnee();
        double perLevel = poly(lv, knee) - poly(lv, knee - 1); // cost of the last tabulated level
        return Math.round(poly(lv, knee) + perLevel / (2 * knee) * ((double) target * target - knee * knee)); // base = R(knee): the step knee→knee+1 costs ≈ perLevel, not a few thousand XP
    }

    /** R(L) for 2 ≤ L ≤ knee: a power law in (L−1) between the two anchors around L (mirror of tiers.py#_poly). */
    private static double poly(StatsData.Leveling lv, double level) {
        double[][] a = lv.anchors();
        for (int i = 0; i + 1 < a.length; i++) {
            if (level <= a[i + 1][0]) {
                double e = Math.log(a[i + 1][1] / a[i][1]) / Math.log((a[i + 1][0] - 1) / (a[i][0] - 1));
                return a[i][1] * Math.pow((level - 1) / (a[i][0] - 1), e);
            }
        }
        throw new IllegalArgumentException("level " + level + " is above the last anchor");
    }

    /** XP of the highest level: lifetime XP never grows past it (the cap, decision D5). */
    public static long maxXp() {
        return totalXp(StatsData.leveling().maxLevel());
    }

    /** The level a lifetime XP total stands at (1..maxLevel). */
    public static int levelOf(long xp) {
        Last cached = last;
        if (cached.xp() == xp) return cached.level();
        int lo = 1;
        int hi = StatsData.leveling().maxLevel();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (totalXp(mid) <= xp) lo = mid;
            else hi = mid - 1;
        }
        last = new Last(xp, lo);
        return lo;
    }

    /** XP gathered towards the next level (0 at the cap). */
    public static long xpIntoLevel(long xp) {
        int level = levelOf(xp);
        return level >= StatsData.leveling().maxLevel() ? 0L : xp - totalXp(level);
    }

    /** XP the current level needs to become the next one (0 at the cap). */
    public static long xpForNextLevel(long xp) {
        int level = levelOf(xp);
        return level >= StatsData.leveling().maxLevel() ? 0L : totalXp(level + 1) - totalXp(level);
    }

    public static double progress(long xp) {
        long need = xpForNextLevel(xp);
        return need <= 0 ? 1.0 : Math.min(1.0, xpIntoLevel(xp) / (double) need);
    }

    /** Skill points a level grants in total (4 per level above the first). */
    public static int totalPoints(int level) {
        return StatsData.leveling().pointsPerLevel() * (Math.max(1, level) - 1);
    }
}
