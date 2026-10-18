package dev.larp.client.ui.menu;

import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Base class for every Larp menu widget.
 *
 * <p>Like {@code LarpButton}, all motion is driven by
 * {@link Util#getMillis()} with frame-rate independent exponential smoothing,
 * never by game ticks. Subclasses get a staggered {@code easeOutCubic}
 * reveal and a 0-to-1 {@code hoverProgress} for the white glow.</p>
 */
public abstract class MenuWidget extends AbstractWidget {
    private static final float HOVER_RESPONSE_MILLIS = 85f;

    /** 0 = idle, 1 = fully hovered. Eased toward the hover target every frame. */
    protected float hoverProgress;
    private final long revealStart;
    private long lastFrameMillis;

    protected MenuWidget(int x, int y, int width, int height, Component message, int revealDelayMillis) {
        super(x, y, width, height, message);
        long now = Util.getMillis();
        this.revealStart = now + revealDelayMillis;
        this.lastFrameMillis = now;
    }

    /** Advances the hover glow and returns it. Call once per rendered frame. */
    protected float trackHover() {
        long now = Util.getMillis();
        float frameMillis = Math.clamp(now - lastFrameMillis, 0L, 50L);
        lastFrameMillis = now;
        float target = active && isHoveredOrFocused() ? 1f : 0f;
        float blend = 1f - (float) Math.exp(-frameMillis / HOVER_RESPONSE_MILLIS);
        hoverProgress += (target - hoverProgress) * blend;
        if (Math.abs(target - hoverProgress) < 0.001f) hoverProgress = target;
        return hoverProgress;
    }

    /** Paints this widget's row surface using the shared card style. */
    protected void rowCard(GuiGraphicsExtractor graphics, float textSize, float hover,
                           boolean selected, float shown) {
        MenuStyle.card(graphics, getX(), getY(), getWidth(), getHeight(), textSize * 0.42f,
                hover, selected, shown);
    }

    /** Paints the left accent marker for hovered, focused or selected rows. */
    protected void rowAccent(GuiGraphicsExtractor graphics, float textSize,
                             float strength, float shown) {
        if (strength <= 0.01f) return;
        MenuStyle.accent(graphics, getX() + textSize * 0.16f, getY() + MenuStyle.rowPadY(textSize),
                getHeight() - MenuStyle.rowPadY(textSize) * 2f, strength, shown);
    }

    /** 0-to-1 emphasis used for cards: hover plus keyboard focus. */
    protected float rowEmphasis(float hover) {
        return Math.max(hover, isFocused() ? 1f : 0f);
    }

    /** Staggered fade-in shared by the whole screen. */
    protected float reveal() {
        long elapsed = Util.getMillis() - revealStart;
        if (elapsed <= 0L) return 0f;
        return MenuStyle.easeOutCubic(elapsed / (float) MenuStyle.REVEAL_MILLIS);
    }

    /**
     * Draws flat text with the soft white bloom used across the menus.
     *
     * @param glow 0-to-1 hover glow strength
     * @param shown 0-to-1 reveal alpha
     */
    protected void glowText(GuiGraphicsExtractor graphics, String label, float x, float baseline,
                            float size, int idleColor, int hoverColor, float glow, float shown) {
        float glowAlpha = glow * glow * 0.09f * shown;
        if (glowAlpha > 0.001f) {
            int bloom = Colors.withAlpha(hoverColor, glowAlpha);
            float radius = 1.6f + size * 0.05f;
            Text.draw(graphics, label, x - radius, baseline, size, bloom);
            Text.draw(graphics, label, x + radius, baseline, size, bloom);
            Text.draw(graphics, label, x, baseline - radius, size, bloom);
            Text.draw(graphics, label, x, baseline + radius, size, bloom);
        }
        int color = Colors.lerp(idleColor, hoverColor, glow);
        Text.draw(graphics, label, x, baseline, size, Colors.withAlpha(color, shown));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
