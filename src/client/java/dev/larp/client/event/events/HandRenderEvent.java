package dev.larp.client.event.events;

import dev.larp.client.event.Event;
import dev.larp.client.render.entity.EntityFill;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public final class HandRenderEvent implements Event {
    @Getter
    private EntityFill fill;

    public void setFill(EntityFill fill) {
        this.fill = fill;
    }
}
