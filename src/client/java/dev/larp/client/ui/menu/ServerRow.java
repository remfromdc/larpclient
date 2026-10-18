package dev.larp.client.ui.menu;

import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * One server entry: icon, name, MOTD/status line and a ping readout.
 * Single click selects, double click (or Enter while focused) joins.
 * Text columns are measured against the row width, so the MOTD, the name
 * and the ping readout stay in their own lanes at every window size.
 */
public class ServerRow extends MenuWidget {
    private final ServerData data;
    private final IconCache icons;
    private final String iconKey;
    private final boolean hideAddress;
    private final Runnable onSelect;
    private final Runnable onActivate;
    private final float textSize;
    private boolean selected;

    public ServerRow(ServerData data, IconCache icons, float textSize, int revealDelayMillis,
                     boolean hideAddress, Runnable onSelect, Runnable onActivate) {
        super(0, 0, 1, 1, Component.literal(data.name), revealDelayMillis);
        this.data = data;
        this.icons = icons;
        this.iconKey = "s:" + data.ip + "#" + java.util.Arrays.hashCode(
                data.getIconBytes() == null ? new byte[0] : data.getIconBytes());
        this.hideAddress = hideAddress;
        this.textSize = textSize;
        this.onSelect = onSelect;
        this.onActivate = onActivate;
    }

    public ServerData data() {
        return data;
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
        IconCache.Entry entry = shown > 0.5f ? icons.iconFor(iconKey, data.getIconBytes()) : null;
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
        String right = rightText();
        float rightWidth = right.isEmpty() ? 0f
                : Math.min(Text.width(right, statusSize), getWidth() * 0.3f);
        float rightEdge = getX() + getWidth() - padX;
        float textWidth = Math.max(0f, rightEdge - textX - (rightWidth > 0f ? rightWidth + gap : 0f));

        float nameCenter = getY() + padY + MenuStyle.titleLine(textSize) * 0.5f;
        String name = MenuStyle.fit(data.name == null ? "" : data.name, textWidth, textSize);
        int nameColor = selected ? 0xFFFFFFFF : Colors.lerp(MenuStyle.TEXT_DIM, 0xFFFFFFFF, hover);
        Text.draw(graphics, name, textX, Text.centeredBaseline(name, textSize, nameCenter),
                textSize, Colors.withAlpha(nameColor, shown));

        float infoCenter = getY() + getHeight() - padY - infoSize * 0.62f;
        String second = MenuStyle.fit(secondLine(), textWidth, infoSize);
        Text.draw(graphics, second, textX, Text.centeredBaseline(second, infoSize, infoCenter),
                infoSize, Colors.withAlpha(secondColor(), shown));

        if (!right.isEmpty()) {
            String shownRight = MenuStyle.fit(right, rightWidth, statusSize);
            float rightX = rightEdge - Math.min(Text.width(shownRight, statusSize), rightWidth);
            Text.draw(graphics, shownRight, rightX,
                    Text.centeredBaseline(shownRight, statusSize, nameCenter), statusSize,
                    Colors.withAlpha(rightColor(), shown));
        }
    }

    private String secondLine() {
        if (data.state() == ServerData.State.SUCCESSFUL && data.motd != null) {
            String motd = firstLine(data.motd.getString());
            if (!motd.isEmpty()) return motd;
        }
        if (data.status != null && !data.status.getString().isEmpty()) {
            return firstLine(data.status.getString());
        }
        return hideAddress ? "" : data.ip;
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private int secondColor() {
        return switch (data.state()) {
            case UNREACHABLE, INCOMPATIBLE -> MenuStyle.DANGER;
            default -> MenuStyle.TEXT_MUTED;
        };
    }

    private String rightText() {
        return switch (data.state()) {
            case INITIAL -> "...";
            case PINGING -> "Pinging...";
            case UNREACHABLE -> "";
            case INCOMPATIBLE -> "Incompatible";
            case SUCCESSFUL -> data.ping + "ms"
                    + (data.players != null ? "  -  " + data.players.online() + "/" + data.players.max() : "");
        };
    }

    private int rightColor() {
        return switch (data.state()) {
            case INCOMPATIBLE -> MenuStyle.DANGER;
            case SUCCESSFUL -> data.ping > 300 ? 0xFFE8C96A : MenuStyle.TEXT_DIM;
            default -> MenuStyle.TEXT_MUTED;
        };
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
