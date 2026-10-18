package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A modal dialog with a single text field (used for renaming worlds).
 * Enter saves, Escape cancels. The card is clamped to the window so it
 * stays fully visible at every GUI scale.
 */
public class MenuPrompt extends MenuWidget {
    private final FieldState field;
    private final Consumer<String> onSave;
    private final float ui;

    private float cardX, cardY, cardW, cardH;
    private float saveX, saveY, saveW, saveH;
    private float cancelX, cancelY, cancelW, cancelH;
    private float boxX, boxY, boxW, boxH;
    private boolean hoverSave;
    private boolean hoverCancel;
    private Runnable closer = () -> { };

    public MenuPrompt(String title, String initial, int maxLength, float ui, Consumer<String> onSave) {
        super(0, 0, 1, 1, Component.literal(title), 0);
        this.field = new FieldState(initial, maxLength, MenuField.freeText());
        this.field.selectEverything();
        this.ui = ui;
        this.onSave = onSave;
    }

    public void onCloseRequest(Runnable closer) {
        this.closer = closer;
    }

    private void layout() {
        cardW = Math.min(440f * ui, Math.max(220f, getWidth() - 32f));
        cardH = Math.min(200f * ui, Math.max(120f, getHeight() - 32f));
        cardX = (getWidth() - cardW) / 2f;
        cardY = (getHeight() - cardH) / 2f;

        boxX = cardX + 28f * ui;
        boxY = cardY + 62f * ui;
        boxW = Math.max(40f, cardW - 56f * ui);
        boxH = Math.max(24f, 34f * ui);

        float buttonHeight = Math.max(24f, 30f * ui);
        float buttonY = cardY + cardH - 16f * ui - buttonHeight;
        float half = (boxW - 10f * ui) / 2f;
        cancelX = boxX;
        cancelY = buttonY;
        cancelW = half;
        cancelH = buttonHeight;
        saveX = boxX + half + 10f * ui;
        saveY = buttonY;
        saveW = half;
        saveH = buttonHeight;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        hoverSave = inside(mouseX, mouseY, saveX, saveY, saveW, saveH);
        hoverCancel = inside(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH);

        graphics.fill(0, 0, getWidth(), getHeight(), 0xAA000000);
        Draw.bordered(graphics, cardX, cardY, cardW, Math.round(cardH), 10f * ui, 0xFF161616, 0xFF2E2E2E);

        float titleSize = 15f * ui;
        String title = MenuStyle.fit(getMessage().getString(), cardW - 56f * ui, titleSize);
        Text.draw(graphics, title, cardX + 28f * ui, cardY + 30f * ui, titleSize, MenuStyle.TEXT_IDLE);
        field.draw(graphics, boxX, boxY, boxW, boxH, 12.5f * ui, true, 1f, "");
        drawButton(graphics, cancelX, cancelY, cancelW, cancelH, "Cancel", hoverCancel, false);
        drawButton(graphics, saveX, saveY, saveW, saveH, "Save", hoverSave, false);
    }

    private static boolean inside(double x, double y, float rx, float ry, float rw, float rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private void drawButton(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                            String label, boolean hovered, boolean destructive) {
        float size = 12.5f * ui;
        int fill = hovered ? 0x22FFFFFF : 0x0BFFFFFF;
        Draw.rect(graphics, x, y, w, Math.max(1, Math.round(h)), Math.round(5f * ui),
                Colors.withAlpha(fill, 1f));
        String text = MenuStyle.fit(label, Math.max(1f, w - 24f * ui), size);
        float textX = x + (w - Text.width(text, size)) / 2f;
        int color = destructive
                ? (hovered ? MenuStyle.DANGER : 0xFFB07A7A)
                : (hovered ? 0xFFFFFFFF : MenuStyle.TEXT_DIM);
        Text.draw(graphics, text, textX, Text.centeredBaseline(text, size, y + h / 2f), size, color);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        layout();
        float x = (float) event.x();
        float y = (float) event.y();
        if (inside(x, y, saveX, saveY, saveW, saveH)) {
            save();
        } else if (inside(x, y, cancelX, cancelY, cancelW, cancelH)) {
            closer.run();
        }
        // Other clicks are swallowed so the world list behind stays inert.
    }

    private void save() {
        if (onSave != null) onSave.accept(field.value().trim());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (field.keyPressed(event)) return true;
        if (event.isSelection()) {
            save();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return field.charTyped(event);
    }
}
