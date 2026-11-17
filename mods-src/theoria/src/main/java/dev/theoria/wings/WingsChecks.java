package dev.theoria.wings;

import com.mojang.brigadier.tree.CommandNode;
import dev.theoria.core.Net;
import dev.theoria.core.check.Checks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

/**
 * Area "wings" of {@code /theoria test}: everything the feature needs from Fantastic Wings is present (run before updating the mod — ADR-002 d),
 * the geometry and the turntable rules hold for every window size. The wings' LOOK is the user's approval (no screenshots).
 */
public final class WingsChecks {
    private WingsChecks() {}

    public static void init() {
        Checks.register("wings", "catalog", source -> {
            List<Identifier> ids = WingsCatalog.ids(source.registryAccess());
            Checks.require(!ids.isEmpty(), "registry fantasticwings:wings is empty or missing — is Fantastic Wings installed?");
            for (Identifier id : ids) {
                Checks.require(WingsCatalog.parse(id.toString()) != null && WingsCatalog.contains(source.registryAccess(), id), "id does not round-trip: " + id);
            }
            return ids.size() + " wings: " + ids.stream().map(Identifier::getPath).toList();
        });
        Checks.register("wings", "commands", source -> {
            CommandNode<CommandSourceStack> wings = source.getServer().getCommands().getDispatcher().getRoot().getChild("wings");
            Checks.require(wings != null, "command /wings missing");
            Checks.require(wings.getChild("give") != null && wings.getChild("take") != null, "/wings give / take missing");
            return "/wings give|take present";
        });
        Checks.register("wings", "wired", source -> {
            Checks.require(WingsModule.CHOICE != null, "attachment theoria:wings not registered");
            Checks.require(Net.payloadCount() >= 1, "no payloads declared");
            return "attachment " + WingsModule.CHOICE.get() + ", payloads=" + Net.payloadCount();
        });
        Checks.register("wings", "ids", source -> {
            Checks.require(WingsCatalog.parse("") == null && WingsCatalog.parse("x".repeat(65)) == null, "empty / oversized id accepted");
            for (String bad : List.of("angel; op @s", "a b", "fantasticwings:angel\nop @s", "FANTASTICWINGS:ANGEL", "fantasticwings:", "a:b:c")) {
                Checks.require(WingsCatalog.parse(bad) == null, "malformed id accepted: " + bad.replace('\n', '↵'));
            }
            Checks.require(WingsCatalog.parse("fantasticwings:angel") != null, "valid id rejected");
            Checks.require("Blue Butterfly".equals(WingsCatalog.prettyName(Identifier.parse("fantasticwings:blue_butterfly"))), "pretty name wrong");
            Checks.require("theoria.wings.name.fantasticwings.angel".equals(WingsCatalog.nameKey(Identifier.parse("fantasticwings:angel"))), "name key wrong");
            return "malformed ids refused, name helpers ok";
        });
        Checks.register("wings", "angle", source -> angle());
        Checks.register("wings", "layout", source -> layout());
        Checks.register("wings", "pause_row", source -> pauseRow());
    }

    /** The turntable rules (docs/04 §2): starts on the back, idles, a drag takes over, idles again after the delay, bounded pitch/steps. */
    static String angle() {
        PreviewAngle a = new PreviewAngle();
        Checks.require(a.yaw() == 0f && a.pitch() == 0f, "must start with the back to the camera (yaw 0)");
        for (int i = 0; i < 10; i++) a.advance(0.1f);
        Checks.require(Math.abs(a.yaw() - PreviewAngle.IDLE_DEGREES_PER_SECOND) < 0.01f, "one idle second should turn " + PreviewAngle.IDLE_DEGREES_PER_SECOND + "°, got " + a.yaw());
        a.drag(10, 0);
        float dragged = a.yaw();
        Checks.require(Math.abs(dragged - ((PreviewAngle.IDLE_DEGREES_PER_SECOND - 16f) + 360f)) < 0.01f, "drag of 10 px should turn -16°, got " + dragged);
        Checks.require(!a.idle(), "must not idle while dragging");
        for (int i = 0; i < 20; i++) a.advance(0.1f);
        Checks.require(a.yaw() == dragged, "must not spin during a drag");
        a.release();
        for (int i = 0; i < 29; i++) a.advance(0.1f);
        Checks.require(a.yaw() == dragged, "idle spin resumed too early (" + a.yaw() + ")");
        for (int i = 0; i < 5; i++) a.advance(0.1f);
        Checks.require(a.idle() && a.yaw() != dragged, "idle spin did not resume after " + PreviewAngle.IDLE_DELAY + " s");
        a.drag(0, 1000);
        Checks.require(a.pitch() == PreviewAngle.MAX_PITCH, "pitch not clamped: " + a.pitch());
        a.drag(0, -5000);
        Checks.require(a.pitch() == -PreviewAngle.MAX_PITCH, "pitch not clamped: " + a.pitch());
        PreviewAngle b = new PreviewAngle();
        b.advance(100f);
        Checks.require(b.yaw() <= PreviewAngle.IDLE_DEGREES_PER_SECOND * PreviewAngle.MAX_STEP + 0.001f, "a long frame flung the player round: " + b.yaw());
        return "back-first, idle, drag takes over, resumes after " + PreviewAngle.IDLE_DELAY + " s, pitch ±" + PreviewAngle.MAX_PITCH + ", long frames bounded";
    }

    /** Every GUI-scaled size vanilla can produce (≥ 320×240) × several catalog sizes: nothing overlaps, nothing leaves the screen. */
    static String layout() {
        int combos = 0;
        for (int w = 320; w <= 1920; w += 32) {
            for (int h = 240; h <= 1080; h += 24) {
                for (int count : new int[] {0, 1, 5, 11, 12, 40}) {
                    problems(w, h, count);
                    combos++;
                }
            }
        }
        return combos + " (size × catalog) combinations, GUI 320×240 … 1920×1080: inside the screen, no overlaps, scrolling consistent";
    }

    private static void problems(int w, int h, int count) {
        WingsLayout.Screen s = WingsLayout.compute(w, h, count);
        String at = String.format(Locale.ROOT, "%dx%d, %d wings: ", w, h, count);
        Checks.require(s.preview().inside(w, h) && s.list().inside(w, h) && s.remove().inside(w, h) && s.done().inside(w, h), at + "a block leaves the screen");
        Checks.require(!s.preview().intersects(s.list()), at + "preview overlaps list");
        Checks.require(!s.remove().intersects(s.done()), at + "buttons overlap");
        Checks.require(s.list().bottom() <= s.remove().y() && s.preview().bottom() <= s.remove().y(), at + "content reaches the buttons");
        Checks.require(s.cards().size() == count, at + "card count");
        for (int i = 0; i < count; i++) {
            WingsLayout.Rect card = s.cards().get(i);
            Checks.require(card.x() >= s.list().x() && card.right() <= s.list().right() - WingsLayout.SCROLLBAR_W, at + "card " + i + " outside the list columns");
            for (int j = i + 1; j < count; j++) Checks.require(!card.intersects(s.cards().get(j)), at + "cards " + i + "/" + j + " overlap");
        }
        boolean fits = s.contentHeight() <= s.list().h();
        Checks.require(fits == (s.maxScroll() == 0), at + "scroll range inconsistent (content " + s.contentHeight() + ", view " + s.list().h() + ", max " + s.maxScroll() + ")");
        Checks.require(s.maxScroll() == 0 || s.maxScroll() == s.contentHeight() - s.list().h(), at + "max scroll wrong");
        if (count > 0) Checks.require(s.list().h() >= WingsLayout.CARD_H, at + "list too short for one card");
    }

    /** The ESC-menu button row: inside the screen and never on the vanilla column when there is any room around it. */
    static String pauseRow() {
        int combos = 0;
        for (int w = 320; w <= 1920; w += 40) {
            for (int h = 240; h <= 1080; h += 30) {
                for (int top : new int[] {20, 60, 90, 120}) {
                    for (int columnH : new int[] {80, 120, 150, 180}) {
                        int bottom = top + columnH;
                        if (bottom > h) continue;
                        for (int count = 1; count <= 3; count++) {
                            List<WingsLayout.Rect> row = WingsLayout.pauseRow(top, bottom, w, h, count);
                            String at = String.format(Locale.ROOT, "%dx%d column %d-%d, %d buttons: ", w, h, top, bottom, count);
                            WingsLayout.Rect column = new WingsLayout.Rect((w - WingsLayout.PAUSE_COLUMN_W) / 2, top, WingsLayout.PAUSE_COLUMN_W, columnH);
                            boolean roomBelow = bottom + WingsLayout.PAUSE_GAP + WingsLayout.BUTTON_H <= h - WingsLayout.PAUSE_MARGIN;
                            boolean roomAbove = top - WingsLayout.BUTTON_H - WingsLayout.PAUSE_GAP >= WingsLayout.PAUSE_MARGIN;
                            List<WingsLayout.Rect> checked = new ArrayList<>(row);
                            for (int i = 0; i < checked.size(); i++) {
                                WingsLayout.Rect r = checked.get(i);
                                Checks.require(r.inside(w, h), at + "button " + i + " leaves the screen");
                                Checks.require(r.w() >= 40, at + "button " + i + " too narrow");
                                if (roomBelow || roomAbove) Checks.require(!r.intersects(column), at + "button " + i + " sits on the vanilla column");
                                for (int j = i + 1; j < checked.size(); j++) Checks.require(!r.intersects(checked.get(j)), at + "buttons overlap");
                            }
                            combos++;
                        }
                    }
                }
            }
        }
        return combos + " column × screen × button-count combinations: inside the screen, off the vanilla column";
    }
}
