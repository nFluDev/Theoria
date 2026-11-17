package dev.theoria.wings.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One catalog entry: the wing's name on a vanilla button; the worn wing gets a green edge and a green name. A card half hidden by the
 * list's scrolling must not react to the mouse where it is clipped, so hit-testing also honours the list's vertical clip.
 */
final class WingCard extends Button {
    private static final int WORN_COLOR = 0xFF55FF55;
    private final Identifier wing;
    private final Component plainName;
    private boolean worn;
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    WingCard(Button.Builder builder, Identifier wing, Component name) {
        super(builder);
        this.wing = wing;
        this.plainName = name;
    }

    Identifier wing() {
        return wing;
    }

    boolean worn() {
        return worn;
    }

    void setWorn(boolean worn) {
        this.worn = worn;
        setMessage(worn ? plainName.copy().withStyle(ChatFormatting.GREEN) : plainName);
    }

    void clipTo(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractDefaultSprite(g);
        if (worn) {
            g.outline(getX(), getY(), getWidth(), getHeight(), WORN_COLOR);
            g.fill(getX() + 2, getY() + 3, getX() + 5, getY() + getHeight() - 3, WORN_COLOR);
        }
        extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
