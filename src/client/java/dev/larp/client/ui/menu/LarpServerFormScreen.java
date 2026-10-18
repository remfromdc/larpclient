package dev.larp.client.ui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Add / edit / direct-join form. Add and edit persist through
 * {@link LarpServerListScreen#onFormSaved()}; direct join connects without
 * touching the saved list.
 */
public class LarpServerFormScreen extends LarpListScreen {
    public enum Mode { ADD, EDIT, DIRECT }

    private final Mode mode;
    private final ServerData editing;
    private final FieldState nameField;
    private final FieldState addressField;

    private LarpServerFormScreen(LarpServerListScreen parent, Mode mode, ServerData editing,
                                 String name, String address) {
        super(switch (mode) {
            case ADD -> "Add Server";
            case EDIT -> "Edit Server";
            case DIRECT -> "Direct Join";
        }, switch (mode) {
            case ADD -> "Save a server to your list.";
            case EDIT -> "Update the saved entry.";
            case DIRECT -> "Connect once without saving.";
        }, parent);
        this.mode = mode;
        this.editing = editing;
        this.nameField = new FieldState(name, 64, MenuField.freeText());
        this.addressField = new FieldState(address, 64, MenuField.noSpaces());
    }

    public static LarpServerFormScreen add(LarpServerListScreen parent) {
        return new LarpServerFormScreen(parent, Mode.ADD, null, "", "");
    }

    public static LarpServerFormScreen edit(LarpServerListScreen parent, ServerData data) {
        return new LarpServerFormScreen(parent, Mode.EDIT, data, data.name, data.ip);
    }

    public static LarpServerFormScreen direct(LarpServerListScreen parent) {
        return new LarpServerFormScreen(parent, Mode.DIRECT, null, "", "");
    }

    @Override
    protected void buildRows() {
        int index = 0;
        if (mode != Mode.DIRECT) {
            MenuField name = new MenuField(0, 0, 1, 1, "Server Name", textSize, stagger(index++),
                    nameField, "My Server");
            name.setHeight(Math.round(fieldHeight));
            row(name);
        }
        MenuField address = new MenuField(0, 0, 1, 1, "Server Address", textSize, stagger(index++),
                addressField, "play.example.net");
        address.setHeight(Math.round(fieldHeight));
        row(address);
        String primary = switch (mode) {
            case ADD -> "Add Server";
            case EDIT -> "Save Changes";
            case DIRECT -> "Join Server";
        };
        row(new MenuButton(0, 0, 1, 1, primary, textSize, stagger(index), this::submit));
    }

    private void submit() {
        String address = addressField.value().trim();
        if (address.isEmpty()) {
            confirm("Missing Address", "Enter the server address first.", "OK", false, null);
            return;
        }
        LarpServerListScreen list = (LarpServerListScreen) parent;
        switch (mode) {
            case ADD -> {
                String name = nameField.value().trim();
                ServerData data = new ServerData(name.isEmpty() ? address : name, address,
                        ServerData.Type.OTHER);
                // The saved entry starts fresh; ping fills in details.
                list.addServer(data);
                list.onFormSaved();
                minecraft.gui.setScreen(list);
            }
            case EDIT -> {
                String name = nameField.value().trim();
                editing.name = name.isEmpty() ? editing.ip : name;
                editing.ip = address;
                list.onFormSaved();
                minecraft.gui.setScreen(list);
            }
            case DIRECT -> {
                ServerData data = new ServerData("Minecraft Server", address, ServerData.Type.OTHER);
                minecraft.gui.setScreen(list);
                list.join(data);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        drawList(graphics, mouseX, mouseY, delta, shown);
    }
}
