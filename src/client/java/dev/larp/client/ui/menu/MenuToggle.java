package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A labeled ON/OFF row with an animated pill switch on the right.
 * The row is a card with text padding, and the label is fitted to the space
 * left of the switch so the two can never overlap.
 */
public class MenuToggle extends MenuWidget {
    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private final float textSize;

    /** Smoothed 0-to-1 knob position, mirroring the real value. */
    private float knobProgress;
    private long lastKnobFrame;

    public MenuToggle(int x, int y, int width, int height, String label, float textSize,
                      int revealDelayMillis, BooleanSupplier get, Consumer<Boolean> set) {
        super(x, y, width, height, Component.literal(label), revealDelayMillis);
        this.get = get;
        this.set = set;
        this.textSize = textSize;
        this.knobProgress = get.getAsBoolean() ? 1f : 0f;
        this.lastKnobFrame = Util.getMillis();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        rowCard(graphics, textSize, hover, isFocused() && active, shown);
        rowAccent(graphics, textSize, rowEmphasis(hover), shown);

        long now = Util.getMillis();
        float frameMillis = Math.clamp(now - lastKnobFrame, 0L, 50L);
        lastKnobFrame = now;
        float target = get.getAsBoolean() ? 1f : 0f;
        float blend = 1f - (float) Math.exp(-frameMillis / 90f);
        knobProgress += (target - knobProgress) * blend;
        if (Math.abs(target - knobProgress) < 0.001f) knobProgress = target;

        float padX = MenuStyle.rowPadX(textSize);
        float innerX = getX() + padX;
        float innerW = Math.max(0f, getWidth() - padX * 2f);
        float centerY = getY() + getHeight() * 0.5f;

        float trackWidth = Math.max(30f, textSize * 2.4f);
        float trackHeight = Math.min(textSize * 1.05f, getHeight() * 0.5f);
        float reserve = trackWidth + textSize * 1.1f;
        float labelWidth = Math.max(0f, innerW - reserve);
        String label = MenuStyle.fit(getMessage().getString(), labelWidth, textSize);
        float slide = 3f * hover;
        int idle = active ? MenuStyle.TEXT_DIM : MenuStyle.TEXT_MUTED;
        glowText(graphics, label, innerX + slide, Text.centeredBaseline(label, textSize, centerY),
                textSize, idle, 0xFFFFFFFF, active ? hover : 0f, shown * (active ? 1f : 0.65f));

        float trackX = getX() + getWidth() - padX - trackWidth;
        float trackY = centerY - trackHeight / 2f;
        int track = Colors.lerp(0xFF2E2E2E, 0xFFEDEDED, knobProgress);
        if (!active) track = Colors.withAlpha(track, 0.55f);
        Draw.rect(graphics, trackX, trackY, trackWidth, Math.round(trackHeight),
                Math.round(trackHeight / 2f), Colors.withAlpha(track, shown));
        float knobDiameter = Math.max(2f, trackHeight - 4f);
        float knobX = trackX + 2f + (trackWidth - knobDiameter - 4f) * knobProgress;
        int knob = Colors.lerp(0xFF777777, 0xFF111111, knobProgress);
        Draw.rect(graphics, knobX, trackY + 2f, knobDiameter, Math.round(knobDiameter),
                Math.round(knobDiameter / 2f), Colors.withAlpha(knob, shown));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        flip();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && event.isSelection()) {
            flip();
            return true;
        }
        return super.keyPressed(event);
    }

    private void flip() {
        if (!active) return;
        set.accept(!get.getAsBoolean());
    }
}
