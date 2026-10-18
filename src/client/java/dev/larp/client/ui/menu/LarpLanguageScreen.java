package dev.larp.client.ui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;

import java.util.Map;

/**
 * Language list. Selecting applies immediately and reloads resources;
 * the active language is marked on the right.
 */
public class LarpLanguageScreen extends LarpListScreen {
    public LarpLanguageScreen(Screen parent) {
        super("Language", "Applies immediately.", parent);
    }

    @Override
    protected void buildRows() {
        LanguageManager manager = minecraft.getLanguageManager();
        String selected = manager.getSelected();
        int index = 0;
        for (Map.Entry<String, LanguageInfo> entry : manager.getLanguages().entrySet()) {
            String code = entry.getKey();
            LanguageInfo info = entry.getValue();
            MenuButton row = new MenuButton(0, 0, 1, 1, info.toComponent().getString(),
                    textSize, stagger(index++), () -> select(code));
            if (code.equals(selected)) row.setValue("Active");
            row(row);
        }
    }

    private void select(String code) {
        LanguageManager manager = minecraft.getLanguageManager();
        if (code.equals(manager.getSelected())) return;
        manager.setSelected(code);
        minecraft.reloadResourcePacks();
        rebuildWidgets();
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
