package dev.larp.client.ui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.larp.Larp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads 64x64 world/server icons into GPU textures and draws them.
 * Entries are keyed by the caller (level id, server address + icon hash)
 * and released when the owning screen closes.
 */
public final class IconCache implements AutoCloseable {
    public record Entry(Identifier id, int width, int height) { }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, DynamicTexture> textures = new HashMap<>();
    /** Keys with no loadable image, so we never hit the disk twice. */
    private final java.util.Set<String> missing = new java.util.HashSet<>();

    /** Loads (or reuses) an icon from raw PNG bytes. Returns null when invalid. */
    public Entry iconFor(String key, byte[] png) {
        if (png == null || png.length == 0 || missing.contains(key)) return null;
        Entry cached = entries.get(key);
        if (cached != null) return cached;
        NativeImage image;
        try {
            image = NativeImage.read(png);
        } catch (IOException | RuntimeException ignored) {
            missing.add(key);
            return null;
        }
        return register(key, image);
    }

    /** Loads (or reuses) an icon from a world folder's icon file. */
    public Entry iconForFile(String key, Path file) {
        if (file == null || missing.contains(key)) return null;
        Entry cached = entries.get(key);
        if (cached != null) return cached;
        byte[] png;
        try (InputStream in = Files.newInputStream(file)) {
            png = in.readAllBytes();
        } catch (IOException | RuntimeException ignored) {
            missing.add(key);
            return null;
        }
        return iconFor(key, png);
    }

    private Entry register(String key, NativeImage image) {
        String safe = key.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        if (safe.length() > 64) safe = safe.substring(0, 64);
        Identifier id = Larp.id("menu-icon/" + safe);
        DynamicTexture texture = new DynamicTexture(() -> "larp-menu-icon", image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        Entry entry = new Entry(id, image.getWidth(), image.getHeight());
        entries.put(key, entry);
        textures.put(key, texture);
        return entry;
    }

    /** Draws an entry scaled into a square box. */
    public void draw(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int size) {
        if (entry == null) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, entry.id(), x, y, 0f, 0f,
                size, size, entry.width(), entry.height());
    }

    public void drop(String key) {
        Entry entry = entries.remove(key);
        DynamicTexture texture = textures.remove(key);
        if (entry != null) {
            Minecraft.getInstance().getTextureManager().release(entry.id());
        }
        if (texture != null) {
            try {
                texture.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        for (String key : entries.keySet().toArray(new String[0])) {
            drop(key);
        }
        missing.clear();
    }
}
