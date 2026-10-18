package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import dev.larp.client.module.Module;

public record ModuleToggleEvent(Module module, boolean enabled) implements Event {
}
