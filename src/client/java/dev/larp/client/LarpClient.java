package dev.larp.client;

import dev.larp.client.util.Clicks;
import dev.larp.client.util.Time;
import dev.larp.client.event.Priority;
import dev.larp.client.backtrack.Backtracker;
import dev.larp.client.config.StateStore;
import dev.larp.client.event.EventBus;
import dev.larp.client.event.events.PreTickEvent;
import dev.larp.client.event.events.TickEvent;
import dev.larp.client.input.Keybinds;
import dev.larp.client.module.ModuleManager;
import dev.larp.client.render.screen.ScreenRenderer;
import dev.larp.client.render.blocks.BlockIndex;
import dev.larp.client.render.blocks.BlockRenderer;
import dev.larp.client.render.world.WorldRenderer;
import dev.larp.client.rotation.RotationManager;
import dev.larp.client.ui.HudEditor;
import dev.larp.client.ui.preview.EntityPreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class LarpClient implements ClientModInitializer {
    public static final EventBus EVENTS = new EventBus();
    public static final ModuleManager MODULES = new ModuleManager();
    public static final RotationManager ROTATIONS = new RotationManager();

    @Override
    public void onInitializeClient() {
        StateStore.init();
        Backtracker.init();
        Keybinds.register();
        ROTATIONS.init();
        HudEditor.init();
        WorldRenderer.init();
        BlockIndex.init(BlockRenderer::onSectionChanged);
        BlockRenderer.init();
        ScreenRenderer.init();
        EVENTS.subscribe(PreTickEvent.class, Priority.HIGHEST, event -> Time.tick());
        EVENTS.subscribe(PreTickEvent.class, event -> MODULES.tickKeybinds(event.client()));
        ClientTickEvents.START_CLIENT_TICK.register(client -> EVENTS.post(new PreTickEvent(client)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> EVENTS.post(new TickEvent(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EntityPreview.clear());
        Clicks.init();
    }
}
