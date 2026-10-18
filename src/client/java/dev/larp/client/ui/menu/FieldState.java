package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/**
 * Editable single-line text held outside any widget so screens survive
 * rebuilds (resize re-runs {@code init()}). Shared by {@link MenuField}
 * and the rename prompt dialog.
 */
public final class FieldState {
    private static final long BLINK_MILLIS = 500L;

    private final int maxLength;
    private final Predicate<String> filter;
    private String value;
    private int cursor;
    private boolean selectAll;

    public FieldState(String initial, int maxLength, Predicate<String> filter) {
        this.value = initial == null ? "" : initial;
        this.maxLength = maxLength;
        this.filter = filter == null ? s -> true : filter;
        this.cursor = this.value.length();
    }

    public String value() {
        return value;
    }

    public void set(String value) {
        this.value = value == null ? "" : value;
        this.cursor = Math.clamp(cursor, 0, this.value.length());
    }

    public boolean keyPressed(KeyEvent event) {
        cursor = Math.clamp(cursor, 0, value.length());
        if (event.isSelectAll()) {
            selectAll = true;
        } else if (event.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
        } else if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
        } else if (event.isLeft()) {
            cursor = Math.max(0, cursor - 1);
            selectAll = false;
        } else if (event.isRight()) {
            cursor = Math.min(value.length(), cursor + 1);
            selectAll = false;
        } else if (event.key() == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            selectAll = false;
        } else if (event.key() == GLFW.GLFW_KEY_END) {
            cursor = value.length();
            selectAll = false;
        } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
            delete(event.key() == GLFW.GLFW_KEY_BACKSPACE);
        } else {
            return false;
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        insert(event.codepointAsString());
        return true;
    }

    public void clickEnd() {
        cursor = value.length();
        selectAll = false;
    }

    public void selectEverything() {
        cursor = value.length();
        selectAll = true;
    }

    private void delete(boolean backspace) {
        if (selectAll) {
            value = "";
            cursor = 0;
            selectAll = false;
        } else if (backspace && cursor > 0) {
            value = value.substring(0, cursor - 1) + value.substring(cursor);
            cursor--;
        } else if (!backspace && cursor < value.length()) {
            value = value.substring(0, cursor) + value.substring(cursor + 1);
        }
    }

    private void insert(String typed) {
        if (typed == null || typed.isEmpty()) return;
        StringBuilder clean = new StringBuilder();
        typed.codePoints().filter(c -> c >= 32 && c != 127).forEach(clean::appendCodePoint);
        String candidate = selectAll ? "" : value;
        int room = Math.max(0, maxLength - candidate.length());
        String added = clean.substring(0, Math.min(clean.length(), room));
        String merged = selectAll
                ? added
                : candidate.substring(0, cursor) + added + candidate.substring(Math.min(cursor, candidate.length()));
        if (!filter.test(merged)) return;
        value = merged;
        cursor = selectAll ? added.length() : Math.min(cursor + added.length(), value.length());
        selectAll = false;
    }

    /** Draws the box, text (or placeholder) and blinking caret. */
    public void draw(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                     float textSize, boolean focused, float shown, String placeholder) {
        int radius = Math.max(3, Math.round(height * 0.22f));
        Draw.rect(graphics, Math.round(x), Math.round(y), Math.round(width), Math.round(height), radius,
                Colors.withAlpha(MenuStyle.FIELD_FILL, shown));
        Draw.rect(graphics, Math.round(x), Math.round(y), Math.round(width), 1, 0,
                Colors.withAlpha(focused ? 0xFF9A9A9A : MenuStyle.FIELD_BORDER, shown));
        float textX = x + 8f;
        float centerY = y + height * 0.5f;
        String visible = value.isEmpty() ? Text.fit(placeholder, width - 16f, textSize) : Text.fit(value, width - 16f, textSize);
        Text.draw(graphics, visible, textX, Text.centeredBaseline(visible, textSize, centerY), textSize,
                Colors.withAlpha(value.isEmpty() ? MenuStyle.TEXT_MUTED : MenuStyle.TEXT_IDLE, shown));
        if (focused && (Util.getMillis() / BLINK_MILLIS) % 2L == 0L) {
            String beforeCursor = value.substring(0, Math.min(cursor, value.length()));
            float cursorX = Math.min(x + width - 5f, textX + Text.width(beforeCursor, textSize));
            Draw.rect(graphics, cursorX, centerY - textSize * 0.45f, 1f, Math.round(textSize * 0.9f), 0,
                    Colors.withAlpha(0xFFFFFFFF, shown));
        }
    }
}
