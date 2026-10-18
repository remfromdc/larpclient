package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One saved world: icon, name, details line and status readout.
 * Single click selects, double click (or Enter while focused) plays.
 * The row paints its own card and measures a status column before fitting
 * the name, so long world names never run into the status text.
 */
public class WorldRow extends MenuWidget {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final LevelSummary summary;
    private final IconCache icons;
    private final String iconKey;
    private final Runnable onSelect;
    private final Runnable onActivate;
    private final float textSize;
    private boolean selected;

    public WorldRow(LevelSummary summary, IconCache icons, String iconKey, float textSize,
                    int revealDelayMillis, Runnable onSelect, Runnable onActivate) {
        super(0, 0, 1, 1, Component.literal(summary.getLevelName()), revealDelayMillis);
        this.summary = summary;
        this.icons = icons;
        this.iconKey = iconKey;
        this.textSize = textSize;
        this.onSelect = onSelect;
        this.onActivate = onActivate;
    }

    public LevelSummary summary() {
        return summary;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        float hover = trackHover();
        rowCard(graphics, textSize, hover, selected, shown);
        rowAccent(graphics, textSize, Math.max(hover, selected ? 1f : 0f), shown);

        float padX = MenuStyle.rowPadX(textSize);
        float padY = MenuStyle.rowPadY(textSize);
        float iconSize = Math.max(16f, getHeight() - padY * 2f);
        int iconX = Math.round(getX() + padX);
        int iconY = Math.round(getY() + (getHeight() - iconSize) / 2f);
        IconCache.Entry entry = shown > 0.5f ? icons.iconForFile(iconKey, summary.getIcon()) : null;
        if (entry != null) {
            icons.draw(graphics, entry, iconX, iconY, Math.round(iconSize));
        } else {
            Draw.rect(graphics, iconX, iconY, Math.round(iconSize), Math.round(iconSize),
                    Math.round(iconSize * 0.2f), Colors.withAlpha(MenuStyle.FIELD_FILL, shown));
            MenuStyle.star(graphics, iconX + iconSize / 2f, iconY + iconSize / 2f,
                    iconSize * 0.26f, 1.2f, Colors.withAlpha(MenuStyle.TEXT_MUTED, shown * 0.7f));
        }

        float gap = textSize * 0.7f;
        float textX = iconX + iconSize + gap;
        float infoSize = MenuStyle.descSize(textSize);
        float statusSize = MenuStyle.descSize(textSize);
        String status = statusText();
        float statusWidth = status.isEmpty() ? 0f
                : Math.min(Text.width(status, statusSize), getWidth() * 0.28f);
        float rightEdge = getX() + getWidth() - padX;
        float textWidth = Math.max(0f, rightEdge - textX - (statusWidth > 0f ? statusWidth + gap : 0f));

        // Name on the upper line, details below it; both are fitted and can
        // never reach the status column on the right.
        float nameCenter = getY() + padY + MenuStyle.titleLine(textSize) * 0.5f;
        String name = MenuStyle.fit(summary.getLevelName(), textWidth, textSize);
        int nameColor = selected ? 0xFFFFFFFF : Colors.lerp(MenuStyle.TEXT_DIM, 0xFFFFFFFF, hover);
        Text.draw(graphics, name, textX, Text.centeredBaseline(name, textSize, nameCenter),
                textSize, Colors.withAlpha(nameColor, shown));

        float infoCenter = getY() + getHeight() - padY - infoSize * 0.62f;
        String info = MenuStyle.fit(infoLine(), textWidth, infoSize);
        Text.draw(graphics, info, textX, Text.centeredBaseline(info, infoSize, infoCenter),
                infoSize, Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));

        if (!status.isEmpty()) {
            String shownStatus = MenuStyle.fit(status, statusWidth, statusSize);
            float statusX = rightEdge - Math.min(Text.width(shownStatus, statusSize), statusWidth);
            Text.draw(graphics, shownStatus, statusX,
                    Text.centeredBaseline(shownStatus, statusSize, nameCenter), statusSize,
                    Colors.withAlpha(statusColor(), shown));
        }
    }

    private String infoLine() {
        GameType mode = summary.getGameMode();
        String name = summary.isHardcore() ? "Hardcore"
                : MenuStyle.prettify(mode == null ? "" : mode.getName());
        String version = summary.getWorldVersionName().getString();
        String date = summary.getLastPlayed() > 0L
                ? DATE.format(Instant.ofEpochMilli(summary.getLastPlayed())) : "never";
        return name + "  -  " + version + "  -  " + date;
    }

    private String statusText() {
        if (summary.isLocked()) return "Locked";
        if (!summary.primaryActionActive()) return summary.primaryActionMessage().getString();
        if (summary.isExperimental()) return "Experimental";
        return "";
    }

    private int statusColor() {
        if (summary.isLocked() || !summary.primaryActionActive()) return MenuStyle.DANGER;
        return 0xFFE8C96A;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (onSelect != null) onSelect.run();
        if (doubleClick && onActivate != null) onActivate.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && event.isSelection()) {
            if (onActivate != null) onActivate.run();
            return true;
        }
        return super.keyPressed(event);
    }
}
