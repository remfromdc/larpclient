package dev.larp.client.module;

import dev.larp.client.render.LarpIcon;
import dev.larp.client.util.Lang;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true)
public enum Category {
    COMBAT("combat", LarpIcon.COMBAT),
    MOVEMENT("movement", LarpIcon.MOVEMENT),
    RENDER("render", LarpIcon.RENDER),
    HUD("hud", LarpIcon.HUD),
    PLAYER("player", LarpIcon.PLAYER),
    MISC("misc", LarpIcon.MISC);

    @Getter
    private final String id;
    @Getter
    private final LarpIcon icon;

    public String label() {
        return Lang.get("category." + id);
    }
}
