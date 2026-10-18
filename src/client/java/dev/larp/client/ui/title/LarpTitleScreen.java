package dev.larp.client.ui.title;

import com.mojang.authlib.GameProfile;
import dev.larp.client.render.Draw;
import dev.larp.client.render.Text;
import dev.larp.client.ui.clickgui.ClickGui;
import dev.larp.client.ui.menu.LarpServerListScreen;
import dev.larp.client.ui.menu.LarpSettingsScreen;
import dev.larp.client.ui.menu.LarpWorldListScreen;
import dev.larp.client.util.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LarpClient's custom, left-aligned title screen.
 *
 * <p>Everything is drawn through the 26.2 extract-based GUI pipeline
 * ({@link GuiGraphicsExtractor}); no {@code MatrixStack} is touched directly.
 * All timing uses {@link Util#getMillis()} so the fade-in survives low tick
 * rates and stays smooth at high frame rates.</p>
 */
public final class LarpTitleScreen extends Screen {
    private static final String TITLE = "Larp Client";
    private static final String BRAND = "larpclient.shop";
    private static final int BACKGROUND_TOP = 0xFF111111;
    private static final int BACKGROUND_BOTTOM = 0xFF222222;
    private static final int TEXT_IDLE = 0xFFE9E9E9;
    private static final int TEXT_MUTED = 0xFF6D6D6D;

    private static final int REVEAL_MILLIS = 620;
    private static final String[] MENU = {
            "Singleplayer",
            "Multiplayer",
            "Minecraft Settings",
            "Client",
            "Quit Game"
    };

    private final long openedAt = Util.getMillis();

    private float ui = 1f;
    private float margin = 40f;
    private float logoRadius = 26f;
    private float logoCenterX;
    private float logoCenterY;
    private float titleBaseline;

    /** Smoothed look direction, driven by the mouse position relative to the preview. */
    private float lookYaw;
    private float lookPitch;
    private long lastPreviewFrame = Util.getMillis();
    private PlayerSkin skin;
    private CompletableFuture<Optional<PlayerSkin>> skinLookup;

    public LarpTitleScreen() {
        super(Component.literal(TITLE));
    }

    /** easeOutCubic(t) = 1 - (1 - t)^3, clamped to [0, 1]. */
    private static float easeOutCubic(float t) {
        t = Math.clamp(t, 0f, 1f);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    private float reveal() {
        long elapsed = Util.getMillis() - openedAt;
        if (elapsed <= 0L) return 0f;
        return easeOutCubic(elapsed / (float) REVEAL_MILLIS);
    }

    @Override
    protected void init() {
        // Scale with both axes: the reference is a tall layout, so a wide
        // window with a short GUI height must still fit all five entries.
        ui = Math.clamp(Math.min(width / 1280f, height / 720f), 0.6f, 1.15f);
        margin = 40f * ui;
        logoRadius = 27f * ui;
        logoCenterX = margin + logoRadius;
        // Keep the logo near Y=80 at normal scales but never let it drift past
        // the top eighth of a short window.
        float logoTop = Math.min(78f * ui, height * 0.125f);
        logoCenterY = logoTop + logoRadius;
        titleBaseline = logoCenterY + logoRadius + 36f * ui;

        float textSize = 15f * ui;
        float startY = titleBaseline + 38f * ui;
        float stride = 27f * ui;
        float buttonHeight = 20f * ui;

        for (int i = 0; i < MENU.length; i++) {
            String label = MENU[i];
            int buttonWidth = Math.max(Math.round(240f * ui),
                    Math.round(Text.width(label, textSize) + 36f * ui));
            float y = startY + i * stride;
            LarpButton button = new LarpButton(Math.round(margin), Math.round(y), buttonWidth,
                    Math.round(buttonHeight), Component.literal(label), textSize, 140 + i * 55,
                    actionFor(i));
            addRenderableWidget(button);
        }

        loadSkin();
    }

    private Runnable actionFor(int index) {
        return switch (index) {
            case 0 -> () -> minecraft.gui.setScreen(new LarpWorldListScreen(this));
            case 1 -> () -> minecraft.gui.setScreen(new LarpServerListScreen(this));
            case 2 -> () -> minecraft.gui.setScreen(new LarpSettingsScreen(this));
            // The ClickGUI is fully client-side and works before a world is joined.
            case 3 -> () -> minecraft.gui.setScreen(new ClickGui());
            case 4 -> () -> minecraft.stop();
            default -> () -> { };
        };
    }

    /**
     * Resolves the local account skin. The default skin is used immediately so
     * the preview never flickers, then the real skin replaces it when the skin
     * manager finishes its lookup.
     */
    private void loadSkin() {
        if (minecraft.player != null) {
            skin = minecraft.player.getSkin();
            return;
        }
        GameProfile profile = minecraft.getGameProfile();
        skin = profile == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(profile);
        if (profile != null) skinLookup = minecraft.getSkinManager().get(profile);
    }

    @Override
    public void tick() {
        if (skinLookup != null && skinLookup.isDone()) {
            try {
                skinLookup.join().ifPresent(loaded -> skin = loaded);
            } catch (RuntimeException ignored) {
                // Offline/dev accounts may not have a resolvable skin; keep the default.
            }
            skinLookup = null;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Dark, smooth vertical gradient. The soft discs add the "slightly
        // blurred" depth of the reference image without a post-process pass.
        graphics.fillGradient(0, 0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        float glowX = width * 0.72f;
        float glowY = height * 0.52f;
        Draw.rect(graphics, glowX - 300f * ui, glowY - 300f * ui, 600f * ui,
                Math.round(600f * ui), Math.round(300f * ui), 0x0EFFFFFF);
        Draw.rect(graphics, width * 0.18f - 220f * ui, height * 0.22f - 220f * ui,
                440f * ui, Math.round(440f * ui), Math.round(220f * ui), 0x08000000);
        // Vignette along the edges keeps the left column readable.
        graphics.fillGradient(0, 0, Math.round(260f * ui), height, 0x66000000, 0x00000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;

        drawLogo(graphics, shown);
        drawTitle(graphics, shown);
        drawPreview(graphics, mouseX, mouseY, shown);

        // Widgets are drawn manually so the whole screen shares one reveal pass.
        for (var child : children()) {
            if (child instanceof LarpButton button) {
                button.extractRenderState(graphics, mouseX, mouseY, delta);
            }
        }

        Text.draw(graphics, BRAND, margin, height - 22f * ui, 9.5f * ui,
                Colors.withAlpha(TEXT_MUTED, shown));
    }

    private void drawLogo(GuiGraphicsExtractor graphics, float shown) {
        // Three passes: a wide, very faint halo, a softer inner halo, then the
        // crisp wireframe. Cheap, resolution independent and no texture needed.
        drawStar(graphics, logoCenterX, logoCenterY, logoRadius, 10f * ui,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.045f));
        drawStar(graphics, logoCenterX, logoCenterY, logoRadius, 5f * ui,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.085f));
        drawStar(graphics, logoCenterX, logoCenterY, logoRadius, 1.9f * ui,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.96f));
    }

    /** Draws a Star of David as two overlapping wireframe triangles. */
    private void drawStar(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius,
                          float thickness, int color) {
        drawTriangle(graphics, centerX, centerY, radius, -90f, thickness, color);
        drawTriangle(graphics, centerX, centerY, radius, 90f, thickness, color);
    }

    private void drawTriangle(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius,
                              float startDegrees, float thickness, int color) {
        float[] xs = new float[3];
        float[] ys = new float[3];
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(startDegrees + i * 120.0);
            xs[i] = centerX + (float) Math.cos(angle) * radius;
            ys[i] = centerY + (float) Math.sin(angle) * radius;
        }
        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            drawLine(graphics, xs[i], ys[i], xs[next], ys[next], thickness, color);
        }
    }

    /** A rotated rounded rectangle is a cheap antialiased-looking line. */
    private void drawLine(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2,
                          float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001f || Colors.transparent(color)) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x1, y1).rotate((float) Math.atan2(dy, dx)).scale(1f, thickness);
            Draw.rect(graphics, 0f, 0f, length, 1, 0, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawTitle(GuiGraphicsExtractor graphics, float shown) {
        float size = 33f * ui;
        int color = Colors.withAlpha(TEXT_IDLE, shown);
        // Flat, shadow-free title with a very light bloom.
        Text.draw(graphics, TITLE, margin - 1.2f, titleBaseline, size,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.07f));
        Text.draw(graphics, TITLE, margin + 1.2f, titleBaseline, size,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.07f));
        Text.draw(graphics, TITLE, margin, titleBaseline - 1.2f, size,
                Colors.withAlpha(0xFFFFFFFF, shown * 0.07f));
        Text.draw(graphics, TITLE, margin, titleBaseline, size, color);
    }

    private void drawPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float shown) {
        if (skin == null || width < 560f * ui) return;

        long now = Util.getMillis();
        float frameMillis = Math.clamp(now - lastPreviewFrame, 0L, 50L);
        lastPreviewFrame = now;

        float centerX = width * 0.73f;
        float centerY = height * 0.56f;
        float boxHeight = Math.min(height * 0.74f, 440f * ui);
        float boxWidth = 330f * ui;

        // Smoothed mouse following with the same real-time lerp as the buttons.
        float targetYaw = Math.clamp((mouseX - centerX) / (7.5f * ui), -30f, 30f);
        float targetPitch = Math.clamp((mouseY - centerY) / (10f * ui), -22f, 22f);
        float blend = 1f - (float) Math.exp(-frameMillis / 110f);
        lookYaw += (targetYaw - lookYaw) * blend;
        lookPitch += (targetPitch - lookPitch) * blend;

        float pop = 0.94f + 0.06f * shown;
        float scale = Math.min(boxHeight * 0.62f / 1.8f, boxWidth * 0.62f / 0.6f) * pop;

        // Soft ground shadow, anchored to the model's feet.
        float shadowY = centerY + 0.9f * scale - 2f * ui;
        Draw.rect(graphics, centerX - 90f * ui, shadowY, 180f * ui, Math.round(18f * ui),
                Math.round(9f * ui), Colors.withAlpha(0xFF000000, shown * 0.30f));
        Draw.rect(graphics, centerX - 55f * ui, shadowY + 3f * ui, 110f * ui, Math.round(10f * ui),
                Math.round(5f * ui), Colors.withAlpha(0xFF000000, shown * 0.24f));
        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityTypes.PLAYER;
        state.skin = skin;
        state.scale = 1f;
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;
        state.pose = Pose.STANDING;
        state.isInvisible = false;
        state.ageInTicks = (now % 20_000L) / 50f;
        state.id = minecraft.getUser() == null ? 0 : minecraft.getUser().getProfileId().hashCode();
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = true;
        // Vanilla's inventory preview convention: body faces the viewer, while
        // the head and torso follow the cursor.
        float baseYaw = 8f;
        state.bodyRot = 180f + baseYaw + lookYaw;
        state.yRot = baseYaw + lookYaw;
        state.xRot = -lookPitch;

        float boxX0 = centerX - boxWidth * 0.5f;
        float boxY0 = centerY - boxHeight * 0.5f;
        float boxX1 = centerX + boxWidth * 0.5f;
        float boxY1 = centerY + boxHeight * 0.5f;
        Vector3f translation = new Vector3f(0f, 0.9f, 0f);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(lookPitch * 0.35f));

        graphics.entity(state, scale, translation, rotation, camera,
                Math.round(boxX0), Math.round(boxY0), Math.round(boxX1), Math.round(boxY1));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
