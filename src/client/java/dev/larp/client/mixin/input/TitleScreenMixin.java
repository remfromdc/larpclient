package dev.larp.client.mixin.input;

import dev.larp.client.mixin.accessor.JoinMultiplayerScreenAccessor;
import dev.larp.client.mixin.accessor.OptionsScreenAccessor;
import dev.larp.client.mixin.accessor.SelectWorldScreenAccessor;
import dev.larp.client.ui.menu.LarpServerListScreen;
import dev.larp.client.ui.menu.LarpSettingsScreen;
import dev.larp.client.ui.menu.LarpWorldListScreen;
import dev.larp.client.ui.title.LarpTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla menus for their Larp counterparts.
 *
 * <p>In 26.2 screens are owned by {@link Gui}; {@code MinecraftClient.setScreen}
 * from older versions is now {@code Gui#setScreen}. Injecting at the tail (rather
 * than rewriting the argument) also covers the {@code setScreen(null)} path, where
 * {@code Gui} builds its own {@link TitleScreen} internally when no level is
 * loaded — for example when leaving a world or closing the ClickGUI.</p>
 *
 * <p>World, multiplayer and options screens keep their original parent through
 * accessor mixins, so Escape from pause-menu settings still returns to pause.</p>
 */
@Mixin(Gui.class)
public abstract class TitleScreenMixin {
    @Inject(method = "setScreen", at = @At("TAIL"), require = 1)
    private void larp$replaceMenus(CallbackInfo info) {
        Gui gui = (Gui) (Object) this;
        Screen current = gui.screen();
        if (current == null) return;
        // Exact class checks: never touch subclasses another mod may have installed.
        if (current.getClass() == TitleScreen.class) {
            gui.setScreen(new LarpTitleScreen());
        } else if (current.getClass() == SelectWorldScreen.class) {
            Screen parent = ((SelectWorldScreenAccessor) current).larp$getLastScreen();
            gui.setScreen(new LarpWorldListScreen(menuParent(parent)));
        } else if (current.getClass() == JoinMultiplayerScreen.class) {
            Screen parent = ((JoinMultiplayerScreenAccessor) current).larp$getLastScreen();
            gui.setScreen(new LarpServerListScreen(menuParent(parent)));
        } else if (current.getClass() == OptionsScreen.class) {
            Screen parent = ((OptionsScreenAccessor) current).larp$getLastScreen();
            gui.setScreen(new LarpSettingsScreen(menuParent(parent)));
        }
    }

    private static Screen menuParent(Screen parent) {
        // Vanilla titles never survive the swap above, but another mod could
        // hand us one directly; normalize it to ours.
        if (parent == null || parent.getClass() == TitleScreen.class) {
            return new LarpTitleScreen();
        }
        return parent;
    }
}
