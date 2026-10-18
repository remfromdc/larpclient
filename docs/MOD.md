# LarpClient

A client-side **Minecraft 26.2 Fabric** mod with a fully custom, animated title screen and
an in-game ClickGUI bound to **Right Shift**.

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)
![Loader](https://img.shields.io/badge/Fabric-0.19.5-blue)
![Java](https://img.shields.io/badge/Java-25%2B-orange)

## Features

### Custom title screen

- Dark `#111111 → #222222` gradient with soft vignette and glow.
- Left-aligned layout: wireframe **Star of David** logo, `Larp Client` wordmark, text-only menu.
- `Singleplayer`, `Multiplayer`, `Minecraft Settings`, `Client`, `Quit Game`.
- Text-only buttons with a frame-rate independent hover bloom. All timing uses
  `Util.getMillis()` (no 20 TPS tick animations).
- `easeOutCubic` reveal for the logo, title and staggered menu entries.
- 3D player preview on the right that smoothly turns toward the mouse, using the
  local account skin (downloaded asynchronously, default skin as fallback).
- The vanilla `TitleScreen` is replaced through a mixin on `Gui#setScreen`
  (`MinecraftClient#setScreen` in older versions).

### In-game ClickGUI

- **Right Shift** toggles the client GUI in singleplayer and on servers.
- Animated panel reveal, sidebar, searchable module list, settings, configs, friends,
  capes and the HUD editor.
- Brand text in the top-left reads **larpclient.shop**.
- The GUI bind can be changed in the client settings.

## Requirements

- JDK **25** or newer (the build targets Java 25, as required by Minecraft 26.2).
- Internet access on the first build so Loom can download Minecraft/Fabric.

## Build

```bash
# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

The remapped jar is written to `build/libs/larpclient-1.0.0.jar`.

For a development client:

```bash
./gradlew runClient
```

## Source map

| File | Purpose |
| --- | --- |
| `src/client/java/dev/larp/client/ui/title/LarpTitleScreen.java` | Custom title screen, layout, logo, player preview and animations |
| `src/client/java/dev/larp/client/ui/title/LarpButton.java` | Text-only widget with real-time hover glow |
| `src/client/java/dev/larp/client/mixin/input/TitleScreenMixin.java` | Replaces the vanilla title screen |
| `src/client/resources/larpclient.mixins.json` | Mixin registration (client environment) |
| `src/main/resources/fabric.mod.json` | Mod metadata and client entrypoint |
| `src/client/java/dev/larp/client/LarpClient.java` | Client initializer (modules, keybinds, renderer) |
| `src/client/java/dev/larp/client/ui/clickgui/` | ClickGUI, sidebar and pages |

## 26.2 API notes

Minecraft 26.2 uses the extract-based GUI pipeline. The old Yarn names referenced in many
1.21 tutorials no longer exist:

| Older tutorials | 26.2 |
| --- | --- |
| `MinecraftClient.setScreen` | `Minecraft.gui.setScreen` (`Gui#setScreen`) |
| `DrawContext` | `GuiGraphicsExtractor` |
| `drawText(...)` | `graphics.text(font, text, x, y, color, false)` / `Text.draw(...)` |
| `ClickableWidget` | `AbstractWidget` |
| `render(DrawContext, ...)` | `extractRenderState(GuiGraphicsExtractor, ...)` |
| `MatrixStack` | `graphics.pose()` (`Matrix3x2fStack`) |

`LarpTitleScreen` draws with the client's MSDF `Text` renderer, which is shadow-free by
design and matches the ClickGUI typography.

## Credits

The client base (modules, renderer, ClickGUI) is derived from the CC0-licensed
[Adin](https://github.com/kolfish/adin) project by koifih. LarpClient re-brands it and adds
the custom title screen. See [LICENSE](../LICENSE).
