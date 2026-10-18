package dev.larp.client.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.larp.client.LarpClient;
import dev.larp.client.rotation.Rotation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow private float yRotLast;
    @Shadow private float xRotLast;

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float larp$sentYaw(float yaw) {
        return LarpClient.ROTATIONS.sentYaw(yaw);
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float larp$sentPitch(float pitch) {
        return LarpClient.ROTATIONS.sentPitch(pitch);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void larp$sent(CallbackInfo info) {
        LarpClient.ROTATIONS.sent(new Rotation(yRotLast, xRotLast));
    }
}
