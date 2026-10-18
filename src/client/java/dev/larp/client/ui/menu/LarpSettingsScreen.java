package dev.larp.client.ui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Top-level "Minecraft Settings" page. Each category is a card with its name,
 * a short description underneath and a chevron on the right, so every entry
 * has a large, unambiguous click target and the two text lines can never
 * collide with each other.
 */
public class LarpSettingsScreen extends LarpListScreen {
    private static final String[][] CATEGORIES = {
            {"Video", "Graphics, render distance, framerate"},
            {"Controls", "Mouse, key binds, toggles"},
            {"Sound", "Volumes and voice"},
            {"Chat", "Visibility, scale, filters"},
            {"Language", "Game language"},
            {"Resource Packs", "Active packs and ordering"},
            {"Accessibility", "Comfort and readability"},
            {"Skin", "Model parts and main hand"},
            {"Online", "Realms, presence, server listing"},
    };

    public LarpSettingsScreen(Screen parent) {
        super("Minecraft Settings", "Tune the game. Everything saves automatically.", parent);
    }

    @Override
    protected void buildRows() {
        for (int i = 0; i < CATEGORIES.length; i++) {
            String name = CATEGORIES[i][0];
            MenuButton row = new MenuButton(0, 0, 1, 1, name, textSize, stagger(i),
                    () -> open(name));
            row.setDescription(CATEGORIES[i][1]);
            row.setValue(">");
            row.setHeight(Math.round(cardHeight));
            row(row);
        }
    }

    private void open(String category) {
        LarpScreen next = switch (category) {
            case "Video" -> new LarpOptionScreen("Video", "What the game renders and how fast.", this,
                    LarpOptionScreen.video(minecraft));
            case "Controls" -> new LarpControlsScreen(this);
            case "Sound" -> new LarpOptionScreen("Sound", "Every volume slider in one place.", this,
                    LarpOptionScreen.sound(minecraft));
            case "Chat" -> new LarpOptionScreen("Chat", "How chat looks and behaves.", this,
                    LarpOptionScreen.chat(minecraft));
            case "Language" -> new LarpLanguageScreen(this);
            case "Resource Packs" -> new LarpPackScreen(this);
            case "Accessibility" -> new LarpOptionScreen("Accessibility", "Comfort and readability.", this,
                    LarpOptionScreen.accessibility(minecraft));
            case "Skin" -> new LarpSkinScreen(this);
            case "Online" -> new LarpOptionScreen("Online", "Multiplayer comfort settings.", this,
                    LarpOptionScreen.online(minecraft));
            default -> null;
        };
        if (next != null) minecraft.gui.setScreen(next);
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
