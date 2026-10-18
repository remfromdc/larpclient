package dev.larp.client.setting;

import dev.larp.client.render.cape.Cape;
import java.util.function.Supplier;

public final class CapePreview extends PreviewSetting {
    private final Supplier<Cape> cape;

    public CapePreview(Supplier<Cape> cape) {
        super("capePreview");
        this.cape = cape;
    }

    public Cape cape() {
        return cape.get();
    }
}
