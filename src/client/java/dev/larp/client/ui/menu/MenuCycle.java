package dev.larp.client.ui.menu;

import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A labeled row that cycles through a fixed list of values.
 * Click advances, shift-click (or arrow keys while focused) goes back.
 * The row card, label and value column all share the menu's padding rules,
 * so long labels are clipped instead of running into the value.
 */
public class MenuCycle extends MenuWidget {
    private final List<String> labels;
    private final IntSupplier index;
    private final IntConsumer setIndex;
    private final Runnable onChange;
    private final float textSize;

    public MenuCycle(int x, int y, int width, int height, String label, float textSize,
                     int revealDelayMillis, List<String> labels, IntSupplier index,
                     IntConsumer setIndex, Runnable onChange) {
        super(x, y, width, height, Component.literal(label), revealDelayMillis);
        this.labels = List.copyOf(labels);
        this.index = index;
        this.setIndex = setIndex;
        this.onChange = onChange;
        this.textSize = textSize;
    }

    public MenuCycle(int x, int y, int width, int height, String label, float textSize,
                     int revealDelayMillis, List<String> labels, IntSupplier index, IntConsumer setIndex) {
        this(x, y, width, height, label, textSize, revealDelayMillis, labels, index, setIndex, null);
    }

    private int current() {
        if (labels.isEmpty()) return -1;
        return Math.floorMod(index.getAsInt(), labels.size());
    }

    private void cycle(int direction) {
        if (!active || labels.isEmpty()) return;
        setIndex.accept(Math.floorMod(current() + direction, labels.size()));
        if (onChange != null) onChange.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        rowCard(graphics, textSize, hover, isFocused() && active, shown);
        rowAccent(graphics, textSize, rowEmphasis(hover), shown);

        float padX = MenuStyle.rowPadX(textSize);
        float innerX = getX() + padX;
        float innerW = Math.max(0f, getWidth() - padX * 2f);
        float centerY = getY() + getHeight() * 0.5f;

        float valueSize = MenuStyle.valueSize(textSize);
        float gap = textSize * 0.8f;
        int at = current();
        String value = at >= 0 ? MenuStyle.fit(labels.get(at), innerW * 0.45f, valueSize) : "";
        float valueWidth = value.isEmpty() ? 0f : Math.min(Text.width(value, valueSize), innerW * 0.45f);
        float labelWidth = Math.max(0f, innerW - (valueWidth > 0f ? valueWidth + gap : 0f));

        String label = MenuStyle.fit(getMessage().getString(), labelWidth, textSize);
        float slide = 3f * hover;
        int idle = active ? MenuStyle.TEXT_DIM : MenuStyle.TEXT_MUTED;
        glowText(graphics, label, innerX + slide, Text.centeredBaseline(label, textSize, centerY),
                textSize, idle, 0xFFFFFFFF, active ? hover : 0f, shown * (active ? 1f : 0.65f));

        if (!value.isEmpty()) {
            float valueX = getX() + getWidth() - padX - valueWidth;
            Text.draw(graphics, value, valueX, Text.centeredBaseline(value, valueSize, centerY), valueSize,
                    Colors.withAlpha(Colors.lerp(MenuStyle.VALUE_IDLE, 0xFFFFFFFF, hover), shown));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        // Shift-click steps backwards; matching the arrow-key direction.
        cycle((event.modifiers() & InputConstants.MOD_SHIFT) != 0 ? -1 : 1);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !isFocused()) return false;
        if (event.isSelection()) {
            cycle(1);
            return true;
        }
        if (event.isLeft()) {
            cycle(-1);
            return true;
        }
        if (event.isRight()) {
            cycle(1);
            return true;
        }
        return super.keyPressed(event);
    }
}
