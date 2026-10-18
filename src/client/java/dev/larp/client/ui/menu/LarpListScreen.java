package dev.larp.client.ui.menu;

import dev.larp.client.render.Scissor;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LarpScreen} whose rows live in a smooth-scrolling viewport between
 * the header and the Back row. Rows are positioned every frame from the
 * eased scroll offset, clipped to the viewport, and clicks outside the
 * viewport never reach them.
 *
 * <p>Each row keeps its own height (one-line rows use the screen's
 * {@code rowHeight}, cards use {@link MenuStyle#autoHeight}), and the scroll
 * extent is the sum of those heights. That makes tall cards and short rows
 * coexist without overlap, and keeps the scrollbar honest.</p>
 */
public abstract class LarpListScreen extends LarpScreen {
    protected record Header(String text, int beforeRow) { }

    protected final ScrollPanel scroll = new ScrollPanel();
    protected final List<MenuWidget> rows = new ArrayList<>();
    protected final List<Header> headers = new ArrayList<>();
    /** Fixed action buttons pinned above the Back row (world/server screens). */
    protected final List<MenuWidget> footer = new ArrayList<>();
    private final List<Float> headerTops = new ArrayList<>();
    protected MenuButton backRow;

    protected LarpListScreen(String title, String subtitle, Screen parent) {
        super(title, subtitle, parent);
    }

    /** Adds a scrollable row widget. */
    protected <T extends MenuWidget> T row(T widget) {
        rows.add(content(widget));
        return widget;
    }

    /** Adds a fixed footer action widget (positioned by {@link #layoutFooter()}). */
    protected <T extends MenuWidget> T footer(T widget) {
        footer.add(content(widget));
        return widget;
    }

    /** Height of one row: an explicit height wins, otherwise the screen default. */
    protected float widgetHeight(MenuWidget widget) {
        return widget.getHeight() > 1 ? widget.getHeight() : rowHeight;
    }

    protected float rowStride() {
        return rowHeight + rowGap();
    }

    protected float headerHeight() {
        return 30f * ui;
    }

    protected float viewportTop() {
        return contentTop();
    }

    protected float viewportBottom() {
        return footer.isEmpty() ? backRowY() - 10f * ui : footerY() - 8f * ui;
    }

    protected float contentHeight() {
        float height = headers.size() * headerHeight();
        for (MenuWidget widget : rows) {
            height += widgetHeight(widget) + rowGap();
        }
        return height;
    }

    protected float maxScroll() {
        return Math.max(0f, contentHeight() - (viewportBottom() - viewportTop()));
    }

    @Override
    protected void buildContent() {
        rows.clear();
        headers.clear();
        footer.clear();
        headerTops.clear();
        buildRows();
        backRow = content(backButton(stagger(rows.size() + footer.size())));
    }

    /** Adds rows (and optional headers); called from {@link #buildContent()}. */
    protected abstract void buildRows();

    /** Positions rows and headers for the given eased scroll offset. */
    protected void layoutRows(float offset) {
        headerTops.clear();
        float y = viewportTop() - offset;
        List<Header> sorted = new ArrayList<>(headers);
        sorted.sort((a, b) -> Integer.compare(a.beforeRow(), b.beforeRow()));
        int headerCursor = 0;
        for (int i = 0; i <= rows.size(); i++) {
            while (headerCursor < sorted.size() && sorted.get(headerCursor).beforeRow() == i) {
                headerTops.add(y);
                y += headerHeight();
                headerCursor++;
            }
            if (i >= rows.size()) break;
            MenuWidget widget = rows.get(i);
            float height = widgetHeight(widget);
            // 26.2's setRectangle is (width, height, x, y).
            widget.setRectangle(Math.round(contentWidth), Math.max(1, Math.round(height)),
                    Math.round(margin), Math.round(y));
            y += height + rowGap();
        }
    }

    /** Gap between footer buttons. */
    protected float footerGap() {
        return 8f * ui;
    }

    /** Columns in the fixed action strip. */
    protected int footerColumns() {
        // Short windows put every action on one line so the list keeps room.
        if (compact() && !footer.isEmpty()) return footer.size();
        return 3;
    }

    protected int footerRows() {
        if (footer.isEmpty()) return 0;
        return Math.max(1, (footer.size() + footerColumns() - 1) / footerColumns());
    }

    protected float footerHeight() {
        int rows = footerRows();
        return rows == 0 ? 0f : rows * rowHeight + (rows - 1) * footerGap();
    }

    /** Y of the fixed footer action strip, just above the Back row. */
    protected float footerY() {
        return backRowY() - 10f * ui - footerHeight();
    }

    /** Lays the footer strip out in as many rows as its labels need. */
    protected void layoutFooter() {
        if (footer.isEmpty()) return;
        int columns = Math.max(1, footerColumns());
        int count = footer.size();
        for (int i = 0; i < count; i++) {
            int row = i / columns;
            int column = i % columns;
            int inRow = Math.min(columns, count - row * columns);
            float width = (contentWidth - (inRow - 1) * footerGap()) / inRow;
            footer.get(i).setRectangle(Math.round(width), Math.round(rowHeight),
                    Math.round(margin + column * (width + footerGap())),
                    Math.round(footerY() + row * (rowHeight + footerGap())));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hasModal()) return super.mouseClicked(event, doubleClick);
        double y = event.y();
        if (y < viewportTop() || y >= viewportBottom()) {
            // Only footer actions and the Back row live outside the viewport.
            if (backRow != null && backRow.isHovered()) {
                return super.mouseClicked(event, doubleClick);
            }
            for (MenuWidget widget : footer) {
                if (widget.isHovered()) return super.mouseClicked(event, doubleClick);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!hasModal() && y >= viewportTop() && y < viewportBottom()) {
            scroll.scrollBy((float) (-scrollY * 30.0), maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** Draws rows, headers, scrollbar, footer and modal. Call from subclasses. */
    protected void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, float shown) {
        scroll.clamp(maxScroll());
        layoutRows(scroll.update());
        ScreenRectangle viewport = new ScreenRectangle(Math.round(margin) - 8,
                Math.round(viewportTop()) - 4, Math.round(contentWidth) + 16,
                Math.max(1, Math.round(viewportBottom() - viewportTop()) + 8));
        Scissor.clip(viewport, () -> {
            drawHeaders(graphics, shown);
            for (MenuWidget widget : rows) {
                widget.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        });
        scroll.drawScrollbar(graphics, margin + contentWidth + 8f * ui, viewportTop(),
                viewportBottom() - viewportTop(), contentHeight(), shown);
        layoutFooter();
        if (backRow != null) backRow.extractRenderState(graphics, mouseX, mouseY, delta);
        for (MenuWidget widget : footer) {
            widget.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        drawFooter(graphics, shown);
        drawModal(graphics, mouseX, mouseY, delta);
    }

    /** Kept for subclasses that need the viewport edge with a footer present. */
    protected float viewportBottomWithFooter() {
        return footerY() - 8f * ui;
    }

    private void drawHeaders(GuiGraphicsExtractor graphics, float shown) {
        float size = 10.5f * ui;
        for (int i = 0; i < headers.size() && i < headerTops.size(); i++) {
            float y = headerTops.get(i);
            Text.draw(graphics, MenuStyle.fit(headers.get(i).text(), contentWidth, size),
                    margin, y + headerHeight() * 0.62f, size,
                    Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
        }
    }
}
