package dev.larp.client.mixin.input;

import dev.larp.client.LarpClient;
import dev.larp.client.event.events.MouseMoveEvent;
import dev.larp.client.event.events.TurnEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Unique
    private static final float TICKS_PER_SECOND = 20f;

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void larp$turned(double seconds, CallbackInfo info) {
        LarpClient.EVENTS.post(new TurnEvent(Minecraft.getInstance(), (float) seconds * TICKS_PER_SECOND));
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void larp$moved(CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() == null) return;
        MouseHandler handler = (MouseHandler) (Object) this;
        LarpClient.EVENTS.post(new MouseMoveEvent(client,
                handler.getScaledXPos(client.getWindow()), handler.getScaledYPos(client.getWindow())));
    }
}
