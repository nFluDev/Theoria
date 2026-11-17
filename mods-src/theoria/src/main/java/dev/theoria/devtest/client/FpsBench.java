package dev.theoria.devtest.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * FPS benchmark (ADR-001, Phase 5 table): for each render distance in -Dtheoria.dev.rd (default 12,16,24) wait
 * settle ticks, then sample Minecraft#getFps once per second while slowly turning the camera.
 * Optional -Dtheoria.dev.fullscreen=true. Run with -Dtheoria.dev=true -Dtheoria.dev.client=fps.
 * Phase 5 additions: per-minute average FPS (a 10-minute run shows drift), JVM heap used and process RSS at the start and end of
 * the sampling window (-Dtheoria.dev.sample=600 for the 10-minute run).
 */
final class FpsBench {
    static final int[] RDS = Arrays.stream(System.getProperty("theoria.dev.rd", "12,16,24").split(","))
            .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
    static final int WARMUP = Integer.getInteger("theoria.dev.warmup", 200);
    static final int SETTLE = Integer.getInteger("theoria.dev.settle", 400);
    static final int SAMPLE_S = Integer.getInteger("theoria.dev.sample", 30);
    static final boolean FULLSCREEN = Boolean.getBoolean("theoria.dev.fullscreen");

    private static int phase = -1;
    private static int phaseStart;
    private static final List<Integer> samples = new ArrayList<>();
    private static long heapStartMb;
    private static long rssStartMb;
    private static long heapMaxMb;

    private FpsBench() {}

    static void tick(Minecraft mc, int t) {
        if (t == WARMUP / 2 && FULLSCREEN && !mc.getWindow().isWindowFullscreen()) {
            mc.getWindow().setFullscreen(true);
        }
        if (t < WARMUP) return;
        if (phase < 0) startPhase(mc, 0, t);
        int local = t - phaseStart;
        mc.player.setYRot(mc.player.getYRot() + 0.25f);
        if (local == SETTLE) {
            heapStartMb = heapMb();
            rssStartMb = rssMb();
            heapMaxMb = heapStartMb;
        }
        if (local >= SETTLE && (local - SETTLE) % 20 == 0) {
            samples.add(mc.getFps());
            heapMaxMb = Math.max(heapMaxMb, heapMb());
        }
        if (local >= SETTLE + SAMPLE_S * 20) {
            int[] s = samples.stream().mapToInt(Integer::intValue).sorted().toArray();
            double avg = Arrays.stream(s).average().orElse(0);
            DevClient.REPORT.line("RESULT rd=%d samples=%d avg=%.1f min=%d p10=%d median=%d max=%d window=%dx%d fullscreen=%s",
                    RDS[phase], s.length, avg, s.length > 0 ? s[0] : -1, pct(s, 10), pct(s, 50), s.length > 0 ? s[s.length - 1] : -1,
                    mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().isWindowFullscreen());
            DevClient.REPORT.line("MEMORY rd=%d heapMb start=%d max=%d end=%d rssMb start=%d end=%d perMinuteAvgFps=%s",
                    RDS[phase], heapStartMb, heapMaxMb, heapMb(), rssStartMb, rssMb(), perMinute(samples));
            if (phase + 1 < RDS.length) {
                startPhase(mc, phase + 1, t);
            } else {
                if (FULLSCREEN) mc.getWindow().setFullscreen(false);
                DevClient.REPORT.line("DONE");
                DevClient.REPORT.write();
                mc.stop();
            }
        }
    }

    private static void startPhase(Minecraft mc, int p, int t) {
        phase = p;
        phaseStart = t;
        samples.clear();
        mc.options.renderDistance().set(RDS[p]);
        DevClient.REPORT.line("phase %d: renderDistance=%d (settle %d ticks, sample %d s)", p, RDS[p], SETTLE, SAMPLE_S);
    }

    private static long heapMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    /** Resident set size of this process in MB (ps -o rss=); -1 if unavailable. */
    private static long rssMb() {
        try {
            Process p = new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(ProcessHandle.current().pid())).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return Long.parseLong(r.readLine().trim()) / 1024;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private static String perMinute(List<Integer> all) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 60) {
            out.add(String.format("%.0f", all.subList(i, Math.min(all.size(), i + 60)).stream().mapToInt(Integer::intValue).average().orElse(0)));
        }
        return out.toString();
    }

    private static int pct(int[] sorted, int p) {
        if (sorted.length == 0) return -1;
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(p / 100.0 * sorted.length))];
    }
}
