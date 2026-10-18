package dev.larp.client.mixin.render;

import dev.larp.client.LarpClient;
import dev.larp.client.event.events.HandRenderEvent;
import dev.larp.client.render.entity.EntityFills;
import dev.larp.client.render.entity.EntityOutlines;
import dev.larp.client.render.entity.HandBounds;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void larp$beginHand(CallbackInfo info) {
        EntityFills.beginHand(LarpClient.EVENTS.post(new HandRenderEvent()).fill());
        if (EntityOutlines.hand() != null) HandBounds.begin();
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"))
    private void larp$endHand(CallbackInfo info) {
        if (EntityOutlines.hand() != null) HandBounds.end(EntityOutlines.hand());
        EntityFills.endHand();
    }
}
