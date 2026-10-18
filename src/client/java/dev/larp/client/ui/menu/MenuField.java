package dev.larp.client.ui.menu;

import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Predicate;

/**
 * A stacked text input: small muted label above a dark field box.
 * Typing state lives in a caller-owned {@link FieldState} so rebuilds
 * (for example on window resize) never lose what was typed.
 */
public class MenuField extends MenuWidget {
    private final FieldState state;
    private final float textSize;
    private final String placeholder;

    public MenuField(int x, int y, int width, int height, String label, float textSize,
                     int revealDelayMillis, FieldState state, String placeholder) {
        super(x, y, width, height, Component.literal(label), revealDelayMillis);
        this.state = state;
        this.textSize = textSize;
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        trackHover();

        float padX = MenuStyle.rowPadX(textSize);
        float innerX = getX() + padX;
        float innerW = Math.max(1f, getWidth() - padX * 2f);
        float labelSize = MenuStyle.descSize(textSize);
        String label = MenuStyle.fit(getMessage().getString(), innerW, labelSize);
        Text.draw(graphics, label, innerX, getY() + labelSize * 0.95f, labelSize,
                Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
        float boxY = getY() + labelSize + 6f;
        float boxHeight = Math.max(1f, getHeight() - labelSize - 8f);
        state.draw(graphics, innerX, boxY, innerW, boxHeight, textSize, isFocused(), shown, placeholder);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) state.selectEverything();
        else state.clickEnd();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && state.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return active && isFocused() && state.charTyped(event);
    }

    /** Convenience filter: world and server names accept almost anything. */
    public static Predicate<String> freeText() {
        return s -> true;
    }

    /** Server addresses stay single-line without spaces. */
    public static Predicate<String> noSpaces() {
        return s -> !s.contains(" ") && !s.contains("\n") && !s.contains("\t");
    }
}
