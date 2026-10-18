package dev.larp.client.mixin.accessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the parent screen so the menu swap can preserve back-navigation. */
@Mixin(JoinMultiplayerScreen.class)
public interface JoinMultiplayerScreenAccessor {
    @Accessor("lastScreen")
    Screen larp$getLastScreen();
}
