package dev.larp.client.ui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Create-world page in Larp style: name and seed fields, game mode and
 * difficulty cycles, and toggles for hardcore, cheats, structures and the
 * bonus chest. Worlds generate with default overworld settings.
 */
public class LarpCreateWorldScreen extends LarpListScreen {
    private final FieldState nameField;
    private final FieldState seedField;
    private GameType gameType = GameType.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean hardcore;
    private boolean cheats;
    private boolean structures = true;
    private boolean bonusChest;

    public LarpCreateWorldScreen(Screen parent) {
        super("Create New World", "Name it, tune it, generate it.", parent);
        this.nameField = new FieldState("", 48, MenuField.freeText());
        this.seedField = new FieldState("", 32, s -> !s.contains(" "));
    }

    /** Re-create flow: same settings as an existing world, fresh folder. */
    public static LarpCreateWorldScreen prefilled(Screen parent, String name, String seed, GameType mode,
                                                  Difficulty difficulty, boolean hardcore, boolean cheats,
                                                  boolean structures, boolean bonusChest) {
        LarpCreateWorldScreen screen = new LarpCreateWorldScreen(parent);
        screen.nameField.set(name);
        screen.seedField.set(seed);
        screen.gameType = mode;
        screen.difficulty = difficulty;
        screen.hardcore = hardcore;
        screen.cheats = cheats && !hardcore;
        screen.structures = structures;
        screen.bonusChest = bonusChest;
        return screen;
    }

    @Override
    protected void buildRows() {
        int index = 0;
        MenuField name = new MenuField(0, 0, 1, 1, "World Name", textSize, stagger(index++),
                nameField, "My World");
        name.setHeight(Math.round(fieldHeight));
        row(name);
        MenuField seed = new MenuField(0, 0, 1, 1, "Seed (blank for random)", textSize, stagger(index++),
                seedField, "Leave blank for a random seed");
        seed.setHeight(Math.round(fieldHeight));
        row(seed);

        GameType[] modes = {GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR};
        row(new MenuCycle(0, 0, 1, 1, "Game Mode", textSize, stagger(index++),
                List.of("Survival", "Creative", "Adventure", "Spectator"),
                () -> slotOf(modes, hardcore ? GameType.SURVIVAL : gameType),
                slot -> gameType = modes[slot]));
        Difficulty[] difficulties = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        row(new MenuCycle(0, 0, 1, 1, "Difficulty", textSize, stagger(index++),
                List.of("Peaceful", "Easy", "Normal", "Hard"),
                () -> slotOf(difficulties, hardcore ? Difficulty.HARD : difficulty),
                slot -> difficulty = difficulties[slot]));
        row(new MenuToggle(0, 0, 1, 1, "Hardcore", textSize, stagger(index++),
                () -> hardcore, next -> {
                    hardcore = next;
                    if (next) cheats = false;
                }));
        MenuToggle cheatsRow = new MenuToggle(0, 0, 1, 1, "Allow Cheats", textSize, stagger(index++),
                () -> cheats && !hardcore, next -> cheats = next && !hardcore);
        cheatsRow.active = !hardcore;
        row(cheatsRow);
        row(new MenuToggle(0, 0, 1, 1, "Generate Structures", textSize, stagger(index++),
                () -> structures, next -> structures = next));
        row(new MenuToggle(0, 0, 1, 1, "Bonus Chest", textSize, stagger(index++),
                () -> bonusChest, next -> bonusChest = next));

        row(new MenuButton(0, 0, 1, 1, "Create New World", textSize,
                stagger(index), this::create));
    }

    private static <T> int slotOf(T[] values, T current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return i;
        }
        return 0;
    }

    private void create() {
        String name = nameField.value().trim();
        if (name.isEmpty()) {
            confirm("Name Your World", "Give the world a name first.", "OK", false, null);
            return;
        }
        LevelStorageSource source = minecraft.getLevelSource();
        String folderId = folderId(source, name);

        OptionalLong seed = WorldOptions.parseSeed(seedField.value().trim());
        long seedValue = seed.orElseGet(WorldOptions::randomSeed);
        WorldOptions worldOptions = new WorldOptions(seedValue, structures, bonusChest);
        WorldGenSettings genSettings = WorldGenSettings.of(worldOptions, RegistryAccess.EMPTY);
        Difficulty effectiveDifficulty = hardcore ? Difficulty.HARD : difficulty;
        GameType effectiveMode = hardcore ? GameType.SURVIVAL : gameType;
        LevelSettings settings = new LevelSettings(name, effectiveMode,
                new LevelSettings.DifficultySettings(effectiveDifficulty, hardcore, false),
                !hardcore && cheats, WorldDataConfiguration.DEFAULT);
        new WorldOpenFlows(minecraft, source).createFreshLevel(folderId, settings, worldOptions,
                registry -> genSettings.dimensions(), this);
    }

    private static String folderId(LevelStorageSource source, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]", "-").replaceAll("-+", "-");
        base = base.replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "world";
        String id = base;
        int counter = 1;
        while (!source.isNewLevelIdAcceptable(id) || source.levelExists(id)) {
            id = base + "-" + (counter++);
        }
        return id;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        drawList(graphics, mouseX, mouseY, delta, shown);
    }

}
