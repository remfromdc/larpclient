package dev.larp.client.mixin.accessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the parent screen so the menu swap can preserve back-navigation. */
@Mixin(SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {
    @Accessor("lastScreen")
    Screen larp$getLastScreen();
}
