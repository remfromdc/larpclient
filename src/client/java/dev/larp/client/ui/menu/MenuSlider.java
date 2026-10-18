package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * A labeled slider row working in normalized 0-to-1 space.
 *
 * <p>The row reserves three columns: a fitted label, the draggable track and
 * a right-aligned readout. All three are derived from the row's own width,
 * so the label can never run under the track and the readout never leaves
 * the card, at any window size.</p>
 */
public class MenuSlider extends MenuWidget {
    private final DoubleSupplier get;
    private final DoubleConsumer set;
    private final Function<Double, String> readout;
    private final float textSize;
    private boolean dragging;
    /** Frozen while dragging so the track never shifts under the cursor. */
    private float stableReadoutWidth = -1f;

    public MenuSlider(int x, int y, int width, int height, String label, float textSize,
                      int revealDelayMillis, DoubleSupplier get, DoubleConsumer set,
                      Function<Double, String> readout) {
        super(x, y, width, height, Component.literal(label), revealDelayMillis);
        this.get = get;
        this.set = set;
        this.readout = readout;
        this.textSize = textSize;
    }

    public MenuSlider(int x, int y, int width, int height, String label, float textSize,
                      int revealDelayMillis, DoubleSupplier get, DoubleConsumer set) {
        this(x, y, width, height, label, textSize, revealDelayMillis, get, set,
                value -> String.valueOf(Math.round(value * 100.0) / 100.0));
    }

    private float fraction() {
        return Math.clamp((float) get.getAsDouble(), 0f, 1f);
    }

    private float readoutSize() {
        return MenuStyle.valueSize(textSize);
    }

    private float padX() {
        return MenuStyle.rowPadX(textSize);
    }

    private float innerWidth() {
        return Math.max(0f, getWidth() - padX() * 2f);
    }

    private float readoutWidth() {
        if (dragging && stableReadoutWidth > 0f) return stableReadoutWidth;
        float measured = Text.width(formatDisplay(), readoutSize());
        // The upper bound can be smaller than the lower one on the first
        // frame of a resize, so order the bounds before clamping.
        float limit = Math.max(1f, innerWidth() * 0.32f);
        return Math.min(Math.max(measured, Math.min(textSize * 2.2f, limit)), limit);
    }

    private float labelWidth() {
        return Math.min(innerWidth() * 0.42f, textSize * 8.4f);
    }

    private float trackX() {
        return getX() + padX() + labelWidth() + textSize * 0.7f;
    }

    private float trackRight() {
        return getX() + getWidth() - padX() - readoutWidth() - textSize * 0.45f;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        rowCard(graphics, textSize, hover, isFocused() && active, shown);
        rowAccent(graphics, textSize, rowEmphasis(hover), shown);
        // Nothing below can be measured meaningfully before the first layout.
        if (innerWidth() < textSize * 2f) return;

        float centerY = getY() + getHeight() * 0.5f;
        float innerX = getX() + padX();

        String label = MenuStyle.fit(getMessage().getString(), labelWidth(), textSize);
        float slide = 2f * hover;
        int idle = active ? MenuStyle.TEXT_DIM : MenuStyle.TEXT_MUTED;
        glowText(graphics, label, innerX + slide, Text.centeredBaseline(label, textSize, centerY),
                textSize, idle, 0xFFFFFFFF, active ? hover : 0f, shown * (active ? 1f : 0.65f));

        String readout = formatDisplay();
        float readoutWidth = readoutWidth();
        if (dragging) stableReadoutWidth = readoutWidth;
        String shownReadout = MenuStyle.fit(readout, readoutWidth, readoutSize());
        float readoutX = getX() + getWidth() - padX() - readoutWidth;
        Text.draw(graphics, shownReadout, readoutX,
                Text.centeredBaseline(shownReadout, readoutSize(), centerY), readoutSize(),
                Colors.withAlpha(Colors.lerp(MenuStyle.VALUE_IDLE, 0xFFFFFFFF, hover), shown));

        float trackX = trackX();
        float trackRight = trackRight();
        if (trackRight - trackX < textSize * 1.5f) return;
        float trackHeight = Math.max(3f, textSize * 0.22f);
        float trackY = centerY - trackHeight / 2f;
        Draw.rect(graphics, trackX, trackY, trackRight - trackX, Math.max(1, Math.round(trackHeight)),
                Math.round(trackHeight / 2f), Colors.withAlpha(MenuStyle.TRACK, shown));
        float fillWidth = (trackRight - trackX) * fraction();
        if (fillWidth > 0.5f) {
            Draw.rect(graphics, trackX, trackY, fillWidth, Math.max(1, Math.round(trackHeight)),
                    Math.round(trackHeight / 2f),
                    Colors.withAlpha(0xFFEDEDED, shown * (0.55f + 0.45f * hover)));
        }
        float knobDiameter = trackHeight + 7f;
        float knobX = trackX + (trackRight - trackX) * fraction() - knobDiameter / 2f;
        Draw.rect(graphics, knobX, centerY - knobDiameter / 2f, knobDiameter, Math.round(knobDiameter),
                Math.round(knobDiameter / 2f), Colors.withAlpha(MenuStyle.KNOB, shown));
    }

    private String formatDisplay() {
        try {
            String text = readout.apply(get.getAsDouble());
            return text == null ? "" : text;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private void moveTo(double mouseX) {
        float trackX = trackX();
        float width = Math.max(1f, trackRight() - trackX);
        float fraction = Math.clamp((float) ((mouseX - trackX) / width), 0f, 1f);
        set.accept(fraction);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!active) return;
        dragging = true;
        moveTo(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (dragging && active) moveTo(event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = false;
        stableReadoutWidth = -1f;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!active || !isFocused()) return false;
        if (event.isLeft() || event.isRight()) {
            double step = (event.isRight() ? 1 : -1) * (event.hasShiftDown() ? 0.2 : 0.05);
            set.accept(Math.clamp(get.getAsDouble() + step, 0.0, 1.0));
            return true;
        }
        return super.keyPressed(event);
    }
}
