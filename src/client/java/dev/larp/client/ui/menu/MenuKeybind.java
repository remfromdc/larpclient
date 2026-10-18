package dev.larp.client.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;

/**
 * A key-bind row: action label on the left, current binding on the right.
 * Click to capture; the next key or mouse button becomes the binding,
 * Escape cancels. Conflicts with another mapping tint the readout red.
 */
public class MenuKeybind extends MenuWidget {
    private final KeyMapping mapping;
    private final BooleanSupplier conflict;
    private final float textSize;
    private boolean capturing;

    public MenuKeybind(int x, int y, int width, int height, KeyMapping mapping, float textSize,
                       int revealDelayMillis, BooleanSupplier conflict) {
        super(x, y, width, height, Component.literal(keyLabel(mapping)), revealDelayMillis);
        this.mapping = mapping;
        this.conflict = conflict;
        this.textSize = textSize;
    }

    private static String keyLabel(KeyMapping mapping) {
        // Vanilla labels are translation keys ("key.jump"); the readable text
        // comes from the language files, so resolve through the client.
        String raw = Component.translatable(mapping.getName()).getString();
        if (raw.equals(mapping.getName())) {
            String name = mapping.getName();
            int dot = name.lastIndexOf('.');
            return MenuStyle.prettify(dot >= 0 ? name.substring(dot + 1) : name);
        }
        return raw;
    }

    public boolean capturing() {
        return capturing;
    }

    public void cancelCapture() {
        capturing = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        rowCard(graphics, textSize, hover, capturing || (isFocused() && active), shown);
        rowAccent(graphics, textSize, capturing ? 1f : rowEmphasis(hover), shown);

        float padX = MenuStyle.rowPadX(textSize);
        float innerX = getX() + padX;
        float innerW = Math.max(0f, getWidth() - padX * 2f);
        float centerY = getY() + getHeight() * 0.5f;

        String readout = capturing ? "Press a key..." : bindingText();
        float valueSize = MenuStyle.valueSize(textSize);
        float valueLimit = innerW * 0.45f;
        String shownReadout = MenuStyle.fit(readout, valueLimit, valueSize);
        float valueWidth = Math.min(Text.width(shownReadout, valueSize), valueLimit);
        float gap = textSize * 0.8f;
        float labelWidth = Math.max(0f, innerW - valueWidth - gap);

        String label = MenuStyle.fit(getMessage().getString(), labelWidth, textSize);
        float slide = 3f * hover;
        int idle = active ? MenuStyle.TEXT_DIM : MenuStyle.TEXT_MUTED;
        glowText(graphics, label, innerX + slide, Text.centeredBaseline(label, textSize, centerY),
                textSize, idle, 0xFFFFFFFF, active ? hover : 0f, shown * (active ? 1f : 0.65f));

        // Changed bindings read brighter so custom keys stand out; conflicts go red.
        int readoutIdle = !mapping.isDefault() ? 0xFFC9C9C9 : MenuStyle.VALUE_IDLE;
        int color = capturing ? 0xFFFFFFFF
                : conflict.getAsBoolean() ? 0xFFFF8080
                : Colors.lerp(readoutIdle, 0xFFFFFFFF, hover);
        float readoutX = getX() + getWidth() - padX - valueWidth;
        Text.draw(graphics, shownReadout, readoutX,
                Text.centeredBaseline(shownReadout, valueSize, centerY), valueSize,
                Colors.withAlpha(color, shown));
    }

    private String bindingText() {
        if (mapping.isUnbound()) return "Not bound";
        String raw = mapping.getTranslatedKeyMessage().getString();
        return raw.isEmpty() ? "Not bound" : raw;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!active) return;
        if (capturing) {
            // A mouse button completes the capture.
            mapping.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            afterChange();
        } else {
            capturing = true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing && active) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturing = false;
                return true;
            }
            mapping.setKey(InputConstants.getKey(event));
            afterChange();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // While capturing, clicks anywhere on this row bind the mouse button.
        // (Container routing only delivers clicks inside our bounds.)
        if (capturing && active && isHovered()) {
            onClick(event, doubleClick);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void afterChange() {
        capturing = false;
        Options options = Minecraft.getInstance().options;
        KeyMapping.resetMapping();
        options.save();
    }
}
