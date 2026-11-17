package dev.theoria.devtest.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Movement benchmark (Phase 7 detour): the FPS bench only turns the camera on the spot in a pre-generated world, so it cannot see what a
 * player sees while moving. This one walks (sprint, auto-jump, a turn every 5 s so it never sits against a wall) and then flies up and
 * down, and for each phase records
 *  - frame interval distribution (System.nanoTime between RenderFrameEvent.Pre): p50/p95/p99/max and counts above 50/100/250 ms,
 *  - client-vs-server player Y at every client tick (rubber-banding / prediction desync), and server tick time,
 *  - camera Y minus the player's eye Y (interpolated) at the end of every frame (camera detached from the player).
 * -Dtheoria.dev.client=move, -Dtheoria.dev.phase=30 (seconds per phase). Report: move.txt (RESULT lines + DONE).
 */
final class MoveBench {
    static final int PHASE_S = Integer.getInteger("theoria.dev.phase", 30);
    private static final String[] PHASES = {"walk", "fly", "spin"};
    static final boolean SHOTS = Boolean.getBoolean("theoria.dev.shots");
    private static String wantShot;

    private static int phase = -1;
    private static int phaseStart;
    private static boolean running;
    private static long lastFrameNs;
    private static long startNs;
    private static final List<Double> dts = new ArrayList<>();
    private static double[] sliceMax = new double[0];
    private static double maxServerDy, maxCamDy, maxServerTickMs;
    private static int serverDyOver, camDyOver, clientTicks, serverTicksStart, serverTicksEnd;
    private static Vec3 startPos;

    private MoveBench() {}

    static void tick(Minecraft mc, int t) {
        if (t < 100) return; // settle after the "render ready" gate
        if (phase < 0) startPhase(mc, 0, t);
        int local = t - phaseStart;
        var player = mc.player;
        // keys are re-asserted every tick: the window may lose focus and the game releases them
        boolean flying = !PHASES[phase].equals("walk");
        boolean spin = PHASES[phase].equals("spin");
        mc.options.keyUp.setDown(true);
        mc.options.keySprint.setDown(!flying);
        if (spin) {
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
            player.setYRot(player.getYRot() + 6f);          // ~120 deg/s: a quick mouse flick, continuously
            player.setXRot((float) (20.0 * Math.sin(local / 15.0)));
        } else if (!flying) {
            mc.options.keyJump.setDown(false);
            if (local % 100 == 0) player.setYRot(player.getYRot() + 40f);
        } else {
            int sec = (local / 20) % 8; // 3 s up, 1 s level, 3 s down, 1 s level
            mc.options.keyJump.setDown(sec < 3);
            mc.options.keyShift.setDown(sec >= 4 && sec < 7);
            if (local % 20 == 0) player.setYRot(player.getYRot() + 6f);
        }
        if (SHOTS && (local == 100 || local == 300 || local == 450)) wantShot = PHASES[phase] + "_" + local;
        clientTicks++;
        var server = mc.getSingleplayerServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp != null) {
                double dy = Math.abs(sp.getY() - player.getY());
                maxServerDy = Math.max(maxServerDy, dy);
                if (dy > 0.6) serverDyOver++;
            }
            maxServerTickMs = Math.max(maxServerTickMs, server.getAverageTickTimeNanos() / 1_000_000.0);
            serverTicksEnd = server.getTickCount();
        }
        if (local >= PHASE_S * 20) {
            finishPhase(mc);
            if (phase + 1 < PHASES.length) {
                startPhase(mc, phase + 1, t);
            } else {
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                mc.options.keyJump.setDown(false);
                mc.options.keyShift.setDown(false);
                DevClient.REPORT.line("DONE");
                DevClient.REPORT.write();
                mc.stop();
            }
        }
    }

    /** RenderFrameEvent.Pre: frame interval. */
    static void framePre() {
        if (!running) return;
        long now = System.nanoTime();
        if (lastFrameNs != 0) {
            double ms = (now - lastFrameNs) / 1_000_000.0;
            dts.add(ms);
            int slice = (int) ((now - startNs) / 10_000_000_000L);
            if (slice < sliceMax.length) sliceMax[slice] = Math.max(sliceMax[slice], ms);
        }
        lastFrameNs = now;
    }

    /** RenderFrameEvent.Post: the camera that was just used vs the player it should follow. */
    static void framePost(Minecraft mc) {
        if (wantShot != null && mc.player != null) {
            String name = wantShot;
            wantShot = null;
            var p = mc.player;
            DevClient.REPORT.line("SHOT %s pos=%.1f,%.1f,%.1f yaw=%.0f pitch=%.0f", name, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, img -> {
                try (img) {
                    var dir = dev.theoria.devtest.Dev.outDir().resolve("shots");
                    java.nio.file.Files.createDirectories(dir);
                    img.writeToFile(dir.resolve(System.getProperty("theoria.dev.label", "x").replaceAll("^_", "") + "_" + name + ".png"));
                } catch (java.io.IOException e) {
                    DevClient.REPORT.line("SHOT failed: %s", e);
                }
            });
        }
        if (!running || mc.player == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double dy = Math.abs(mc.gameRenderer.mainCamera().position().y - mc.player.getEyePosition(pt).y);
        maxCamDy = Math.max(maxCamDy, dy);
        if (dy > 0.6) camDyOver++;
    }

    private static void startPhase(Minecraft mc, int p, int t) {
        phase = p;
        phaseStart = t;
        dts.clear();
        sliceMax = new double[PHASE_S / 10 + 2];
        maxServerDy = maxCamDy = maxServerTickMs = 0;
        serverDyOver = camDyOver = clientTicks = 0;
        lastFrameNs = 0;
        startNs = System.nanoTime();
        startPos = mc.player.position();
        var server = mc.getSingleplayerServer();
        serverTicksStart = server == null ? 0 : server.getTickCount();
        if (PHASES[p].equals("fly")) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        running = true;
        DevClient.REPORT.line("phase %s: %d s", PHASES[p], PHASE_S);
    }

    private static void finishPhase(Minecraft mc) {
        running = false;
        double[] s = dts.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double total = Arrays.stream(s).sum();
        Vec3 end = mc.player.position();
        DevClient.REPORT.line(
                "RESULT phase=%s frames=%d avgFps=%.1f dtMs p50=%.1f p95=%.1f p99=%.1f max=%.1f over50ms=%d over100ms=%d over250ms=%d",
                PHASES[phase], s.length, s.length / Math.max(0.001, total / 1000.0), pct(s, 50), pct(s, 95), pct(s, 99),
                s.length > 0 ? s[s.length - 1] : -1, count(s, 50), count(s, 100), count(s, 250));
        DevClient.REPORT.line("  slices(10s) maxFrameMs=%s", Arrays.toString(Arrays.stream(sliceMax).mapToLong(Math::round).toArray()));
        DevClient.REPORT.line("  moved=%.0f blocks (dy=%.1f) clientTicks=%d serverTicks=%d | clientVsServerY max=%.2f over0.6=%d | cameraVsEyeY max=%.2f over0.6=%d | serverTickMs max=%.1f",
                end.subtract(startPos).horizontalDistance(), end.y - startPos.y, clientTicks, serverTicksEnd - serverTicksStart,
                maxServerDy, serverDyOver, maxCamDy, camDyOver, maxServerTickMs);
    }

    private static double pct(double[] sorted, int p) {
        if (sorted.length == 0) return -1;
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(p / 100.0 * sorted.length))];
    }

    private static int count(double[] s, double above) {
        return (int) Arrays.stream(s).filter(v -> v > above).count();
    }
}
