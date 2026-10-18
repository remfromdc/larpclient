package dev.larp.client.ui.menu;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every key binding grouped by category. Click a row to capture the next
 * key or mouse button; Escape cancels. Conflicts tint red, and a trailing
 * row resets everything to defaults.
 */
public class LarpKeybindsScreen extends LarpListScreen {
    private final List<MenuKeybind> binds = new ArrayList<>();

    public LarpKeybindsScreen(Screen parent) {
        super("Key Binds", "Click a row, then press a key or mouse button.", parent);
    }

    @Override
    protected void buildRows() {
        binds.clear();
        KeyMapping[] mappings = minecraft.options.keyMappings;
        Map<String, List<KeyMapping>> grouped = new LinkedHashMap<>();
        for (KeyMapping mapping : mappings) {
            grouped.computeIfAbsent(mapping.getCategory().id().getPath(), key -> new ArrayList<>())
                    .add(mapping);
        }
        int index = 0;
        for (Map.Entry<String, List<KeyMapping>> group : grouped.entrySet()) {
            headers.add(new Header(MenuStyle.prettify(group.getKey()), index));
            for (KeyMapping mapping : group.getValue()) {
                MenuKeybind row = new MenuKeybind(0, 0, 1, 1, mapping, textSize,
                        stagger(index), () -> conflicted(mapping, mappings));
                binds.add(row(row));
                index++;
            }
        }
        MenuButton reset = new MenuButton(0, 0, 1, 1, "Reset All Keybinds", textSize,
                stagger(index), MenuButton.Variant.DANGER, this::resetAll);
        row(reset);
    }

    private static boolean conflicted(KeyMapping mapping, KeyMapping[] all) {
        if (mapping.isUnbound()) return false;
        String code = mapping.saveString();
        for (KeyMapping other : all) {
            if (other != mapping && !other.isUnbound() && other.saveString().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private void resetAll() {
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            mapping.setKey(mapping.getDefaultKey());
        }
        KeyMapping.resetMapping();
        minecraft.options.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Starting a new capture cancels any other row still listening.
        for (MenuKeybind row : binds) {
            if (row.capturing() && !row.isHovered()) row.cancelCapture();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        drawList(graphics, mouseX, mouseY, delta, shown);
    }

    @Override
    protected void saveState() {
        minecraft.options.save();
    }
}
