package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look-and-feel for every Larp menu screen.
 *
 * <p>The palette, left-aligned layout metrics and animation curves mirror
 * {@code LarpTitleScreen} so settings, world and server flows feel like the
 * same client. All timing uses wall-clock milliseconds.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MenuStyle {
    public static final int BACKGROUND_TOP = 0xFF111111;
    public static final int BACKGROUND_BOTTOM = 0xFF222222;
    public static final int TEXT_IDLE = 0xFFE9E9E9;
    public static final int TEXT_DIM = 0xFF9A9A9A;
    public static final int TEXT_MUTED = 0xFF6D6D6D;
    public static final int VALUE_IDLE = 0xFF8F8F8F;
    public static final int DANGER = 0xFFFF8080;
    public static final int FIELD_FILL = 0xFF181818;
    public static final int FIELD_BORDER = 0xFF2E2E2E;
    public static final int TRACK = 0xFF333333;
    public static final int KNOB = 0xFFEDEDED;

    /** Row surfaces: barely visible at rest, lifting on hover and selection. */
    public static final int CARD_IDLE = 0x09FFFFFF;
    public static final int CARD_HOVER = 0x16FFFFFF;
    public static final int CARD_SELECTED = 0x24FFFFFF;
    public static final int CARD_BORDER = 0x1CFFFFFF;
    public static final int ACCENT = 0xFFFFFFFF;

    public static final int REVEAL_MILLIS = 520;
    public static final int STAGGER_MILLIS = 35;

    /** easeOutCubic(t) = 1 - (1 - t)^3, clamped to [0, 1]. */
    public static float easeOutCubic(float t) {
        t = Math.clamp(t, 0f, 1f);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    /** Matches the title screen's responsive scale. */
    public static float uiFor(int width, int height) {
        return Math.clamp(Math.min(width / 1280f, height / 720f), 0.62f, 1.15f);
    }

    // ------------------------------------------------------------------
    // Row metrics
    //
    // Every metric is expressed as a multiple of the row's text size so the
    // whole system scales with the window and Minecraft's GUI scale without
    // a second unit to keep in sync.
    // ------------------------------------------------------------------

    /** Horizontal breathing room inside a row, left and right. */
    public static float rowPadX(float textSize) {
        return textSize * 0.95f;
    }

    /** Vertical breathing room inside a row, top and bottom. */
    public static float rowPadY(float textSize) {
        return textSize * 0.6f;
    }

    /** Gap between consecutive rows. */
    public static float rowGap(float textSize) {
        return textSize * 0.42f;
    }

    /** Small second line under a row title. */
    public static float descSize(float textSize) {
        return textSize * 0.76f;
    }

    /** Right-hand value, readout or key binding. */
    public static float valueSize(float textSize) {
        return textSize * 0.9f;
    }

    /** Height of the title line's box, including ascender and descender room. */
    public static float titleLine(float textSize) {
        return textSize * 1.3f;
    }

    /**
     * Natural height of a row. Callers that want a title plus description
     * should use this instead of hard-coding a pixel height, so the row can
     * never be shorter than the text it holds.
     */
    public static float autoHeight(float textSize, boolean description) {
        float height = rowPadY(textSize) * 2f + titleLine(textSize);
        if (description) height += descSize(textSize) * 1.25f + textSize * 0.2f;
        return height;
    }

    /** Clips text to a column, returning "" when there is no room at all. */
    public static String fit(String text, float width, float size) {
        if (text == null || text.isEmpty() || width <= 0f) return "";
        return Text.fit(text, width, size);
    }

    /**
     * Paints a row surface. Rows are cards rather than bare text so the
     * clickable area is visible; the border only appears once the row is
     * hovered, focused or selected.
     */
    public static void card(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                            float radius, float hover, boolean selected, float shown) {
        if (width <= 0f || height <= 0f || shown <= 0f) return;
        if (width > 10_000f || height > 10_000f) {
            // A transient pre-layout rectangle must never take the game down.
            return;
        }
        int fill = selected ? CARD_SELECTED : Colors.lerp(CARD_IDLE, CARD_HOVER, hover);
        float strength = Math.max(hover, selected ? 1f : 0f);
        int border = Colors.withAlpha(CARD_BORDER, shown * Math.max(0.45f, strength));
        Draw.bordered(graphics, x, y, width, Math.max(1, Math.round(height)),
                Math.max(0f, radius), Colors.withAlpha(fill, shown), border);
    }

    /** The thin vertical marker that highlights the active row. */
    public static void accent(GuiGraphicsExtractor graphics, float x, float y, float height,
                              float strength, float shown) {
        if (strength <= 0.01f || shown <= 0f) return;
        Draw.rect(graphics, x, y, Math.max(2f, 2.5f), Math.max(1, Math.round(height)), 1,
                Colors.withAlpha(ACCENT, shown * 0.9f * Math.clamp(strength, 0f, 1f)));
    }

    /**
     * Turns SCREAMING_SNAKE_CASE (or a raw value name) into a short "Title Case"
     * label. Already-friendly strings pass through untouched.
     */
    public static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        boolean shouty = raw.chars().anyMatch(c -> c == '_')
                || raw.equals(raw.toUpperCase()) && raw.chars().anyMatch(Character::isLetter);
        if (!shouty) return raw;
        String[] words = raw.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            out.append(word.substring(1));
        }
        return out.toString();
    }

    /** Dark vertical gradient with the same soft glow discs as the title screen. */
    public static void background(GuiGraphicsExtractor graphics, int width, int height, float ui) {
        graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        float glowX = width * 0.72f;
        float glowY = height * 0.52f;
        Draw.rect(graphics, glowX - 300f * ui, glowY - 300f * ui, 600f * ui,
                Math.round(600f * ui), Math.round(300f * ui), 0x0EFFFFFF);
        Draw.rect(graphics, width * 0.18f - 220f * ui, height * 0.22f - 220f * ui,
                440f * ui, Math.round(440f * ui), Math.round(220f * ui), 0x08000000);
        graphics.fillGradient(0, 0, Math.round(260f * ui), height, 0x66000000, 0x00000000);
    }

    /** Minimalist Star of David wireframe, drawn from rotated rounded rects. */
    public static void star(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius,
                            float thickness, int color) {
        if (Colors.transparent(color)) return;
        triangle(graphics, centerX, centerY, radius, -90f, thickness, color);
        triangle(graphics, centerX, centerY, radius, 90f, thickness, color);
    }

    private static void triangle(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius,
                                 float startDegrees, float thickness, int color) {
        float[] xs = new float[3];
        float[] ys = new float[3];
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(startDegrees + i * 120.0);
            xs[i] = centerX + (float) Math.cos(angle) * radius;
            ys[i] = centerY + (float) Math.sin(angle) * radius;
        }
        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            line(graphics, xs[i], ys[i], xs[next], ys[next], thickness, color);
        }
    }

    private static void line(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2,
                             float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001f || Colors.transparent(color)) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x1, y1).rotate((float) Math.atan2(dy, dx)).scale(1f, thickness);
            Draw.rect(graphics, 0f, 0f, length, 1, 0, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
