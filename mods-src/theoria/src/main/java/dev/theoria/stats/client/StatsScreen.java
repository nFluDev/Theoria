package dev.theoria.stats.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.core.client.NetClient;
import dev.theoria.stats.AllocatePointsPayload;
import dev.theoria.stats.LevelCurve;
import dev.theoria.stats.PlayerStats;
import dev.theoria.stats.StatsData;
import dev.theoria.stats.StatsLayout;
import dev.theoria.stats.StatsModule;
import dev.theoria.wings.PreviewAngle;
import dev.theoria.wings.WingsLayout;
import dev.theoria.wings.client.WingsPreview;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * The Status screen (docs/04 §5), anime style: the player in 3D on the left, one bar per stat in the middle with its [−] / [+] on the right, the level and its XP
 * progress at the bottom. Points are STAGED here and spent with one "Apply" — {@link AllocatePointsPayload} — the server checks every number again; the screen only asks
 * (the +/- of the docs is the staging, a point already spent comes back only with an Orb of Oblivion). Does NOT pause the game: a paused single-player server
 * processes no packets (the wings screen's lesson, LucidRem 45.5). How it looks is the user's approval.
 */
public final class StatsScreen extends Screen {
    private static final int FRAME = 0x66000000;
    private static final int FRAME_EDGE = 0xFF000000;
    private static final int BAR_BG = 0xCC101010;
    private static final int PENDING = 0xFF9FE870;
    private static final int XP_FILL = 0xFF5BC0EB;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    /** A request that the server never confirms (cancelled by a kick, a lost packet) must not leave the screen waiting for ever. */
    private static final int CONFIRM_TIMEOUT_TICKS = 40;

    /** Dev scenario evidence: frames the screen was extracted, requests sent. */
    private static long extractCalls;
    private static long applied;

    private final Screen parent;
    private final PreviewAngle angle = new PreviewAngle(180f);
    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private final List<Button> minus = new ArrayList<>();
    private final List<Button> plus = new ArrayList<>();
    private StatsLayout.Screen layout;
    private Button apply;
    private Button clear;
    private boolean draggingPreview;
    private boolean previewFailed;
    private long lastFrame = Util.getNanos();
    /** After Apply: the spent total the server must report before the staging is dropped (−1 = not waiting). */
    private int awaitSpent = -1;
    private int awaitTicks;

    public StatsScreen(Screen parent) {
        super(Component.translatable("theoria.stats.title"));
        this.parent = parent;
    }

    /** Must stay false: see the class comment. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PlayerStats stats() {
        return minecraft.player == null ? PlayerStats.NEW : StatsModule.of(minecraft.player);
    }

    private int pendingTotal() {
        int sum = 0;
        for (int n : pending.values()) sum += n;
        return sum;
    }

    private int remaining() {
        return stats().available() - pendingTotal();
    }

    @Override
    protected void init() {
        List<StatsData.Stat> stats = StatsData.stats();
        layout = StatsLayout.compute(width, height, stats.size());
        minus.clear();
        plus.clear();
        for (int i = 0; i < stats.size(); i++) {
            StatsData.Stat stat = stats.get(i);
            StatsLayout.Row row = layout.rows().get(i);
            minus.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> step(stat.id(), -amount())).bounds(row.minus().x(), row.minus().y(), row.minus().w(), row.minus().h()).build()));
            plus.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> step(stat.id(), amount())).bounds(row.plus().x(), row.plus().y(), row.plus().w(), row.plus().h()).build()));
        }
        StatsLayout.Rect a = layout.apply();
        StatsLayout.Rect c = layout.clear();
        StatsLayout.Rect d = layout.done();
        apply = addRenderableWidget(Button.builder(Component.translatable("theoria.stats.apply"), b -> send()).bounds(a.x(), a.y(), a.w(), a.h()).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("theoria.stats.clear"), b -> pending.clear()).bounds(c.x(), c.y(), c.w(), c.h()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(d.x(), d.y(), d.w(), d.h()).build());
        refreshButtons();
    }

    /** Shift = 10, Ctrl = 100, otherwise 1 — the spend of one click. */
    private int amount() {
        return minecraft.hasControlDown() ? 100 : minecraft.hasShiftDown() ? 10 : 1;
    }

    /** Stages {@code delta} points on a stat (negative = take staged points back); never below 0, never more than the player has to spend. */
    private void step(String stat, int delta) {
        if (awaitSpent >= 0) return;
        int now = pending.getOrDefault(stat, 0);
        int next = delta > 0 ? now + Math.min(delta, Math.max(0, remaining())) : Math.max(0, now + delta);
        if (next == 0) pending.remove(stat);
        else pending.put(stat, next);
    }

    private void send() {
        if (pending.isEmpty() || awaitSpent >= 0) return;
        awaitSpent = stats().spent() + pendingTotal();
        awaitTicks = 0;
        applied++;
        NetClient.send(new AllocatePointsPayload(new HashMap<>(pending)));
    }

    private void refreshButtons() {
        List<StatsData.Stat> stats = StatsData.stats();
        boolean waiting = awaitSpent >= 0;
        boolean room = remaining() > 0;
        for (int i = 0; i < stats.size(); i++) {
            plus.get(i).active = room && !waiting;
            minus.get(i).active = pending.getOrDefault(stats.get(i).id(), 0) > 0 && !waiting;
        }
        apply.active = !pending.isEmpty() && !waiting;
        clear.active = !pending.isEmpty() && !waiting;
    }

    @Override
    public void tick() {
        super.tick();
        if (awaitSpent >= 0 && (stats().spent() >= awaitSpent || ++awaitTicks > CONFIRM_TIMEOUT_TICKS)) {
            pending.clear();
            awaitSpent = -1;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        refreshButtons();
        super.extractRenderState(g, mouseX, mouseY, a);
        extractCalls++;
        long now = Util.getNanos();
        angle.advance((now - lastFrame) / 1.0E9F);
        lastFrame = now;
        PlayerStats stats = stats();

        g.centeredText(font, title, width / 2, StatsLayout.TITLE_Y, WHITE);
        StatsLayout.Rect p = layout.preview();
        g.fill(p.x(), p.y(), p.right(), p.bottom(), FRAME);
        g.outline(p.x(), p.y(), p.w(), p.h(), FRAME_EDGE);
        if (minecraft.player != null && !previewFailed) {
            previewFailed = !WingsPreview.draw(g, minecraft.player, new WingsLayout.Rect(p.x(), p.y(), p.w(), p.h()), angle.yaw(), angle.pitch());
        }

        List<StatsData.Stat> list = StatsData.stats();
        for (int i = 0; i < list.size(); i++) extractRow(g, list.get(i), layout.rows().get(i), stats, mouseX, mouseY);
        extractXpBar(g, stats, mouseX, mouseY);
        extractInfo(g, stats);
    }

    private void extractRow(GuiGraphicsExtractor g, StatsData.Stat stat, StatsLayout.Row row, PlayerStats stats, int mouseX, int mouseY) {
        int owned = stats.pointOf(stat.id());
        int staged = pending.getOrDefault(stat.id(), 0);
        StatsData.Effect primary = stat.effects().get(0);
        StatsLayout.Rect bar = row.bar();
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), BAR_BG);
        int innerW = bar.w() - 2;
        int ownedW = (int) Math.round(innerW * primary.value(owned) / primary.cap());
        int stagedW = (int) Math.round(innerW * primary.value(owned + staged) / primary.cap());
        g.fill(bar.x() + 1, bar.y() + 1, bar.x() + 1 + stagedW, bar.bottom() - 1, PENDING & 0x80FFFFFF);
        g.fill(bar.x() + 1, bar.y() + 1, bar.x() + 1 + ownedW, bar.bottom() - 1, colour(stat.id()));
        g.outline(bar.x(), bar.y(), bar.w(), bar.h(), FRAME_EDGE);
        int textY = bar.y() + (bar.h() - font.lineHeight) / 2 + 1;
        g.text(font, StatsText.name(stat.id()), bar.x() + 4, textY, WHITE, true);
        Component value = StatsText.line(primary, primary.value(owned + staged));
        g.text(font, value, bar.right() - 4 - font.width(value), textY, staged > 0 ? PENDING : WHITE, true);

        StatsLayout.Rect count = row.count();
        String text = Integer.toString(owned + staged);
        float scale = Math.min(1.0f, (count.w() - 2f) / Math.max(1, font.width(text)));
        g.pose().pushMatrix();
        g.pose().translate(count.x() + (count.w() - font.width(text) * scale) / 2f, count.y() + (count.h() - font.lineHeight * scale) / 2f + 1);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, staged > 0 ? PENDING : WHITE, true);
        g.pose().popMatrix();

        if (bar.contains(mouseX, mouseY) || row.count().contains(mouseX, mouseY)) {
            g.setTooltipForNextFrame(tooltip(stat, owned, staged).stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(StatsData.Stat stat, int owned, int staged) {
        List<Component> lines = new ArrayList<>();
        lines.add(StatsText.name(stat.id()).copy().withStyle(ChatFormatting.BOLD));
        lines.add(StatsText.description(stat.id()).copy().withStyle(ChatFormatting.GRAY));
        for (StatsData.Effect e : stat.effects()) {
            MutableComponent line = StatsText.line(e, e.value(owned)).copy();
            if (staged > 0) line.append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY)).append(StatsText.line(e, e.value(owned + staged)).copy().withStyle(ChatFormatting.GREEN));
            lines.add(line);
        }
        lines.add(Component.translatable("theoria.stats.row.points", owned, staged > 0 ? Component.literal(" (+" + staged + ")").withStyle(ChatFormatting.GREEN) : Component.empty())
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("theoria.stats.row.first", StatsText.line(stat.effects().get(0), stat.effects().get(0).cap() / stat.effects().get(0).half())).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void extractXpBar(GuiGraphicsExtractor g, PlayerStats stats, int mouseX, int mouseY) {
        StatsLayout.Rect bar = layout.xpBar();
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), BAR_BG);
        g.fill(bar.x() + 1, bar.y() + 1, bar.x() + 1 + (int) Math.round((bar.w() - 2) * LevelCurve.progress(stats.xp())), bar.bottom() - 1, XP_FILL);
        g.outline(bar.x(), bar.y(), bar.w(), bar.h(), FRAME_EDGE);
        int level = stats.level();
        Component text = level >= StatsData.leveling().maxLevel()
                ? Component.translatable("theoria.stats.xpline.max", level)
                : Component.translatable("theoria.stats.xpline", level, StatsText.grouped(LevelCurve.xpIntoLevel(stats.xp())), StatsText.grouped(LevelCurve.xpForNextLevel(stats.xp())));
        g.centeredText(font, text, bar.x() + bar.w() / 2, bar.y() + (bar.h() - font.lineHeight) / 2 + 1, WHITE);
        if (bar.contains(mouseX, mouseY)) {
            g.setTooltipForNextFrame(List.of(
                    Component.translatable("theoria.stats.xp.total", StatsText.grouped(stats.xp())).getVisualOrderText(),
                    Component.translatable("theoria.stats.xp.spendable", minecraft.player == null ? 0 : minecraft.player.experienceLevel).getVisualOrderText(),
                    Component.translatable("theoria.stats.xp.note").withStyle(ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void extractInfo(GuiGraphicsExtractor g, PlayerStats stats) {
        StatsLayout.Rect info = layout.info();
        int left = remaining();
        g.text(font, Component.translatable("theoria.stats.points", left), info.x(), info.y() + 1, left > 0 ? PENDING : GREY, true);
        Component right = Component.translatable("theoria.stats.spent", stats.spent());
        g.text(font, right, info.right() - font.width(right), info.y() + 1, GREY, true);
    }

    private static int colour(String stat) {
        return switch (stat) {
            case "strength" -> 0xFFC8662B;
            case "vitality" -> 0xFFC0303E;
            case "endurance" -> 0xFF4F7FB0;
            case "agility" -> 0xFF2FA89B;
            case "satiety" -> 0xFFC2A12E;
            case "precision" -> 0xFFB05FD0;
            case "fortune" -> 0xFF7A8B2E;
            case "wisdom" -> 0xFF3F6FD6;
            default -> 0xFF808080;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && layout.preview().contains(event.x(), event.y())) {
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            angle.drag(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingPreview = false;
            angle.release();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** The key that opened the screen closes it too. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (StatsClient.OPEN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------ dev scenario evidence (devtest stats)
    /** {frames extracted, previews that reached the GUI renderer, requests sent}. */
    public static long[] counters() {
        return new long[] {extractCalls, WingsPreview.draws, applied};
    }

    public StatsLayout.Screen layout() {
        return layout;
    }

    public Map<String, Integer> staged() {
        return pending;
    }

    /** Test hooks: press the same buttons a click would. */
    public void clickPlus(int row) {
        plus.get(row).onPress(null);
    }

    public void clickApply() {
        apply.onPress(null);
    }
}
