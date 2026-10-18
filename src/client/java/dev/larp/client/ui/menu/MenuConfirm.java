package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A full-screen modal confirmation: dimmed backdrop, centered card, message
 * lines and confirm/cancel buttons. Blocks all clicks outside its buttons.
 *
 * <p>The card is clamped to the window and the message is wrapped against
 * the card's actual inner width on every frame, so the text never spills
 * outside the panel at small sizes or unusual GUI scales.</p>
 */
public class MenuConfirm extends MenuWidget {
    private final Runnable onConfirm;
    private final String confirmLabel;
    private final boolean danger;
    private final String message;
    private final float ui;

    private List<String> lines = List.of();
    private float cardX, cardY, cardW, cardH;
    private float confirmX, confirmY, confirmW, confirmH;
    private float cancelX, cancelY, cancelW, cancelH;
    private boolean hoverConfirm;
    private boolean hoverCancel;

    public MenuConfirm(String title, String message, String confirmLabel, boolean danger,
                       float ui, Runnable onConfirm) {
        super(0, 0, 1, 1, Component.literal(title), 0);
        this.onConfirm = onConfirm;
        this.confirmLabel = confirmLabel;
        this.danger = danger;
        this.ui = ui;
        this.message = message == null ? "" : message;
    }

    private void layout() {
        float lineSize = 11.5f * ui;
        cardW = Math.min(440f * ui, Math.max(220f, getWidth() - 32f));
        cardH = 1f;
        lines = Text.wrap(message, Math.max(40f, cardW - 56f * ui), lineSize, 8);
        cardH = (104f + lines.size() * 19f) * ui;
        float maxHeight = Math.max(80f, getHeight() - 24f);
        cardH = Math.min(cardH, maxHeight);
        cardX = (getWidth() - cardW) / 2f;
        cardY = (getHeight() - cardH) / 2f;

        float buttonHeight = Math.max(24f, 30f * ui);
        float buttonY = cardY + cardH - 16f * ui - buttonHeight;
        float half = (cardW - 56f * ui - 10f * ui) / 2f;
        cancelX = cardX + 28f * ui;
        cancelY = buttonY;
        cancelW = half;
        cancelH = buttonHeight;
        confirmX = cancelX + half + 10f * ui;
        confirmY = buttonY;
        confirmW = half;
        confirmH = buttonHeight;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        layout();
        hoverConfirm = inside(mouseX, mouseY, confirmX, confirmY, confirmW, confirmH);
        hoverCancel = inside(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH);

        graphics.fill(0, 0, getWidth(), getHeight(), 0xAA000000);
        Draw.bordered(graphics, cardX, cardY, cardW, Math.round(cardH), 10f * ui, 0xFF161616, 0xFF2E2E2E);

        float titleSize = 15f * ui;
        String title = MenuStyle.fit(getMessage().getString(), cardW - 56f * ui, titleSize);
        Text.draw(graphics, title, cardX + 28f * ui, cardY + 30f * ui, titleSize, MenuStyle.TEXT_IDLE);
        float lineSize = 11.5f * ui;
        for (int i = 0; i < lines.size(); i++) {
            Text.draw(graphics, lines.get(i), cardX + 28f * ui,
                    cardY + (56f + i * 19f) * ui, lineSize, MenuStyle.TEXT_DIM);
        }
        drawButton(graphics, cancelX, cancelY, cancelW, cancelH, "Cancel", hoverCancel, false);
        drawButton(graphics, confirmX, confirmY, confirmW, confirmH, confirmLabel, hoverConfirm, danger);
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

    private static boolean inside(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        layout();
        float x = (float) event.x();
        float y = (float) event.y();
        if (inside(x, y, confirmX, confirmY, confirmW, confirmH)) {
            if (onConfirm != null) onConfirm.run();
        }
        // Clicks anywhere else are swallowed; cancellation goes through
        // the Cancel row, Escape, or the owning screen.
        else if (inside(x, y, cancelX, cancelY, cancelW, cancelH)) {
            cancel();
        }
    }

    /** Ask the owning screen to drop this dialog. Set by the screen. */
    private Runnable closer = () -> { };

    public void onCloseRequest(Runnable closer) {
        this.closer = closer;
    }

    private void cancel() {
        closer.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isSelection()) {
            if (onConfirm != null) onConfirm.run();
            return true;
        }
        return true;
    }
}
