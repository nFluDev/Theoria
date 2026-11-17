package dev.theoria.wings.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.core.client.NetClient;
import dev.theoria.wings.PreviewAngle;
import dev.theoria.wings.SelectWingsPayload;
import dev.theoria.wings.WingsCatalog;
import dev.theoria.wings.WingsLayout;
import dev.theoria.wings.WingsModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * ESC ▸ Wings (docs/04 §2, D14). Left: the local player in 3D, back to the camera, idling, draggable. Right: every wing of Fantastic Wings'
 * registry as a card; a click wears it at once (the server does it — the screen only asks), a click on the worn card or on "Take off"
 * removes it. The screen does NOT pause the game ({@link #isPauseScreen}): a paused single-player server processes no packets, and the
 * wing animation (driven by the player's ticks) would freeze — LucidRem 45.5. How it looks is the user's approval (no screenshots).
 */
public final class WingsScreen extends Screen {
    private static final int FRAME = 0x66000000;
    private static final int FRAME_EDGE = 0xFF000000;
    private static final int HINT_COLOR = 0xFFA0A0A0;
    private static final int SCROLLBAR = 0xFF8B8B8B;

    /** Dev scenario evidence: frames the screen was extracted. */
    private static long extractCalls;

    private final Screen parent;
    private final PreviewAngle angle = new PreviewAngle();
    private final List<WingCard> cards = new ArrayList<>();
    private WingsLayout.Screen layout;
    private Button takeOff;
    private int scroll;
    private boolean draggingPreview;
    private boolean previewFailed;
    private String shownChoice = "\0";
    private long lastFrame = Util.getNanos();

    public WingsScreen(Screen parent) {
        super(Component.translatable("theoria.wings.title"));
        this.parent = parent;
    }

    /** Must stay false: see the class comment. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        List<Identifier> ids = minecraft.level == null ? List.of() : WingsCatalog.ids(minecraft.level.registryAccess());
        layout = WingsLayout.compute(width, height, ids.size());
        cards.clear();
        for (int i = 0; i < ids.size(); i++) {
            Identifier id = ids.get(i);
            Component name = Component.translatableWithFallback(WingsCatalog.nameKey(id), WingsCatalog.prettyName(id));
            WingsLayout.Rect r = layout.cards().get(i);
            WingCard card = new WingCard(Button.builder(name, button -> toggle(id)).bounds(r.x(), r.y(), r.w(), r.h()), id, name);
            card.clipTo(layout.list().y(), layout.list().bottom());
            cards.add(card);
            addWidget(card); // drawn by us inside the list's scissor, not by Screen
        }
        takeOff = addRenderableWidget(Button.builder(Component.translatable("theoria.wings.remove"), button -> send(""))
                .bounds(layout.remove().x(), layout.remove().y(), layout.remove().w(), layout.remove().h()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(layout.done().x(), layout.done().y(), layout.done().w(), layout.done().h()).build());
        scroll = Math.max(0, Math.min(scroll, layout.maxScroll()));
        shownChoice = "\0"; // force refreshCards on the next frame
        placeCards();
    }

    private String choice() {
        return minecraft.player == null ? "" : minecraft.player.getData(WingsModule.CHOICE).id();
    }

    /** Click on a card: wear that wing, or take it off when it is the worn one. The attachment sync flips the green mark a tick later. */
    private void toggle(Identifier id) {
        send(id.toString().equals(choice()) ? "" : id.toString());
    }

    private void send(String wingId) {
        NetClient.send(new SelectWingsPayload(wingId));
    }

    private void placeCards() {
        for (int i = 0; i < cards.size(); i++) {
            WingsLayout.Rect r = layout.cards().get(i);
            cards.get(i).setPosition(r.x(), r.y() - scroll);
        }
    }

    private void refreshWorn() {
        String choice = choice();
        if (choice.equals(shownChoice)) return;
        shownChoice = choice;
        for (WingCard card : cards) card.setWorn(card.wing().toString().equals(choice));
        takeOff.active = !choice.isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        extractCalls++;
        long now = Util.getNanos();
        angle.advance((now - lastFrame) / 1.0E9F);
        lastFrame = now;
        refreshWorn();

        g.centeredText(font, title, width / 2, WingsLayout.TITLE_Y, 0xFFFFFFFF);
        WingsLayout.Rect p = layout.preview();
        g.fill(p.x(), p.y(), p.right(), p.bottom(), FRAME);
        g.outline(p.x(), p.y(), p.w(), p.h(), FRAME_EDGE);
        if (minecraft.player != null && !previewFailed) {
            previewFailed = !WingsPreview.draw(g, minecraft.player, p, angle.yaw(), angle.pitch());
        }
        Component hint = Component.translatable("theoria.wings.preview.hint");
        if (font.width(hint) <= p.w() - 4) g.centeredText(font, hint, p.x() + p.w() / 2, p.bottom() - 11, HINT_COLOR);

        WingsLayout.Rect list = layout.list();
        if (cards.isEmpty()) {
            g.centeredText(font, Component.translatable("theoria.wings.unavailable.screen"), list.x() + (list.w() - WingsLayout.SCROLLBAR_W) / 2, list.y() + 8, HINT_COLOR);
            return;
        }
        g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (WingCard card : cards) card.extractRenderState(g, mouseX, mouseY, a);
        g.disableScissor();
        if (layout.maxScroll() > 0) extractScrollbar(g, list);
    }

    private void extractScrollbar(GuiGraphicsExtractor g, WingsLayout.Rect list) {
        int x = list.right() - 4;
        int thumb = Math.max(12, list.h() * list.h() / layout.contentHeight());
        int y = list.y() + (list.h() - thumb) * scroll / layout.maxScroll();
        g.fill(x, list.y(), x + 3, list.bottom(), FRAME);
        g.fill(x, y, x + 3, y + thumb, SCROLLBAR);
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

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (layout.maxScroll() > 0 && layout.list().contains(x, y)) {
            scroll = Math.max(0, Math.min(layout.maxScroll(), scroll - (int) Math.signum(scrollY) * (WingsLayout.CARD_H + WingsLayout.GAP)));
            placeCards();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------ dev scenario evidence (devtest wings)
    /** Frames extracted, previews that reached the GUI renderer. */
    public static long[] counters() {
        return new long[] {extractCalls, WingsPreview.draws};
    }

    public float previewYaw() {
        return angle.yaw();
    }

    public int scrollOffset() {
        return scroll;
    }

    public int cardCount() {
        return cards.size();
    }

    /** Centre of card {@code index} on screen (scroll applied), as {x, y}. */
    public int[] cardCentre(int index) {
        WingCard card = cards.get(index);
        return new int[] {card.getX() + card.getWidth() / 2, card.getY() + card.getHeight() / 2};
    }

    public int[] previewCentre() {
        WingsLayout.Rect p = layout.preview();
        return new int[] {p.x() + p.w() / 2, p.y() + p.h() / 2};
    }

    public WingsLayout.Screen layout() {
        return layout;
    }

    public String wornCard() {
        return cards.stream().filter(WingCard::worn).map(card -> card.wing().toString()).findFirst().orElse("");
    }
}
