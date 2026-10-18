package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public record HudRenderEvent(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) implements Event {
}
