package dev.larp.client.render.screen;

import dev.larp.Larp;
import dev.larp.client.LarpClient;
import dev.larp.client.event.events.HudRenderEvent;
import dev.larp.client.event.events.ScreenRenderEvent;
import dev.larp.client.render.Opacity;
import dev.larp.client.render.Scissor;
import dev.larp.client.render.state.LineState;
import dev.larp.client.render.state.QuadState;
import dev.larp.client.render.state.ShapeState;
import dev.larp.client.render.state.Submit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScreenRenderer {
    public static void init() {
        HudElementRegistry.addLast(Larp.id("overlay"), ScreenRenderer::extract);
    }

    public static void submit(GuiGraphicsExtractor graphics, ScreenBuffer buffer) {
        if (buffer.isEmpty()) return;
        Matrix3x2fc pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = Scissor.current();
        for (ScreenBuffer.Shape shape : buffer.shapes()) {
            Submit.submit(graphics, new ShapeState(pose, shape.bounds(), Opacity.apply(shape.style()), scissor));
        }
        for (ScreenBuffer.Quad quad : buffer.quads()) {
            Submit.submit(graphics, new QuadState(pose, quad.a(), quad.b(), quad.c(), quad.d(),
                    Opacity.apply(quad.color()), scissor));
        }
        for (ScreenBuffer.Line line : buffer.lines()) {
            Submit.submit(graphics, new LineState(pose, line.x0(), line.y0(), line.x1(), line.y1(), line.width(),
                    Opacity.apply(line.color()), scissor));
        }
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ScreenBuffer buffer = new ScreenBuffer();
        LarpClient.EVENTS.post(new ScreenRenderEvent(minecraft.level, minecraft.gameRenderer.mainCamera(), deltaTracker,
                Projection.capture(), buffer));
        submit(graphics, buffer);
        LarpClient.EVENTS.post(new HudRenderEvent(graphics, deltaTracker));
    }
}
