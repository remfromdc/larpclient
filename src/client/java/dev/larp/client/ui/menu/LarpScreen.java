package dev.larp.client.ui.menu;

import dev.larp.client.render.Text;
import dev.larp.client.ui.title.LarpTitleScreen;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every Larp menu screen.
 *
 * <p>Layout mirrors the title screen: a small star + title + subtitle in the
 * top-left corner, left-aligned content rows, a Back row at the bottom-left
 * and the {@code larpclient.shop} wordmark at the bottom-right. Escape (or
 * Back) returns to the parent screen; with no parent it falls back to a
 * fresh {@link LarpTitleScreen} instead of the vanilla menu.</p>
 */
public abstract class LarpScreen extends Screen {
    protected final Screen parent;
    protected final String subtitle;

    protected float ui = 1f;
    protected float margin = 40f;
    protected float contentWidth = 560f;
    /** Height of a one-line row, derived from the row font. */
    protected float rowHeight = 38f;
    /** Height of a row with a title and a description line. */
    protected float cardHeight = 56f;
    /** Height of a stacked label + text-field row. */
    protected float fieldHeight = 52f;
    protected float textSize = 15f;

    protected final long openedAt = Util.getMillis();

    /** Currently open modal dialog, if any. Content rows go inert while set. */
    private MenuWidget modal;
    private final List<GuiEventListener> content = new ArrayList<>();

    protected LarpScreen(String title, String subtitle, Screen parent) {
        super(Component.literal(title));
        this.subtitle = subtitle == null ? "" : subtitle;
        this.parent = parent;
    }

    protected String screenTitle() {
        return getTitle().getString();
    }

    protected float reveal() {
        long elapsed = Util.getMillis() - openedAt;
        if (elapsed <= 0L) return 0f;
        return MenuStyle.easeOutCubic(elapsed / (float) MenuStyle.REVEAL_MILLIS);
    }

    @Override
    protected void init() {
        ui = MenuStyle.uiFor(width, height);
        margin = 40f * ui;
        contentWidth = Math.min(560f * ui, width - margin * 2f);
        textSize = 15f * ui;
        // Derive every row height from the font instead of a fixed pixel
        // constant, so larger text can never overflow its row.
        rowHeight = MenuStyle.autoHeight(textSize, false);
        cardHeight = MenuStyle.autoHeight(textSize, true);
        fieldHeight = textSize * 3.4f;
        content.clear();
        buildContent();
        if (modal != null) {
            // 26.2's setRectangle is (width, height, x, y).
            modal.setRectangle(width, height, 0, 0);
        }
    }

    /** Adds a content widget (tracked so modals can suspend it). */
    protected <T extends MenuWidget> T content(T widget) {
        content.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    /** Builds rows; called from {@link #init()} after metrics are set. */
    protected abstract void buildContent();

    /**
     * True on short windows, where the generous header and the two-row action
     * strip would leave no room for the list itself. Compact mode keeps only
     * the title and pins the Back row directly under the content.
     */
    protected boolean compact() {
        return height < 300f;
    }

    /** Top Y of the content area, below the header. */
    protected float contentTop() {
        if (compact()) return 42f * ui;
        return Math.max(120f * ui, height * 0.16f) + 30f * ui;
    }

    /** Gap between list rows, matching the card layout. */
    protected float rowGap() {
        return MenuStyle.rowGap(textSize);
    }

    /** Y of the Back row, pinned above the footer. */
    protected float backRowY() {
        return compact() ? height - rowHeight - 10f * ui : height - 64f * ui;
    }

    protected int stagger(int index) {
        return 60 + index * MenuStyle.STAGGER_MILLIS;
    }

    protected MenuButton backButton(int revealDelay) {
        return new MenuButton(Math.round(margin), Math.round(backRowY()),
                Math.round(contentWidth), Math.round(rowHeight),
                "Back", textSize, revealDelay, this::goBack);
    }

    protected void goBack() {
        onClose();
    }

    /** Opens a confirmation wired to close itself on either choice. */
    protected void confirm(String title, String message, String confirmLabel, boolean danger,
                           Runnable onConfirm) {
        MenuConfirm dialog = new MenuConfirm(title, message, confirmLabel, danger, ui,
                () -> {
                    closeModal();
                    if (onConfirm != null) onConfirm.run();
                });
        dialog.onCloseRequest(this::closeModal);
        openModal(dialog);
    }

    /** Opens a text prompt wired to close itself on save or cancel. */
    protected void prompt(String title, String initial, int maxLength,
                          java.util.function.Consumer<String> onSave) {
        MenuPrompt dialog = new MenuPrompt(title, initial, maxLength, ui, value -> {
            closeModal();
            if (onSave != null) onSave.accept(value);
        });
        dialog.onCloseRequest(this::closeModal);
        openModal(dialog);
    }

    protected void openModal(MenuWidget dialog) {
        this.modal = dialog;
        dialog.setRectangle(width, height, 0, 0);
        addRenderableWidget(dialog);
        setContentActive(false);
    }

    protected void closeModal() {
        if (modal != null) {
            removeWidget(modal);
            modal = null;
        }
        setContentActive(true);
    }

    protected boolean hasModal() {
        return modal != null;
    }

    /** Draws the open modal on top of everything else. */
    protected void drawModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (modal != null) modal.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void setContentActive(boolean enabled) {
        for (GuiEventListener child : content) {
            if (child instanceof MenuWidget widget) widget.active = enabled;
        }
    }

    /** Persisted when the screen closes (options save, server list save...). */
    protected void saveState() {
    }

    @Override
    public void onClose() {
        if (modal != null) {
            closeModal();
            // Re-show this screen: the close was meant for the dialog.
            minecraft.gui.setScreen(this);
            return;
        }
        saveState();
        Screen next = parent != null ? parent : new LarpTitleScreen();
        minecraft.gui.setScreen(next);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeModal();
                return true;
            }
            return modal.keyPressed(event) || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        closeModal();
        rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        MenuStyle.background(graphics, width, height, ui);
    }

    /** Draws the star + title + subtitle header. Call from subclasses. */
    protected void drawHeader(GuiGraphicsExtractor graphics, float shown) {
        float headerWidth = Math.max(0f, width - margin * 2f);
        if (compact()) {
            // Short window: star and title on one line, no subtitle.
            float titleSize = 16f * ui;
            float starRadius = 7f * ui;
            float starX = margin + starRadius;
            float starY = 22f * ui;
            MenuStyle.star(graphics, starX, starY, starRadius, 1.2f * ui,
                    Colors.withAlpha(0xFFFFFFFF, shown * 0.9f));
            float textX = margin + starRadius * 2f + 8f * ui;
            Text.draw(graphics, MenuStyle.fit(screenTitle(), headerWidth - (textX - margin), titleSize),
                    textX, starY + titleSize * 0.36f, titleSize,
                    Colors.withAlpha(MenuStyle.TEXT_IDLE, shown));
            return;
        }
        float logoRadius = 13f * ui;
        float logoX = margin + logoRadius;
        float logoY = Math.min(64f * ui, height * 0.09f) + logoRadius;
        MenuStyle.star(graphics, logoX, logoY, logoRadius, 1.4f * ui,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.9f));
        float titleSize = 21f * ui;
        float titleBaseline = logoY + logoRadius + 26f * ui;
        Text.draw(graphics, MenuStyle.fit(screenTitle(), headerWidth, titleSize), margin, titleBaseline,
                titleSize, Colors.withAlpha(MenuStyle.TEXT_IDLE, shown));
        if (!subtitle.isEmpty()) {
            float subSize = 10.5f * ui;
            Text.draw(graphics, MenuStyle.fit(subtitle, headerWidth, subSize),
                    margin, titleBaseline + 20f * ui, subSize,
                    Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
        }
    }

    /** Draws the footer wordmark. Call from subclasses. */
    protected void drawFooter(GuiGraphicsExtractor graphics, float shown) {
        float size = 9.5f * ui;
        String brand = "larpclient.shop";
        Text.draw(graphics, brand, width - margin - Text.width(brand, size),
                height - 22f * ui, size, Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
    }
}
