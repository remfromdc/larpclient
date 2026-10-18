package dev.larp.client.ui.menu;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Skin customization: main hand plus every overlay part toggle.
 */
public class LarpSkinScreen extends LarpListScreen {
    public LarpSkinScreen(Screen parent) {
        super("Skin", "Which model layers render on your player.", parent);
    }

    @Override
    protected void buildRows() {
        Options options = minecraft.options;
        int index = 0;
        row(OptionRows.bind(options::mainHand, "Main Hand", textSize, stagger(index++)));
        for (PlayerModelPart part : PlayerModelPart.values()) {
            MenuToggle toggle = new MenuToggle(0, 0, 1, 1, partName(part), textSize,
                    stagger(index++),
                    () -> options.isModelPartEnabled(part),
                    next -> options.setModelPart(part, next));
            row(toggle);
        }
    }

    private static String partName(PlayerModelPart part) {
        return switch (part) {
            case CAPE -> "Cape";
            case JACKET -> "Jacket";
            case LEFT_SLEEVE -> "Left Sleeve";
            case RIGHT_SLEEVE -> "Right Sleeve";
            case LEFT_PANTS_LEG -> "Left Pants Leg";
            case RIGHT_PANTS_LEG -> "Right Pants Leg";
            case HAT -> "Hat";
        };
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
