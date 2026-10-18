package dev.larp.client.mixin.input;

import dev.larp.client.LarpClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void larp$correctInput(CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec2 corrected = LarpClient.ROTATIONS.correctInput(moveVector, player.getYRot());
        if (corrected == moveVector) return;
        moveVector = corrected;
        Input keys = keyPresses;
        keyPresses = new Input(corrected.y > 0f, corrected.y < 0f, corrected.x > 0f, corrected.x < 0f, keys.jump(), keys.shift(), keys.sprint());
    }
}
