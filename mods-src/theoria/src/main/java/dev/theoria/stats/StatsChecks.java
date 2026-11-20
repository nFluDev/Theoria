package dev.theoria.stats;

import dev.theoria.core.check.Checks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Area "stats" of {@code /theoria test}: the stat table and the level curve measured against what {@code tools/balance/stats.py} computed (Java ≡ Python), and the
 * screen geometry swept over window sizes. Pure — nothing here touches a player or the world; the player-facing behaviour is in the {@code stats_*} GameTests.
 */
public final class StatsChecks {
    /** {@code pow} may round one XP differently in Java and Python. */
    private static final long XP_TOLERANCE = 1;

    private StatsChecks() {}

    public static void init() {
        Checks.register("stats", "data", source -> {
            StatsData.Table t = StatsData.table();
            Checks.require(t.stats().size() == 8, "expected the 8 stats of docs/04 §5, got " + t.stats().size());
            Checks.require(t.leveling().maxLevel() == 9999 && t.leveling().pointsPerLevel() >= 1, "level cap 9999 (D5) and at least one point per level — got "
                    + t.leveling().maxLevel() + " / " + t.leveling().pointsPerLevel());
            return String.format(Locale.ROOT, "%d stats, %d effects, cap L%d, %d points/level, respec item %s (boss drop %.0f%%)", t.stats().size(),
                    t.stats().stream().mapToInt(s -> s.effects().size()).sum(), t.leveling().maxLevel(), t.leveling().pointsPerLevel(), t.respecItem(), t.respecBossChance() * 100);
        });
        Checks.register("stats", "l2_is_55", source -> {
            Checks.require(LevelCurve.totalXp(1) == 0 && LevelCurve.totalXp(2) == 55, "L2 must need exactly 55 raw XP, got " + LevelCurve.totalXp(2));
            Checks.require(LevelCurve.levelOf(54) == 1 && LevelCurve.levelOf(55) == 2, "54 XP is level 1, 55 XP is level 2");
            return "L1=0, L2=55 raw XP (vanilla's 5th level)";
        });
        Checks.register("stats", "xp_cases", source -> {
            for (long[] c : StatsData.table().cases().xp()) {
                long got = LevelCurve.totalXp((int) c[0]);
                Checks.require(Math.abs(got - c[1]) <= XP_TOLERANCE, "R(" + c[0] + ") = " + got + ", the table says " + c[1]);
            }
            for (long[] c : StatsData.table().cases().level()) {
                int got = LevelCurve.levelOf(c[0]);
                Checks.require(got == c[1], "level of " + c[0] + " XP = " + got + ", the table says " + c[1]);
            }
            return StatsData.table().cases().xp().size() + " curve points and " + StatsData.table().cases().level().size() + " level lookups equal the generator's";
        });
        Checks.register("stats", "curve_shape", source -> {
            long previous = -1;
            long previousCost = 0;
            for (int level = 1; level <= StatsData.leveling().maxLevel(); level++) {
                long xp = LevelCurve.totalXp(level);
                Checks.require(xp > previous, "R(" + level + ") = " + xp + " does not grow past " + previous);
                // no cliff in the cost of one level: a base error at the knee once made L200→L201 cost 12k instead of ~5M. (The power-law anchors dip 28 % at L100 — accepted, docs/10.)
                if (level >= 3) Checks.require(xp - previous >= 0.6 * previousCost, "the step to L" + level + " costs " + (xp - previous) + ", under 60 % of the step before (" + previousCost + ")");
                if (level >= 2) previousCost = xp - previous;
                previous = xp;
            }
            Checks.require(LevelCurve.levelOf(Long.MAX_VALUE) == StatsData.leveling().maxLevel(), "the level never passes the cap");
            return String.format(Locale.ROOT, "strictly increasing 1..%d, R(%d) = %,d", StatsData.leveling().maxLevel(), StatsData.leveling().maxLevel(), previous);
        });
        Checks.register("stats", "bonus_cases", source -> {
            int n = 0;
            for (Object[] c : StatsData.table().cases().bonus()) {
                String[] id = ((String) c[0]).split("\\.", 2);
                StatsData.Effect effect = StatsData.stat(id[0]).effects().stream().filter(e -> e.id().equals(id[1])).findFirst().orElse(null);
                Checks.require(effect != null, "unknown effect " + c[0]);
                double got = effect.value((int) c[1]);
                double want = (double) c[2];
                Checks.require(Math.abs(got - want) <= 1e-6 * Math.max(1.0, Math.abs(want)), c[0] + " at " + c[1] + " points = " + got + ", the table says " + want);
                n++;
            }
            return n + " stat values equal the generator's";
        });
        Checks.register("stats", "soft_cap", source -> {
            int most = LevelCurve.totalPoints(StatsData.leveling().maxLevel());
            for (StatsData.Stat stat : StatsData.stats()) {
                for (StatsData.Effect e : stat.effects()) {
                    Checks.require(e.value(0) == 0.0 && e.value(1) > 0.0, stat.id() + "." + e.id() + ": no points = no effect, one point = some");
                    Checks.require(e.value(most) < e.cap() && e.value(most) >= 0.88 * e.cap(), stat.id() + "." + e.id() + ": all points must stay under the cap and near it");
                    Checks.require(e.value(101) - e.value(100) < e.value(2) - e.value(1), stat.id() + "." + e.id() + ": returns must diminish");
                }
            }
            return "every effect < cap at " + most + " points, diminishing returns";
        });
        Checks.register("stats", "layout", source -> {
            int rows = StatsData.stats().size();
            int worst = Integer.MAX_VALUE;
            for (int sw = 320; sw <= 1280; sw += 40) {
                for (int sh = 240; sh <= 720; sh += 40) {
                    StatsLayout.Screen l = StatsLayout.compute(sw, sh, rows);
                    List<StatsLayout.Rect> all = new ArrayList<>(List.of(l.preview(), l.xpBar(), l.info(), l.apply(), l.clear(), l.done()));
                    for (StatsLayout.Row r : l.rows()) all.addAll(List.of(r.bar(), r.minus(), r.count(), r.plus()));
                    for (StatsLayout.Rect r : all) Checks.require(r.inside(sw, sh), "off screen at " + sw + "x" + sh + ": " + r);
                    for (int i = 0; i < all.size(); i++) {
                        for (int j = i + 1; j < all.size(); j++) {
                            Checks.require(!all.get(i).intersects(all.get(j)), "overlap at " + sw + "x" + sh + ": " + all.get(i) + " / " + all.get(j));
                        }
                    }
                    Checks.require(l.rows().get(0).bar().h() >= StatsLayout.ROW_H_MIN, "rows too thin at " + sw + "x" + sh);
                    worst = Math.min(worst, l.rows().get(0).bar().h());
                }
            }
            return "320x240 .. 1280x720: nothing off screen, nothing overlapping, thinnest row " + worst + " px";
        });
    }
}
