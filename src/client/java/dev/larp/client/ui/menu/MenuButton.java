package dev.larp.client.ui.menu;

import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A menu row: title on the left, optional value on the right and an optional
 * description underneath. The row paints its own card so the clickable area
 * is visible, and both text columns are measured and fitted, which keeps a
 * long title from ever colliding with the value.
 */
public class MenuButton extends MenuWidget {
    public enum Variant { NORMAL, DANGER }

    private final Runnable action;
    private final float textSize;
    private final Variant variant;
    private String value = "";
    private String description = "";
    private boolean enabled = true;

    public MenuButton(int x, int y, int width, int height, String label, float textSize,
                      int revealDelayMillis, Runnable action) {
        this(x, y, width, height, label, textSize, revealDelayMillis, Variant.NORMAL, action);
    }

    public MenuButton(int x, int y, int width, int height, String label, float textSize,
                      int revealDelayMillis, Variant variant, Runnable action) {
        super(x, y, width, height, Component.literal(label), revealDelayMillis);
        this.textSize = textSize;
        this.variant = variant;
        this.action = action;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    /** Adds a muted second line; the row should be sized with {@link #preferredHeight()}. */
    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    /** Height this row needs to hold its text without clipping. */
    public float preferredHeight() {
        return MenuStyle.autoHeight(textSize, !description.isEmpty());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.active = enabled;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        boolean twoLine = !description.isEmpty();
        boolean focused = isFocused() && active;

        rowCard(graphics, textSize, hover, focused, shown);
        rowAccent(graphics, textSize, rowEmphasis(hover), shown);

        float padX = MenuStyle.rowPadX(textSize);
        float padY = MenuStyle.rowPadY(textSize);
        float innerX = getX() + padX;
        float innerW = Math.max(0f, getWidth() - padX * 2f);
        float titleLine = MenuStyle.titleLine(textSize);
        float titleCenter = twoLine
                ? getY() + padY + titleLine * 0.5f
                : getY() + getHeight() * 0.5f;

        float valueSize = MenuStyle.valueSize(textSize);
        float gap = textSize * 0.8f;
        float valueLimit = innerW * 0.4f;
        String shownValue = value.isEmpty() ? "" : MenuStyle.fit(value, valueLimit, valueSize);
        float valueWidth = shownValue.isEmpty() ? 0f : Math.min(Text.width(shownValue, valueSize), valueLimit);
        float titleWidth = Math.max(0f, innerW - (valueWidth > 0f ? valueWidth + gap : 0f));

        String label = MenuStyle.fit(getMessage().getString(), titleWidth, textSize);
        float slide = 3f * hover;
        float baseline = Text.centeredBaseline(label, textSize, titleCenter);

        int idle = variant == Variant.DANGER ? 0xFFB07A7A : MenuStyle.TEXT_DIM;
        int hovered = variant == Variant.DANGER ? MenuStyle.DANGER : 0xFFFFFFFF;
        if (!enabled) {
            Text.draw(graphics, label, innerX, baseline, textSize,
                    Colors.withAlpha(MenuStyle.TEXT_MUTED, shown * 0.6f));
        } else {
            glowText(graphics, label, innerX + slide, baseline, textSize, idle, hovered, hover, shown);
        }

        if (!shownValue.isEmpty()) {
            float valueX = getX() + getWidth() - padX - valueWidth;
            int valueIdle = variant == Variant.DANGER && !enabled
                    ? MenuStyle.TEXT_MUTED : MenuStyle.VALUE_IDLE;
            int valueColor = Colors.lerp(valueIdle, 0xFFFFFFFF, hover);
            Text.draw(graphics, shownValue, valueX,
                    Text.centeredBaseline(shownValue, valueSize, titleCenter), valueSize,
                    Colors.withAlpha(valueColor, shown * (enabled ? 1f : 0.6f)));
        }

        if (twoLine) {
            float descSize = MenuStyle.descSize(textSize);
            float descCenter = getY() + getHeight() - padY - descSize * 0.62f;
            String desc = MenuStyle.fit(description, innerW, descSize);
            Text.draw(graphics, desc, innerX,
                    Text.centeredBaseline(desc, descSize, descCenter), descSize,
                    Colors.withAlpha(MenuStyle.TEXT_DIM, shown));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (enabled) activate();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (enabled && active && isFocused() && event.isSelection()) {
            activate();
            return true;
        }
        return super.keyPressed(event);
    }

    private void activate() {
        if (action != null) action.run();
    }
}
