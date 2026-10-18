package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import net.minecraft.client.Minecraft;

public record MouseMoveEvent(Minecraft client, double x, double y) implements Event {
}
