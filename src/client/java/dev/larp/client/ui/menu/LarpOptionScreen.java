package dev.larp.client.ui.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.function.Supplier;

/**
 * A generic option list: curated {@link OptionInstance}s bound to Larp rows.
 * Categories are built by the static factories below, each a hand-picked
 * subset of {@code minecraft.options} with short labels.
 */
public class LarpOptionScreen extends LarpListScreen {
    private final Populator populator;

    @FunctionalInterface
    public interface Populator {
        void populate(LarpOptionScreen screen);
    }

    public LarpOptionScreen(String title, String subtitle, Screen parent, Populator populator) {
        super(title, subtitle, parent);
        this.populator = populator;
    }

    private int staggerIndex;

    @Override
    protected void buildRows() {
        staggerIndex = 0;
        populator.populate(this);
    }

    /** Binds an option with default formatting. */
    public void option(Supplier<OptionInstance> source, String label) {
        row(OptionRows.bind(source, label, textSize, stagger(staggerIndex++)));
    }

    /** Binds an option with slider formatting (units, percent, custom text). */
    public void option(Supplier<OptionInstance> source, String label, OptionRows.SliderFormat slider) {
        row(OptionRows.bind(source, label, textSize, stagger(staggerIndex++), slider, null));
    }

    /** Binds a cyclable option with explicit value labels. */
    public void option(Supplier<OptionInstance> source, String label, List<String> labels) {
        row(OptionRows.bind(source, label, textSize, stagger(staggerIndex++),
                OptionRows.SliderFormat.plain(""), labels));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        drawList(graphics, mouseX, mouseY, delta, shown);
    }

    @Override
    protected void saveState() {
        minecraft.options.save();
    }

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    public static Populator video(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            screen.option(o::graphicsPreset, "Graphics Preset");
            screen.option(o::preferredGraphicsBackend, "Graphics Backend");
            screen.option(o::renderDistance, "Render Distance",
                    OptionRows.SliderFormat.plain(" chunks"));
            screen.option(o::simulationDistance, "Simulation Distance",
                    OptionRows.SliderFormat.plain(" chunks"));
            screen.option(o::entityDistanceScaling, "Entity Distance",
                    OptionRows.SliderFormat.percent());
            screen.option(o::framerateLimit, "Max Framerate", new OptionRows.SliderFormat(
                    v -> v, v -> v >= Options.UNLIMITED_FRAMERATE_CUTOFF ? "Unlimited" : Math.round(v) + " fps"));
            screen.option(o::enableVsync, "VSync");
            screen.option(o::fullscreen, "Fullscreen");
            screen.option(o::exclusiveFullscreen, "Exclusive Fullscreen");
            screen.option(o::bobView, "View Bobbing");
            screen.option(o::entityShadows, "Entity Shadows");
            screen.option(o::attackIndicator, "Attack Indicator");
            screen.option(o::particles, "Particles");
            screen.option(o::cloudStatus, "Clouds");
            screen.option(o::cloudRange, "Cloud Distance", OptionRows.SliderFormat.plain(" chunks"));
            screen.option(o::weatherRadius, "Weather Radius", OptionRows.SliderFormat.plain(""));
            screen.option(o::cutoutLeaves, "Cutout Leaves");
            screen.option(o::vignette, "Vignette");
            screen.option(o::improvedTransparency, "Fancy Transparency");
            screen.option(o::ambientOcclusion, "Smooth Lighting");
            screen.option(o::prioritizeChunkUpdates, "Chunk Updates");
            screen.option(o::chunkSectionFadeInTime, "Chunk Fade",
                    OptionRows.SliderFormat.plain(" ms"));
            screen.option(o::biomeBlendRadius, "Biome Blend", OptionRows.SliderFormat.plain(""));
            screen.option(o::mipmapLevels, "Mipmap Levels", OptionRows.SliderFormat.plain(""));
            screen.option(o::maxAnisotropyBit, "Anisotropy", OptionRows.SliderFormat.plain("x"));
            screen.option(o::textureFiltering, "Texture Filtering");
            screen.option(o::guiScale, "GUI Scale");
            screen.option(o::fov, "FOV", OptionRows.SliderFormat.plain(""));
            screen.option(o::gamma, "Brightness", OptionRows.SliderFormat.percent());
            screen.row(new MenuToggle(0, 0, 1, 1, "Pause on Lost Focus", screen.textSize,
                    screen.stagger(screen.staggerIndex++),
                    () -> o.pauseOnLostFocus, next -> o.pauseOnLostFocus = next));
        };
    }

    public static Populator keyBindsAndMouse() {
        return screen -> {
            screen.row(new MenuButton(0, 0, 1, 1, "Key Binds", screen.textSize,
                    screen.stagger(screen.staggerIndex++),
                    () -> screen.minecraft.gui.setScreen(new LarpKeybindsScreen(screen))));
            LarpOptionScreen.mouse(screen.minecraft).populate(screen);
        };
    }

    public static Populator mouse(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            screen.option(o::sensitivity, "Sensitivity", OptionRows.SliderFormat.percent());
            screen.option(o::invertMouseX, "Invert X Axis");
            screen.option(o::invertMouseY, "Invert Y Axis");
            screen.option(o::discreteMouseScroll, "Discrete Scroll");
            screen.option(o::mouseWheelSensitivity, "Scroll Sensitivity",
                    OptionRows.SliderFormat.plain(""));
            screen.option(o::rawMouseInput, "Raw Input");
            screen.option(o::allowCursorChanges, "Allow Cursor Changes");
            screen.option(o::toggleSprint, "Toggle Sprint");
            screen.option(o::toggleCrouch, "Toggle Sneak");
            screen.option(o::toggleAttack, "Toggle Attack");
            screen.option(o::toggleUse, "Toggle Use");
            screen.option(o::autoJump, "Auto Jump");
            screen.option(o::sprintWindow, "Sprint Window", OptionRows.SliderFormat.plain(" ms"));
            screen.option(o::rotateWithMinecart, "Rotate With Minecart");
            screen.row(new MenuToggle(0, 0, 1, 1, "Advanced Tooltips", screen.textSize,
                    screen.stagger(screen.staggerIndex++),
                    () -> o.advancedItemTooltips, next -> o.advancedItemTooltips = next));
        };
    }

    public static Populator sound(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            for (SoundSource source : SoundSource.values()) {
                screen.option(() -> o.getSoundSourceOptionInstance(source),
                        MenuStyle.prettify(source.name()) + " Volume",
                        OptionRows.SliderFormat.percent());
            }
            screen.option(o::showSubtitles, "Subtitles");
            screen.option(o::directionalAudio, "Directional Audio");
            screen.option(o::musicFrequency, "Music Frequency");
            screen.option(o::musicToast, "Now Playing Toast");
            screen.option(o::soundDevice, "Sound Device");
        };
    }

    public static Populator chat(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            screen.option(o::chatVisibility, "Chat Visibility");
            screen.option(o::chatColors, "Colors");
            screen.option(o::chatLinks, "Web Links");
            screen.option(o::chatLinksPrompt, "Link Prompt");
            screen.option(o::chatOpacity, "Text Opacity", OptionRows.SliderFormat.percent());
            screen.option(o::textBackgroundOpacity, "Background Opacity",
                    OptionRows.SliderFormat.percent());
            screen.option(o::backgroundForChatOnly, "Chat Focus Only");
            screen.option(o::chatScale, "Scale", OptionRows.SliderFormat.percent());
            screen.option(o::chatWidth, "Width", OptionRows.SliderFormat.percent());
            screen.option(o::chatHeightFocused, "Focused Height", OptionRows.SliderFormat.plain(""));
            screen.option(o::chatHeightUnfocused, "Unfocused Height", OptionRows.SliderFormat.plain(""));
            screen.option(o::chatLineSpacing, "Line Spacing", OptionRows.SliderFormat.plain(""));
            screen.option(o::chatDelay, "Message Delay", OptionRows.SliderFormat.plain(" ticks"));
            screen.option(o::notificationDisplayTime, "Toast Duration",
                    OptionRows.SliderFormat.plain(" s"));
            screen.option(o::onlyShowSecureChat, "Secure Chat Only");
            screen.option(o::saveChatDrafts, "Save Drafts");
            screen.option(o::autoSuggestions, "Command Suggestions");
        };
    }

    public static Populator accessibility(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            screen.option(o::narrator, "Narrator");
            screen.option(o::narratorHotkey, "Narrator Hotkey");
            screen.option(o::highContrast, "High Contrast");
            screen.option(o::highContrastBlockOutline, "Outline Contrast");
            screen.option(o::hideLightningFlash, "Hide Lightning Flashes");
            screen.option(o::hideSplashTexts, "Hide Splash Texts");
            screen.option(o::darkMojangStudiosBackground, "Dark Mojang Background");
            screen.option(o::screenEffectScale, "Screen Effects", OptionRows.SliderFormat.percent());
            screen.option(o::fovEffectScale, "FOV Effects", OptionRows.SliderFormat.percent());
            screen.option(o::darknessEffectScale, "Darkness Effect", OptionRows.SliderFormat.percent());
            screen.option(o::damageTiltStrength, "Damage Tilt", OptionRows.SliderFormat.percent());
            screen.option(o::glintSpeed, "Glint Speed", OptionRows.SliderFormat.percent());
            screen.option(o::glintStrength, "Glint Strength", OptionRows.SliderFormat.percent());
            screen.option(o::menuBackgroundBlurriness, "Menu Blur", OptionRows.SliderFormat.plain(""));
            screen.option(o::panoramaSpeed, "Panorama Speed", OptionRows.SliderFormat.plain(""));
            screen.option(o::hideMatchedNames, "Hide Matched Names");
            screen.option(o::showAutosaveIndicator, "Autosave Indicator");
            screen.option(o::inGameNotification, "In-Game Notifications");
            screen.option(o::forceUnicodeFont, "Force Unicode Font");
            screen.option(o::japaneseGlyphVariants, "Japanese Glyphs");
        };
    }

    public static Populator online(Minecraft minecraft) {
        Options o = minecraft.options;
        return screen -> {
            screen.option(o::realmsNotifications, "Realms News");
            screen.option(o::allowServerListing, "Server Listing");
            screen.option(o::sharePresence, "Share Presence");
            screen.row(new MenuToggle(0, 0, 1, 1, "Hide Server Addresses", screen.textSize,
                    screen.stagger(screen.staggerIndex++),
                    () -> o.hideServerAddress, next -> o.hideServerAddress = next));
        };
    }
}
