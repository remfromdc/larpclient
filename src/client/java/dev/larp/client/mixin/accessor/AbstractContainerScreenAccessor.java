package dev.larp.client.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot larp$getHoveredSlot();

    @Accessor("leftPos")
    int larp$getLeftPos();

    @Accessor("topPos")
    int larp$getTopPos();

    @Invoker("slotClicked")
    void larp$slotClicked(Slot slot, int index, int button, ContainerInput input);
}
