package dev.larp.client.mixin.accessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the parent screen so the menu swap can preserve back-navigation. */
@Mixin(OptionsScreen.class)
public interface OptionsScreenAccessor {
    @Accessor("lastScreen")
    Screen larp$getLastScreen();
}
