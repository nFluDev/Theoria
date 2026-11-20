package dev.theoria.devtest.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.core.client.NetClient;
import dev.theoria.wings.SelectWingsPayload;
import dev.theoria.wings.WingsCatalog;
import dev.theoria.wings.WingsLayout;
import dev.theoria.wings.WingsModule;
import dev.theoria.wings.client.WingsClient;
import dev.theoria.wings.client.WingsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

/**
 * Unattended wings scenario ({@code ./gradlew runDevClient -PdevClient=wings}), Phase 4's Definition of Done as far as a program can see it:
 * the ESC menu has our button, off the vanilla column; ESC pauses the game (D14) while the Wings screen does not (player and server keep
 * ticking, the 3D preview draws every frame); wings go on / switch / come off by clicking cards (a real click through the screen's hit-testing,
 * answered by the real server — Fantastic Wings' own state is read back with /data get); they survive death; the layout holds at every GUI scale
 * the window allows. How the wings LOOK in the preview is the user's approval (CLAUDE.md: no screenshots).
 */
final class WingsScenario {
    private static final Pattern WING_TYPE = Pattern.compile("wing_type: \"([^\"]+)\"");

    /** {@code action} runs on entering the step, the step holds for {@code hold} ticks, then {@code verify} returns null = ok or a complaint. */
    private record Step(String name, Consumer<Minecraft> action, int hold, Function<Minecraft, String> verify) {}

    private static List<Step> steps;
    private static int stepIndex = -1;
    private static int stepStart;
    private static boolean failed;
    // state carried between steps
    private static List<Identifier> catalog = List.of();
    private static int playerTickMark;
    private static int serverTickMark;
    private static long[] counterMark = new long[2];
    private static float yawMark;
    private static int chatMark;
    private static String equipped = "";
    /** Numbers a verify wants in the report next to its ok / FAIL (cleared after each step). */
    private static String note = "";

    private WingsScenario() {}

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
            try {
                steps.get(stepIndex).action().accept(mc);
            } catch (RuntimeException e) {
                // A step whose precondition failed (the previous step already says why) must not take the whole game down.
                failed = true;
                DevClient.REPORT.line("%-26s FAIL action threw %s", steps.get(stepIndex).name(), e);
            }
        }
    }

    private static void finish(Minecraft mc, Step step) {
        String problem;
        try {
            problem = step.verify().apply(mc);
        } catch (RuntimeException e) {
            problem = "threw " + e;
        }
        if (problem != null) failed = true;
        DevClient.REPORT.line("%-26s %s%s", step.name(), problem == null ? "ok" : "FAIL " + problem, note.isEmpty() ? "" : "  [" + note + "]");
        note = "";
    }

    // ------------------------------------------------------------ helpers
    private static String choice(Minecraft mc) {
        return mc.player.getData(WingsModule.CHOICE).id();
    }

    private static void click(Minecraft mc, int x, int y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        Screen screen = mc.gui.screen();
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static WingsScreen wingsScreen(Minecraft mc) {
        return mc.gui.screen() instanceof WingsScreen s ? s : null;
    }

    private static void clickCard(Minecraft mc, int index) {
        int[] c = wingsScreen(mc).cardCentre(index);
        click(mc, c[0], c[1]);
    }

    /** Asks the server what Fantastic Wings itself thinks the player wears (the answer arrives in chat a few ticks later). */
    private static void askWorn(Minecraft mc) {
        chatMark = DevClient.chat().size();
        mc.player.connection.sendCommand("data get entity @s neoforge:attachments");
    }

    private static String wornAnswer() {
        List<String> chat = DevClient.chat();
        Matcher m = WING_TYPE.matcher(String.join(" ", chat.subList(Math.min(chatMark, chat.size()), chat.size())));
        return m.find() ? m.group(1) : "";
    }

    private static String expectWorn(String expected) {
        String actual = wornAnswer();
        note = "Fantastic Wings state '" + actual + "'";
        return actual.equals(expected) ? null : "Fantastic Wings says '" + actual + "', expected '" + expected + "'";
    }

    private static String expectChoice(Minecraft mc, String expected) {
        note = "synced choice '" + choice(mc) + "'";
        return choice(mc).equals(expected) ? null : "synced choice '" + choice(mc) + "', expected '" + expected + "'";
    }

    /** Our ESC button, or null. */
    private static AbstractButton wingsButton(Screen screen) {
        String label = I18n.get(WingsClient.BUTTON_KEY);
        List<AbstractButton> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton b && b.getMessage().getString().equals(label)) found.add(b);
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    /** The button exists once, sits inside the screen and not on any vanilla centre-column button. */
    private static String checkEscButton(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof PauseScreen)) return "not on the pause screen: " + screen;
        AbstractButton ours = wingsButton(screen);
        if (ours == null) return "Wings button missing or duplicated";
        WingsLayout.Rect r = new WingsLayout.Rect(ours.getX(), ours.getY(), ours.getWidth(), ours.getHeight());
        if (!r.inside(screen.width, screen.height)) return "button outside the screen: " + r + " in " + screen.width + "x" + screen.height;
        int columnBottom = Integer.MIN_VALUE;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractButton b) || b == ours) continue;
            if (WingsClient.isOurs(b)) { // the Status button (Faz 10) shares our row: it must not overlap us, but it is not the vanilla column
                WingsLayout.Rect mine = new WingsLayout.Rect(b.getX(), b.getY(), b.getWidth(), b.getHeight());
                if (r.intersects(mine)) return "Wings and Status buttons overlap " + r + " / " + mine;
                continue;
            }
            if (Math.abs(b.getX() + b.getWidth() / 2 - screen.width / 2) > WingsLayout.PAUSE_CENTRE_TOLERANCE) continue;
            WingsLayout.Rect other = new WingsLayout.Rect(b.getX(), b.getY(), b.getWidth(), b.getHeight());
            if (r.intersects(other)) return "button overlaps '" + b.getMessage().getString() + "' " + other;
            columnBottom = Math.max(columnBottom, other.bottom());
        }
        note = "gui " + screen.width + "x" + screen.height + ", vanilla column ends y=" + columnBottom + ", Wings button " + r.x() + "," + r.y() + " " + r.w() + "x" + r.h();
        return r.y() >= columnBottom ? null : "button is not below the vanilla column (column ends " + columnBottom + ", button at " + r.y() + ")";
    }

    /** Every widget of the current screen is inside it and no two buttons / cards overlap. */
    private static String checkWingsLayout(Minecraft mc) {
        WingsScreen screen = wingsScreen(mc);
        if (screen == null) return "not on the wings screen: " + mc.gui.screen();
        List<WingsLayout.Rect> boxes = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w) boxes.add(new WingsLayout.Rect(w.getX(), w.getY() + screen.scrollOffset(), w.getWidth(), w.getHeight()));
        }
        for (int i = 0; i < boxes.size(); i++) {
            WingsLayout.Rect a = boxes.get(i);
            if (a.x() < 0 || a.right() > screen.width || a.bottom() > screen.height + screen.scrollOffset() + 1) return "widget " + a + " leaves the " + screen.width + "x" + screen.height + " screen";
            for (int j = i + 1; j < boxes.size(); j++) if (a.intersects(boxes.get(j))) return "widgets overlap: " + a + " / " + boxes.get(j);
        }
        WingsLayout.Screen l = screen.layout();
        if (!l.preview().inside(screen.width, screen.height) || !l.list().inside(screen.width, screen.height)) return "preview or list outside the screen";
        note = "gui " + screen.width + "x" + screen.height + " (scale " + mc.getWindow().getGuiScale() + "), " + boxes.size() + " widgets, " + l.columns() + " column(s), max scroll " + l.maxScroll();
        return boxes.size() >= catalog.size() + 2 ? null : "only " + boxes.size() + " widgets for " + catalog.size() + " wings";
    }

    /** D14: the vanilla ESC menu stops the single-player game (only our Wings screen does not). */
    private static String escMenuPauses(Minecraft mc) {
        int ticks = mc.player.tickCount - playerTickMark;
        note = "isPaused=" + mc.isPaused() + ", player ticks +" + ticks + " over 20 client ticks";
        if (!mc.isPaused()) return "ESC menu does not pause the game (D14 says it does)";
        return ticks > 1 ? "player still ticked " + ticks + " times under the ESC menu" : null;
    }

    private static Step scale(int n) {
        return new Step("esc_button_gui_scale_" + n, mc -> {
            mc.options.guiScale().set(n);
            mc.resizeGui();
            mc.gui.setScreen(new PauseScreen(true));
        }, 8, mc -> {
            String problem = checkEscButton(mc);
            if (problem != null) return problem + " (gui " + mc.gui.screen().width + "x" + mc.gui.screen().height + ")";
            AbstractButton b = wingsButton(mc.gui.screen());
            mc.gui.setScreen(new WingsScreen(mc.gui.screen()));
            return b == null ? "button vanished" : null;
        });
    }

    private static Step wingsScale(int n) {
        return new Step("wings_screen_gui_scale_" + n, mc -> {
            mc.options.guiScale().set(n);
            mc.resizeGui();
        }, 8, mc -> {
            String problem = checkWingsLayout(mc);
            WingsScreen s = wingsScreen(mc);
            return problem == null ? null : problem + (s == null ? "" : " (gui " + s.width + "x" + s.height + ", scale " + mc.getWindow().getGuiScale() + ")");
        });
    }

    private static List<Step> buildSteps() {
        return List.of(
                new Step("reset", mc -> {
                    mc.player.connection.sendCommand("gamemode survival");
                    mc.options.guiScale().set(0);
                    mc.resizeGui();
                    NetClient.send(new SelectWingsPayload(""));
                    catalog = WingsCatalog.ids(mc.level.registryAccess());
                }, 14, mc -> catalog.size() < 2 ? "catalog has " + catalog.size() + " wings (Fantastic Wings missing?)" : expectChoice(mc, "")),
                new Step("esc_menu", mc -> mc.pauseGame(false), 10, WingsScenario::checkEscButton),
                new Step("esc_menu_pauses_game", mc -> playerTickMark = mc.player.tickCount, 20, WingsScenario::escMenuPauses),
                new Step("click_wings_button", mc -> {
                    AbstractButton b = wingsButton(mc.gui.screen());
                    if (b != null) click(mc, b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2);
                }, 6, mc -> wingsScreen(mc) == null ? "clicking the button did not open the wings screen: " + mc.gui.screen() : null),
                // Leaving the ESC menu resumes the integrated server, which sleeps while paused: give it a moment, then measure steady state.
                new Step("screen_warm_up", mc -> {}, 25, mc -> mc.isPaused() ? "wings screen paused the game" : null),
                new Step("screen_does_not_pause", mc -> {
                    playerTickMark = mc.player.tickCount;
                    serverTickMark = mc.getSingleplayerServer().getTickCount();
                    counterMark = WingsScreen.counters();
                    yawMark = wingsScreen(mc).previewYaw();
                }, 30, mc -> {
                    long[] now = WingsScreen.counters();
                    int playerTicks = mc.player.tickCount - playerTickMark;
                    int serverTicks = mc.getSingleplayerServer().getTickCount() - serverTickMark;
                    note = "isPaused=" + mc.isPaused() + ", player ticks +" + playerTicks + ", server ticks +" + serverTicks + " over 30 client ticks; frames +"
                            + (now[0] - counterMark[0]) + ", 3D previews +" + (now[1] - counterMark[1]) + ", cards " + wingsScreen(mc).cardCount();
                    if (mc.isPaused()) return "wings screen paused the game";
                    if (playerTicks < 25 || serverTicks < 25) return "game not running: player +" + playerTicks + ", server +" + serverTicks + " ticks over 30";
                    if (now[0] - counterMark[0] < 25) return "screen extracted only " + (now[0] - counterMark[0]) + " frames";
                    if (now[1] - counterMark[1] < 25) return "3D preview reached the GUI renderer only " + (now[1] - counterMark[1]) + " times";
                    if (wingsScreen(mc).cardCount() != catalog.size()) return wingsScreen(mc).cardCount() + " cards for " + catalog.size() + " wings";
                    float turned = (wingsScreen(mc).previewYaw() - yawMark + 360f) % 360f;
                    return turned > 0.5f && turned < 90f ? null : "idle spin moved the preview " + turned + "° in 30 ticks";
                }),
                new Step("drag_turns_preview", mc -> {
                    WingsScreen s = wingsScreen(mc);
                    yawMark = s.previewYaw();
                    int[] c = s.previewCentre();
                    MouseButtonEvent press = new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                    s.mouseClicked(press, false);
                    s.mouseDragged(press, 20, 0);
                    s.mouseReleased(press);
                }, 2, mc -> {
                    float delta = (wingsScreen(mc).previewYaw() - yawMark + 540f) % 360f - 180f; // 20 px × 1.6 = 32° the other way
                    note = "20 px drag -> " + Math.round(delta) + "°";
                    return Math.abs(delta + 32f) < 4f ? null : "dragging 20 px turned the preview " + delta + "°, expected about -32°";
                }),
                new Step("equip_first", mc -> {
                    equipped = catalog.get(0).toString();
                    clickCard(mc, 0);
                }, 12, mc -> and(expectChoice(mc, equipped), wingsScreen(mc).wornCard().equals(equipped) ? null : "card not marked worn")),
                new Step("fantastic_wings_first", WingsScenario::askWorn, 8, mc -> expectWorn(equipped)),
                new Step("switch_to_last", mc -> {
                    equipped = catalog.get(catalog.size() - 1).toString();
                    clickCard(mc, catalog.size() - 1);
                }, 12, mc -> and(expectChoice(mc, equipped), wingsScreen(mc).wornCard().equals(equipped) ? null : "card not marked worn")),
                new Step("fantastic_wings_last", WingsScenario::askWorn, 8, mc -> expectWorn(equipped)),
                new Step("click_worn_takes_off", mc -> clickCard(mc, catalog.size() - 1), 12,
                        mc -> and(expectChoice(mc, ""), wingsScreen(mc).wornCard().isEmpty() ? null : "card still marked worn")),
                new Step("fantastic_wings_none", WingsScenario::askWorn, 8, mc -> expectWorn("")),
                new Step("equip_for_death", mc -> {
                    equipped = catalog.get(Math.min(3, catalog.size() - 1)).toString();
                    clickCard(mc, Math.min(3, catalog.size() - 1));
                }, 12, mc -> expectChoice(mc, equipped)),
                new Step("die", mc -> mc.player.connection.sendCommand("kill @s"), 50,
                        mc -> !mc.player.isAlive() ? "still dead (auto respawn did not run)" : expectChoice(mc, equipped)),
                new Step("fantastic_wings_after_death", WingsScenario::askWorn, 8, mc -> expectWorn(equipped)),
                // The screen and the ESC button at every GUI scale this window allows (the pure sweep over all sizes is /theoria test wings).
                scale(1), wingsScale(1), scale(2), wingsScale(2), scale(3), wingsScale(3), scale(4), wingsScale(4),
                new Step("take_off_button", mc -> {
                    // "Take wings off" is a real widget, not just a toggle: find it by position (the left bottom button) and press it.
                    WingsLayout.Rect r = wingsScreen(mc).layout().remove();
                    click(mc, r.x() + r.w() / 2, r.y() + r.h() / 2);
                }, 12, mc -> expectChoice(mc, "")),
                new Step("fantastic_wings_clean", WingsScenario::askWorn, 8, mc -> expectWorn("")),
                new Step("escape_returns_to_esc_menu", mc -> mc.gui.screen().onClose(), 6,
                        mc -> mc.gui.screen() instanceof PauseScreen ? null : "closing the wings screen did not return to the ESC menu: " + mc.gui.screen()),
                new Step("finish", mc -> {
                    mc.gui.setScreen(null);
                    mc.options.guiScale().set(0);
                    mc.resizeGui();
                }, 4, mc -> null));
    }

    private static String and(String first, String second) {
        return first != null ? first : second;
    }
}
