package dev.theoria.devtest.client;

import dev.theoria.stats.PlayerStats;
import dev.theoria.stats.StatsLayout;
import dev.theoria.stats.StatsModule;
import dev.theoria.stats.client.StatsClient;
import dev.theoria.stats.client.StatsScreen;
import dev.theoria.wings.WingsLayout;
import dev.theoria.wings.client.WingsClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/**
 * Unattended Status-screen scenario ({@code ./gradlew runDevClient -PdevClient=stats}, Phase 10): gives a survival player points through the server's own command,
 * opens the screen, stages points with the same buttons a click would press, applies, and asserts what a program CAN see — the screen drew every frame with the 3D preview,
 * did not pause the game, kept every widget inside the window, the server booked exactly what was asked and the attachment synced back, the ESC button sits beside Wings
 * without overlapping anything, the key is registered. How it LOOKS is the user's approval (CLAUDE.md: no screenshots).
 */
final class StatsScenario {
    private record Step(String name, List<String> commands, Consumer<Minecraft> before, int hold, Function<Minecraft, String> verify) {}

    private static int stepIndex = -1;
    private static int stepStart;
    private static boolean failed;
    private static List<Step> steps;
    private static String note = "";
    private static long[] mark = new long[3];

    private StatsScenario() {}

    static void tick(Minecraft mc, int t) {
        if (steps == null) {
            steps = buildSteps();
            stepStart = t;
        }
        if (t < 40) return; // let the world settle
        if (stepIndex < 0 || t - stepStart >= steps.get(stepIndex).hold()) {
            if (stepIndex >= 0) finish(mc, steps.get(stepIndex));
            stepIndex++;
            stepStart = t;
            if (stepIndex >= steps.size()) {
                DevClient.REPORT.line("RESULT %s", failed ? "FAIL" : "PASS");
                DevClient.REPORT.write();
                mc.stop();
                return;
            }
            Step step = steps.get(stepIndex);
            note = "";
            mark = StatsScreen.counters();
            if (step.before() != null) step.before().accept(mc);
            step.commands().forEach(mc.player.connection::sendCommand);
        }
    }

    private static void finish(Minecraft mc, Step step) {
        long[] now = StatsScreen.counters();
        String problem;
        try {
            problem = step.verify().apply(mc);
        } catch (RuntimeException e) {
            problem = "threw " + e;
        }
        if (problem != null) failed = true;
        DevClient.REPORT.line("%-16s %s  [frames +%d, previews +%d, requests +%d] %s", step.name(), problem == null ? "ok  " : "FAIL " + problem, now[0] - mark[0], now[1] - mark[1], now[2] - mark[2], note);
    }

    private static PlayerStats stats(Minecraft mc) {
        return StatsModule.of(mc.player);
    }

    private static StatsScreen screen(Minecraft mc) {
        return mc.gui.screen() instanceof StatsScreen s ? s : null;
    }

    private static String open(Minecraft mc) {
        StatsScreen screen = screen(mc);
        if (screen == null) return "not on the Status screen: " + mc.gui.screen();
        long[] now = StatsScreen.counters();
        if (now[0] - mark[0] < 10) return "the screen drew only " + (now[0] - mark[0]) + " frames";
        if (now[1] - mark[1] < 10) return "the 3D preview reached the GUI renderer only " + (now[1] - mark[1]) + " times";
        if (mc.isPaused()) return "the Status screen paused the game (a paused single-player server processes no packets)";
        StatsLayout.Screen l = screen.layout();
        List<StatsLayout.Rect> all = new ArrayList<>(List.of(l.preview(), l.xpBar(), l.info(), l.apply(), l.clear(), l.done()));
        for (StatsLayout.Row r : l.rows()) all.addAll(List.of(r.bar(), r.minus(), r.count(), r.plus()));
        for (StatsLayout.Rect r : all) if (!r.inside(screen.width, screen.height)) return "widget off screen " + r + " in " + screen.width + "x" + screen.height;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton b && (b.getX() < 0 || b.getY() < 0 || b.getX() + b.getWidth() > screen.width || b.getY() + b.getHeight() > screen.height)) return "button off screen: " + b.getMessage().getString();
        }
        note = "gui " + screen.width + "x" + screen.height + ", " + l.rows().size() + " rows, row height " + l.rows().get(0).bar().h();
        return null;
    }

    private static String staged(Minecraft mc, int expected) {
        StatsScreen screen = screen(mc);
        if (screen == null) return "not on the Status screen";
        int sum = screen.staged().values().stream().mapToInt(Integer::intValue).sum();
        note = "staged " + screen.staged();
        return sum == expected ? null : "staged " + sum + " points, expected " + expected;
    }

    private static String applied(Minecraft mc) {
        StatsScreen screen = screen(mc);
        PlayerStats s = stats(mc);
        note = "server booked vitality " + s.pointOf("vitality") + ", " + s.available() + " left, max health " + mc.player.getMaxHealth();
        if (StatsScreen.counters()[2] - mark[2] < 1) return "no request was sent";
        if (s.pointOf("vitality") != 3) return "expected 3 points in vitality, the synced attachment says " + s.pointOf("vitality");
        if (s.available() != 37) return "expected 37 points left, got " + s.available();
        if (screen != null && !screen.staged().isEmpty()) return "staging was not dropped after the server confirmed: " + screen.staged();
        if (mc.player.getMaxHealth() < 20.5f) return "max health did not grow: " + mc.player.getMaxHealth();
        return null;
    }

    private static AbstractButton button(Screen screen, String key) {
        String label = I18n.get(key);
        List<AbstractButton> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton b && b.getMessage().getString().equals(label)) found.add(b);
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    private static String escRow(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof PauseScreen)) return "not on the pause screen: " + screen;
        AbstractButton wings = button(screen, WingsClient.BUTTON_KEY);
        AbstractButton status = button(screen, StatsClient.BUTTON_KEY);
        if (wings == null || status == null) return "Wings / Status button missing or duplicated (wings=" + (wings != null) + ", status=" + (status != null) + ")";
        WingsLayout.Rect w = new WingsLayout.Rect(wings.getX(), wings.getY(), wings.getWidth(), wings.getHeight());
        WingsLayout.Rect s = new WingsLayout.Rect(status.getX(), status.getY(), status.getWidth(), status.getHeight());
        note = "gui " + screen.width + "x" + screen.height + ", Wings " + w + ", Status " + s;
        if (!s.inside(screen.width, screen.height) || !w.inside(screen.width, screen.height)) return "a button is off screen";
        if (w.intersects(s)) return "the Wings and Status buttons overlap";
        if (w.y() != s.y()) return "the two buttons are not in one row (" + w.y() + " / " + s.y() + ")";
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractButton b) || b == wings || b == status) continue;
            WingsLayout.Rect o = new WingsLayout.Rect(b.getX(), b.getY(), b.getWidth(), b.getHeight());
            if (o.intersects(s) || o.intersects(w)) return "a mod button overlaps '" + b.getMessage().getString() + "' " + o;
        }
        return null;
    }

    private static String key(Minecraft mc) {
        KeyMapping mapping = KeyMapping.get("key.theoria.stats");
        if (mapping == null) return "key mapping key.theoria.stats is not registered";
        note = "key " + mapping.saveString() + " in category " + mapping.getCategory().id();
        return mapping.saveString().equals("key.keyboard.k") ? null : "default key is " + mapping.saveString() + ", expected K";
    }

    private static List<Step> buildSteps() {
        return List.of(
                new Step("reset", List.of("gamemode survival", "theoria stats xp set 0", "theoria stats reset"), null, 10,
                        mc -> stats(mc).level() == 1 && stats(mc).spent() == 0 ? null : "expected level 1, got " + stats(mc).level()),
                new Step("level_40_points", List.of("theoria stats level " + (1 + 40 / dev.theoria.stats.StatsData.leveling().pointsPerLevel())), null, 10,
                        mc -> stats(mc).level() == 1 + 40 / dev.theoria.stats.StatsData.leveling().pointsPerLevel() && stats(mc).available() == 40 ? null : "expected the level that gives 40 points, got L" + stats(mc).level() + " / " + stats(mc).available()),
                new Step("key", List.of(), null, 2, StatsScenario::key),
                new Step("open_screen", List.of(), mc -> mc.gui.setScreen(new StatsScreen(null)), 24, StatsScenario::open),
                new Step("stage_3", List.of(), mc -> { StatsScreen s = screen(mc); for (int i = 0; i < 3; i++) s.clickPlus(1); }, 6, mc -> staged(mc, 3)),
                new Step("apply", List.of(), mc -> screen(mc).clickApply(), 30, StatsScenario::applied),
                new Step("close", List.of(), mc -> mc.gui.setScreen(null), 4, mc -> mc.gui.screen() == null ? null : "the screen did not close: " + mc.gui.screen()),
                new Step("esc_row", List.of(), mc -> mc.gui.setScreen(new PauseScreen(true)), 10, StatsScenario::escRow),
                // the dev world persists between scenarios (the HUD scenario counts max health): leave it as found
                new Step("cleanup", List.of("theoria stats reset", "theoria stats xp set 0"), mc -> mc.gui.setScreen(null), 10,
                        mc -> stats(mc).spent() == 0 && stats(mc).level() == 1 && mc.player.getMaxHealth() == 20.0f ? null : "dev world not clean: L" + stats(mc).level() + ", spent " + stats(mc).spent() + ", max health " + mc.player.getMaxHealth()));
    }
}
