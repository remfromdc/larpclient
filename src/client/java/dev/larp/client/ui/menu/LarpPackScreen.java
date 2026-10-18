package dev.larp.client.ui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resource pack list. Click a pack to toggle it; changes apply with a
 * resource reload. Incompatible packs are shown but left inert.
 */
public class LarpPackScreen extends LarpListScreen {
    public LarpPackScreen(Screen parent) {
        super("Resource Packs", "Click a pack to enable or disable it.", parent);
    }

    @Override
    protected void buildRows() {
        PackRepository repository = minecraft.getResourcePackRepository();
        Collection<String> selected = new ArrayList<>(repository.getSelectedIds());
        int index = 0;
        List<Pack> packs = new ArrayList<>(repository.getAvailablePacks());
        packs.sort((a, b) -> {
            boolean sa = selected.contains(a.getId());
            boolean sb = selected.contains(b.getId());
            if (sa != sb) return sa ? -1 : 1;
            return a.getTitle().getString().compareToIgnoreCase(b.getTitle().getString());
        });
        for (Pack pack : packs) {
            boolean on = selected.contains(pack.getId());
            boolean compatible = pack.getCompatibility().isCompatible();
            MenuButton row = new MenuButton(0, 0, 1, 1, pack.getTitle().getString(),
                    textSize, stagger(index++), () -> toggle(pack.getId()));
            row.setValue(!compatible ? "Incompatible" : on ? "On" : "Off");
            row.setEnabled(compatible);
            row(row);
        }
    }

    private void toggle(String id) {
        PackRepository repository = minecraft.getResourcePackRepository();
        List<String> selected = new ArrayList<>(repository.getSelectedIds());
        if (selected.contains(id)) selected.remove(id);
        else selected.add(id);
        repository.setSelected(selected);
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
