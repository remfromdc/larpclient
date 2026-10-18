package dev.larp.client.module.impl.render;

import dev.larp.client.event.events.EntityRenderStateEvent;
import dev.larp.client.module.Module;
import dev.larp.client.render.cape.Cape;
import dev.larp.client.render.cape.CapeCatalog;
import dev.larp.client.render.cape.ClothCape;
import dev.larp.client.setting.BoolSetting;
import dev.larp.client.setting.CapeSetting;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class Capes extends Module {
    private final CapeSetting cape = add(new CapeSetting());
    private final BoolSetting cloth = add(new BoolSetting("cloth", false));

    public Capes() {
        super("capes");
    }

    public Cape selected() {
        return CapeCatalog.get(cape.get());
    }

    @Override
    protected void onEnable() {
        listen(EntityRenderStateEvent.class, this::onRenderState);
    }

    @Override
    protected void onDisable() {
        ClothCape.enabled = false;
    }

    private void onRenderState(EntityRenderStateEvent event) {
        if (!(event.state() instanceof AvatarRenderState avatar) || event.entity() != mc.player) return;
        ClothCape.enabled = cloth.get();
        Cape selected = selected();
        selected.update();
        avatar.skin = selected.apply(avatar.skin);
        avatar.showCape = true;
    }
}
