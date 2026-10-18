package dev.larp.client.ui.menu;

import net.minecraft.client.gui.screens.Screen;

/**
 * Controls page: a Key Binds entry followed by the mouse-feel rows.
 */
public final class LarpControlsScreen extends LarpOptionScreen {
    public LarpControlsScreen(Screen parent) {
        super("Controls", "Remap everything, then tune the mouse.", parent,
                LarpOptionScreen.keyBindsAndMouse());
    }
}
