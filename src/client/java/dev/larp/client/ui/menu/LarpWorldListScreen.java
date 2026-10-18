package dev.larp.client.ui.menu;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleplayer world list. Worlds load asynchronously; rows show the world
 * icon, name, details and status. Play, Create, Rename, Delete and
 * Re-create sit in a fixed action strip above the Back row.
 */
public class LarpWorldListScreen extends LarpListScreen {
    private List<LevelSummary> summaries;
    private boolean loading;
    private String selectedId;
    private final IconCache icons = new IconCache();

    private MenuButton playButton;
    private MenuButton editButton;
    private MenuButton deleteButton;
    private MenuButton recreateButton;

    public LarpWorldListScreen(Screen parent) {
        super("Singleplayer", "Pick a world, or create a new one.", parent);
    }

    private LevelSummary selected() {
        if (summaries == null || selectedId == null) return null;
        for (LevelSummary summary : summaries) {
            if (summary.getLevelId().equals(selectedId)) return summary;
        }
        return null;
    }

    @Override
    protected void buildRows() {
        if (summaries == null && !loading) load();
        if (summaries == null) return;
        int index = 0;
        for (LevelSummary summary : summaries) {
            WorldRow row = new WorldRow(summary, icons, "w:" + summary.getLevelId(), textSize,
                    stagger(index++), () -> select(summary), () -> play(selected()));
            row.setSelected(summary.getLevelId().equals(selectedId));
            // Two text lines plus an icon: the shared card height, not the
            // one-line default, so the detail line is never squeezed.
            row.setHeight(Math.round(cardHeight));
            row(row);
        }
        if (selected() == null && !summaries.isEmpty()) {
            selectedId = summaries.get(0).getLevelId();
            refreshSelection();
        }

        float footerWidth = (contentWidth - 4f * 10f * ui) / 5f;
        int footerStagger = stagger(index);
        footer(playButton = actionButton("Play", 0, footerWidth, footerStagger, () -> play(selected())));
        footer(actionButton("Create", 1, footerWidth, footerStagger, this::create));
        footer(editButton = actionButton("Rename", 2, footerWidth, footerStagger, this::rename));
        footer(deleteButton = actionButton("Delete", 3, footerWidth, footerStagger, this::askDelete));
        footer(recreateButton = actionButton("Re-create", 4, footerWidth, footerStagger, this::recreate));
        updateActions();
    }

    private MenuButton actionButton(String label, int slot, float slotWidth, int delay, Runnable action) {
        return new MenuButton(0, 0, 1, 1, label, textSize * 0.9f, delay, action);
    }

    private java.util.concurrent.CompletableFuture<List<LevelSummary>> pending;

    private void load() {
        loading = true;
        LevelStorageSource source = minecraft.getLevelSource();
        pending = source.loadLevelSummaries(source.findLevelCandidates());
    }

    @Override
    public void tick() {
        super.tick();
        // The summary future completes off-thread; join it here on the
        // render thread before touching widgets or textures.
        if (pending != null && pending.isDone()) {
            try {
                List<LevelSummary> sorted = new ArrayList<>(pending.join());
                sorted.sort(null);
                summaries = sorted;
            } catch (RuntimeException ignored) {
                summaries = List.of();
            }
            pending = null;
            loading = false;
            rebuildWidgets();
        }
    }

    private void reload() {
        icons.close();
        summaries = null;
        loading = false;
        selectedId = null;
        rebuildWidgets();
    }

    private void select(LevelSummary summary) {
        selectedId = summary.getLevelId();
        refreshSelection();
    }

    private void refreshSelection() {
        for (MenuWidget widget : rows) {
            if (widget instanceof WorldRow row) {
                row.setSelected(row.summary().getLevelId().equals(selectedId));
            }
        }
        updateActions();
    }

    private void updateActions() {
        LevelSummary current = selected();
        boolean has = current != null;
        if (playButton != null) playButton.setEnabled(has && !current.isLocked() && current.primaryActionActive());
        if (editButton != null) editButton.setEnabled(has && current.canEdit());
        if (deleteButton != null) deleteButton.setEnabled(has && current.canDelete());
        if (recreateButton != null) recreateButton.setEnabled(has && current.canRecreate());
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void play(LevelSummary summary) {
        if (summary == null || summary.isLocked() || !summary.primaryActionActive()) return;
        Runnable back = () -> minecraft.gui.setScreen(this);
        if (summary.shouldBackup()) {                    confirm("Back Up and Play?",
                    "This world is from an older version. Larp will back it up, then play.",
                    "Back Up and Play", false, () -> {
                        LevelStorageSource.LevelStorageAccess access = null;
                        try {
                            access = minecraft.getLevelSource().createAccess(summary.getLevelId());
                            access.makeWorldBackup();
                        } catch (Exception ignored) {
                        } finally {
                            if (access != null) access.safeClose();
                        }
                        flows().openWorld(summary.getLevelId(), back);
                    });
            return;
        }
        flows().openWorld(summary.getLevelId(), back);
    }

    private void create() {
        minecraft.gui.setScreen(new LarpCreateWorldScreen(this));
    }

    private void rename() {
        LevelSummary summary = selected();
        if (summary == null || !summary.canEdit()) return;
        prompt("Rename World", summary.getLevelName(), 48, name -> {
            if (name.isEmpty() || name.equals(summary.getLevelName())) return;
            LevelStorageSource.LevelStorageAccess access = null;
            try {
                access = minecraft.getLevelSource().createAccess(summary.getLevelId());
                access.renameLevel(name);
            } catch (Exception error) {
                confirm("Couldn't Rename", String.valueOf(error.getMessage()), "OK", false, null);
                return;
            } finally {
                if (access != null) access.safeClose();
            }
            reload();
        });
    }

    private void askDelete() {
        LevelSummary summary = selected();
        if (summary == null || !summary.canDelete()) return;
        confirm("Delete World?",
                "\"" + summary.getLevelName() + "\" will be gone forever. This cannot be undone.",
                "Delete", true, () -> {
                    LevelStorageSource.LevelStorageAccess access = null;
                    try {
                        access = minecraft.getLevelSource().createAccess(summary.getLevelId());
                        access.deleteLevel();
                    } catch (Exception error) {
                        confirm("Couldn't Delete", String.valueOf(error.getMessage()), "OK", false, null);
                        return;
                    } finally {
                        if (access != null) access.safeClose();
                    }
                    selectedId = null;
                    reload();
                });
    }

    private void recreate() {
        LevelSummary summary = selected();
        if (summary == null || !summary.canRecreate()) return;
        LevelStorageSource.LevelStorageAccess access = null;
        try {
            access = minecraft.getLevelSource().createAccess(summary.getLevelId());
            Pair<LevelSettings, net.minecraft.client.gui.screens.worldselection.WorldCreationContext> data =
                    flows().recreateWorldData(access);
            LevelSettings settings = data.getFirst();
            var worldOptions = data.getSecond().options();
            minecraft.gui.setScreen(LarpCreateWorldScreen.prefilled(this,
                    settings.levelName(), Long.toString(worldOptions.seed()),
                    settings.difficultySettings().hardcore() ? GameType.SURVIVAL : settings.gameType(),
                    settings.difficultySettings().difficulty(),
                    settings.difficultySettings().hardcore(), settings.allowCommands(),
                    worldOptions.generateStructures(), worldOptions.generateBonusChest()));
        } catch (Exception error) {
            confirm("Couldn't Re-create", String.valueOf(error.getMessage()), "OK", false, null);
        } finally {
            if (access != null) access.safeClose();
        }
    }

    private WorldOpenFlows flows() {
        return new WorldOpenFlows(minecraft, minecraft.getLevelSource());
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        if (summaries == null) {
            dev.larp.client.render.Text.draw(graphics, "Loading worlds...",
                    margin, contentTop() + 20f * ui, textSize,
                    dev.larp.client.util.Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
        } else if (summaries.isEmpty()) {
            dev.larp.client.render.Text.draw(graphics, "No worlds yet. Create one below.",
                    margin, contentTop() + 20f * ui, textSize,
                    dev.larp.client.util.Colors.withAlpha(MenuStyle.TEXT_MUTED, shown));
        } else {
            updateActions();
        }
        drawList(graphics, mouseX, mouseY, delta, shown);
    }

    @Override
    public void removed() {
        icons.close();
    }
}
