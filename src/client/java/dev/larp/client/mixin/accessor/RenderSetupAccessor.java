package dev.larp.client.mixin.accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("textures")
    Map<String, ?> larp$getTextures();
}
