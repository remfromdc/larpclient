package dev.larp.client.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.larp.client.LarpClient;
import dev.larp.client.module.impl.combat.Triggerbot;
import dev.larp.client.util.Entities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float larp$jumpYaw(float yaw) {
        return LarpClient.ROTATIONS.movementYaw((Entity) (Object) this, yaw);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float larp$glidePitch(float pitch) {
        return LarpClient.ROTATIONS.movementPitch((Entity) (Object) this, pitch);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 larp$glideLook(Vec3 look) {
        return LarpClient.ROTATIONS.movementLook((Entity) (Object) this, look);
    }

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void larp$holdSprint(boolean sprinting, CallbackInfo info) {
        if (!sprinting || !Entities.isLocal((Entity) (Object) this)) return;
        if (LarpClient.MODULES.get(Triggerbot.class).holdsSprint()) info.cancel();
    }
}
