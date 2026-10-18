package dev.larp.client.event.events;

import dev.larp.client.render.screen.Projection;
import dev.larp.client.render.screen.ScreenBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

public record ScreenRenderEvent(ClientLevel level, Camera camera, DeltaTracker deltaTracker, Projection projection,
                                ScreenBuffer buffer) implements FrameEvent {
}
