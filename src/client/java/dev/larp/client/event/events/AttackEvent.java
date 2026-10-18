package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record AttackEvent(Player player, Entity target) implements Event {
}
