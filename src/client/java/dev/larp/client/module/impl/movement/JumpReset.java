package dev.larp.client.module.impl.movement;

import dev.larp.client.event.events.PreTickEvent;
import dev.larp.client.module.Module;
import dev.larp.client.setting.Measure;
import dev.larp.client.setting.SliderSetting;
import net.minecraft.client.player.LocalPlayer;

public final class JumpReset extends Module {
    private final SliderSetting chance = add(new SliderSetting("chance", 100, 0, 100, Measure.PERCENT));
    private int lastHurt;
    private boolean pressed;
    private boolean wasDown;

    public JumpReset() {
        super("jumpReset");
    }

    @Override
    protected void onEnable() {
        listen(PreTickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        release();
        lastHurt = 0;
    }

    private void onTick(PreTickEvent event) {
        LocalPlayer player = event.client().player;
        release();
        if (player == null) return;
        boolean hurt = player.hurtTime > lastHurt;
        lastHurt = player.hurtTime;
        if (hurt && player.onGround() && player.getRandom().nextInt(100) < chance.get()) press();
    }

    private void press() {
        wasDown = mc.options.keyJump.isDown();
        mc.options.keyJump.setDown(true);
        pressed = true;
    }

    private void release() {
        if (!pressed) return;
        mc.options.keyJump.setDown(wasDown);
        pressed = false;
    }
}
