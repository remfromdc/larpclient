package dev.larp.client.module.impl.movement;

import dev.larp.client.module.Module;
import dev.larp.client.setting.EnumSetting;

public final class MoveFix extends Module {
    private static final String[] MODES = {"Silent", "Strict"};
    private static final int SILENT = 0;

    private final EnumSetting mode = add(new EnumSetting("mode", SILENT, MODES));

    public MoveFix() {
        super("moveFix");
    }

    @Override
    public String info() {
        return mode.selected();
    }

    public boolean corrects() {
        return isEnabled();
    }

    public boolean correctsInput() {
        return isEnabled() && mode.get() == SILENT;
    }
}
