package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import net.minecraft.client.Minecraft;

public record TickEvent(Minecraft client) implements Event {
}
