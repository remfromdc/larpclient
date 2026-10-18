package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Smooth, real-time scroll state for list screens.
 * The offset eases toward its target every rendered frame so wheel scrolling
 * stays fluid at any frame rate; rows are repositioned by the owning screen.
 */
public final class ScrollPanel {
    private static final float RESPONSE_MILLIS = 110f;

    private float scroll;
    private float target;
    private long lastFrame = Util.getMillis();

    public void scrollBy(float pixels, float maxScroll) {
        target = Math.clamp(target + pixels, 0f, Math.max(0f, maxScroll));
    }

    public void snapTo(float pixels, float maxScroll) {
        target = Math.clamp(pixels, 0f, Math.max(0f, maxScroll));
        scroll = target;
    }

    public void clamp(float maxScroll) {
        target = Math.clamp(target, 0f, Math.max(0f, maxScroll));
        scroll = Math.clamp(scroll, 0f, Math.max(0f, maxScroll));
    }

    /** Advances the smoothing and returns the current offset in pixels. */
    public float update() {
        long now = Util.getMillis();
        float frameMillis = Math.clamp(now - lastFrame, 0L, 50L);
        lastFrame = now;
        float blend = 1f - (float) Math.exp(-frameMillis / RESPONSE_MILLIS);
        scroll += (target - scroll) * blend;
        if (Math.abs(target - scroll) < 0.05f) scroll = target;
        return scroll;
    }

    public float offset() {
        return scroll;
    }

    /** Slim scrollbar on the right edge of the viewport; no-op when it all fits. */
    public void drawScrollbar(GuiGraphicsExtractor graphics, float x, float y, float height,
                              float contentHeight, float shown) {
        if (contentHeight <= height || shown <= 0f) return;
        if (!(height > 0f) || height > 20_000f || !(contentHeight > 0f)) {
            // A viewport that collapsed or was read before layout must not
            // submit a rectangle the vertex format cannot represent.
            return;
        }
        float trackWidth = 3f;
        Draw.rect(graphics, x, y, trackWidth, Math.round(height), Math.round(trackWidth),
                Colors.withAlpha(0xFF2A2A2A, shown));
        float knobHeight = Math.max(18f, height * height / contentHeight);
        float travel = height - knobHeight;
        float knobY = y + travel * (target / (contentHeight - height));
        Draw.rect(graphics, x, knobY, trackWidth, Math.round(knobHeight), Math.round(trackWidth),
                Colors.withAlpha(0xFF8A8A8A, shown * 0.8f));
    }
}
