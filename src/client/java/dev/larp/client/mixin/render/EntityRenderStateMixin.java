package dev.larp.client.mixin.render;

import dev.larp.client.render.entity.EntityFill;
import dev.larp.client.render.entity.EntityOutline;
import dev.larp.client.render.entity.Filled;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements Filled {
    @Unique
    private EntityFill larp$fill;
    @Unique
    private EntityOutline larp$outline;

    @Override
    public EntityFill larp$fill() {
        return larp$fill;
    }

    @Override
    public void larp$setFill(EntityFill fill) {
        larp$fill = fill;
    }

    @Override
    public EntityOutline larp$outline() {
        return larp$outline;
    }

    @Override
    public void larp$setOutline(EntityOutline outline) {
        larp$outline = outline;
    }
}
