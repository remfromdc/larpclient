package dev.larp.client.ui.title;

import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * A text-only menu entry for {@link LarpTitleScreen}.
 *
 * <p>There is deliberately no background quad: the widget is just an invisible
 * hitbox around a line of the client's MSDF font. Every animation is driven by
 * wall-clock milliseconds ({@link Util#getMillis()}) instead of the 20 TPS game
 * tick, so the hover glow stays smooth at any frame rate.</p>
 */
public final class LarpButton extends AbstractWidget {
    private static final int REVEAL_MILLIS = 620;
    private static final float HOVER_RESPONSE_MILLIS = 85f;
    private static final int IDLE_COLOR = 0xFF8F8F8F;
    private static final int HOVER_COLOR = 0xFFFFFFFF;

    private final Runnable action;
    private final float textSize;
    private final long revealStart;

    /** 0 = idle, 1 = fully hovered. Eased toward the hover target every frame. */
    private float hoverProgress;
    private long lastFrameMillis;

    public LarpButton(int x, int y, int width, int height, Component message, float textSize,
                      int revealDelayMillis, Runnable action) {
        super(x, y, width, height, message);
        this.action = action;
        this.textSize = textSize;
        this.revealStart = Util.getMillis() + revealDelayMillis;
        this.lastFrameMillis = Util.getMillis();
    }

    /** easeOutCubic(t) = 1 - (1 - t)^3, clamped to [0, 1]. */
    private static float easeOutCubic(float t) {
        t = Math.clamp(t, 0f, 1f);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    private float reveal() {
        long elapsed = Util.getMillis() - revealStart;
        if (elapsed <= 0L) return 0f;
        return easeOutCubic(elapsed / (float) REVEAL_MILLIS);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = Util.getMillis();
        // Clamp the frame delta so tabbing back into the window cannot snap the glow.
        float frameMillis = Math.clamp(now - lastFrameMillis, 0L, 50L);
        lastFrameMillis = now;

        boolean hovered = active && isHoveredOrFocused();
        float target = hovered ? 1f : 0f;
        // Frame-rate independent exponential smoothing (a lerp with a real-time factor).
        float blend = 1f - (float) Math.exp(-frameMillis / HOVER_RESPONSE_MILLIS);
        hoverProgress += (target - hoverProgress) * blend;
        if (Math.abs(target - hoverProgress) < 0.001f) hoverProgress = target;

        float shown = reveal();
        if (shown <= 0f) return;

        String label = getMessage().getString();
        float slide = 6f * hoverProgress;
        float x = getX() + slide;
        float centerY = getY() + getHeight() * 0.5f;
        float baseline = Text.centeredBaseline(label, textSize, centerY);

        // Soft white glow: several low-alpha offset copies fade in with the hover.
        float glowAlpha = hoverProgress * hoverProgress * 0.09f * shown;
        if (glowAlpha > 0.001f) {
            int glow = Colors.withAlpha(HOVER_COLOR, glowAlpha);
            float radius = 1.6f + textSize * 0.05f;
            Text.draw(graphics, label, x - radius, baseline, textSize, glow);
            Text.draw(graphics, label, x + radius, baseline, textSize, glow);
            Text.draw(graphics, label, x, baseline - radius, textSize, glow);
            Text.draw(graphics, label, x, baseline + radius, textSize, glow);
        }

        int color = Colors.lerp(IDLE_COLOR, HOVER_COLOR, hoverProgress);
        Text.draw(graphics, label, x, baseline, textSize, Colors.withAlpha(color, shown));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        activate();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && event.isSelection()) {
            activate();
            return true;
        }
        return super.keyPressed(event);
    }

    private void activate() {
        if (action != null) action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
