package dev.larp.client.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.larp.client.LarpClient;
import dev.larp.client.event.events.PacketProcessEvent;
import dev.larp.client.module.impl.combat.AimAssist;
import dev.larp.client.util.Clicks;
import dev.larp.client.util.Players;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public HitResult hitResult;
    @Shadow public MultiPlayerGameMode gameMode;
    @Shadow public LocalPlayer player;

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V", shift = At.Shift.AFTER))
    private void larp$packetsProcessed(boolean renderLevel, CallbackInfo info) {
        LarpClient.EVENTS.post(new PacketProcessEvent((Minecraft) (Object) this));
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void larp$startAttack(CallbackInfoReturnable<Boolean> info) {
        if (larp$blocksBreaking() || larp$activates(GLFW.GLFW_MOUSE_BUTTON_LEFT)) info.setReturnValue(false);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void larp$startUseItem(CallbackInfo info) {
        if (larp$activates(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && !Players.consuming(player)) info.cancel();
    }

    @Unique
    private static boolean larp$activates(int button) {
        return !Clicks.simulating(button) && LarpClient.MODULES.activates(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void larp$continueAttack(boolean leftClick, CallbackInfo info) {
        if (larp$blocksBreaking()) info.cancel();
    }

    @Unique
    private boolean larp$blocksBreaking() {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!LarpClient.MODULES.get(AimAssist.class).blocksBreaking()) return false;
        if (gameMode != null) gameMode.stopDestroyBlock();
        return true;
    }
}
