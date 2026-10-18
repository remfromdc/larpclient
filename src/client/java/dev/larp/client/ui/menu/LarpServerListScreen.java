package dev.larp.client.ui.menu;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Multiplayer server list with live pinging, LAN discovery, Realms entry
 * and the full join/add/edit/delete/direct-join flow in Larp style.
 */
public class LarpServerListScreen extends LarpListScreen {
    private ServerList servers;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final LanServerDetection.LanServerList lanList = new LanServerDetection.LanServerList();
    private LanServerDetection.LanServerDetector lanDetector;
    private final List<ServerData> lanServers = new ArrayList<>();
    private final IconCache icons = new IconCache();

    private String selectedKey;
    private MenuButton joinButton;
    private MenuButton editButton;
    private MenuButton deleteButton;

    public LarpServerListScreen(Screen parent) {
        super("Multiplayer", "Join a server, or add your own.", parent);
    }

    private static String keyOf(ServerData data) {
        return (data.isLan() ? "lan:" : "srv:") + data.ip + "\0" + data.name;
    }

    private ServerData selected(List<ServerData> all) {
        if (selectedKey == null) return null;
        for (ServerData data : all) {
            if (keyOf(data).equals(selectedKey)) return data;
        }
        return null;
    }

    private List<ServerData> allServers() {
        List<ServerData> all = new ArrayList<>(lanServers);
        if (servers != null) {
            for (int i = 0; i < servers.size(); i++) all.add(servers.get(i));
        }
        return all;
    }

    @Override
    protected void buildRows() {
        if (servers == null) {
            servers = new ServerList(minecraft);
            try {
                servers.load();
            } catch (Exception ignored) {
                // Corrupt server list file: start empty, saving recreates it.
            }
            pingAll();
        }
        if (lanDetector == null) {
            try {
                lanDetector = new LanServerDetection.LanServerDetector(lanList);
                lanDetector.start();
            } catch (Exception ignored) {
                // No multicast (or no network): LAN discovery stays off.
                lanDetector = null;
            }
        }
        drainLan();

        MenuButton realms = new MenuButton(0, 0, 1, 1, "Minecraft Realms", textSize, stagger(0),
                () -> minecraft.gui.setScreen(new RealmsMainScreen(this)));
        realms.setValue("Mojang servers");
        row(realms);

        List<ServerData> all = allServers();
        int index = 1;
        for (ServerData data : all) {
            ServerRow row = new ServerRow(data, icons, textSize, stagger(index++),
                    minecraft.options.hideServerAddress,
                    () -> select(data), () -> join(data));
            row.setSelected(keyOf(data).equals(selectedKey));
            // Name + MOTD/address line beside the icon.
            row.setHeight(Math.round(cardHeight));
            row(row);
        }
        if (selectedKey == null && !all.isEmpty()) {
            selectedKey = keyOf(all.get(0));
            refreshSelection();
        }

        int footerStagger = stagger(index);
        footer(joinButton = actionButton("Join", footerStagger, () -> join(selected(allServers()))));
        footer(actionButton("Direct", footerStagger, () -> minecraft.gui.setScreen(
                LarpServerFormScreen.direct(this))));
        footer(actionButton("Add", footerStagger, () -> minecraft.gui.setScreen(
                LarpServerFormScreen.add(this))));
        footer(editButton = actionButton("Edit", footerStagger, () -> edit(selected(allServers()))));
        footer(deleteButton = actionButton("Delete", footerStagger, () -> askDelete(selected(allServers()))));
        footer(actionButton("Refresh", footerStagger, this::refresh));
        updateActions();
    }

    private MenuButton actionButton(String label, int delay, Runnable action) {
        return new MenuButton(0, 0, 1, 1, label, textSize * 0.9f, delay, action);
    }

    private void select(ServerData data) {
        selectedKey = keyOf(data);
        refreshSelection();
    }

    private void refreshSelection() {
        for (MenuWidget widget : rows) {
            if (widget instanceof ServerRow row) {
                row.setSelected(keyOf(row.data()).equals(selectedKey));
            }
        }
        updateActions();
    }

    private void updateActions() {
        ServerData current = selected(allServers());
        boolean saved = current != null && !current.isLan();
        if (joinButton != null) joinButton.setEnabled(current != null);
        if (editButton != null) editButton.setEnabled(saved);
        if (deleteButton != null) deleteButton.setEnabled(saved);
    }

    // ------------------------------------------------------------------
    // Ping + LAN
    // ------------------------------------------------------------------

    private EventLoopGroupHolder eventLoops() {
        return EventLoopGroupHolder.remote(minecraft.options.useNativeTransport());
    }

    private void pingAll() {
        if (servers == null) return;
        for (int i = 0; i < servers.size(); i++) {
            ServerData data = servers.get(i);
            data.setState(ServerData.State.PINGING);
            try {
                pinger.pingServer(data, () -> { }, () -> { }, eventLoops());
            } catch (Exception ignored) {
                data.setState(ServerData.State.UNREACHABLE);
            }
        }
    }

    private void drainLan() {
        List<net.minecraft.client.server.LanServer> fresh = lanList.takeDirtyServers();
        // 26.2 returns null (not an empty list) when nothing new arrived.
        if (fresh == null || fresh.isEmpty()) return;
        for (net.minecraft.client.server.LanServer lan : fresh) {
            boolean known = false;
            for (ServerData existing : lanServers) {
                if (existing.ip.equals(lan.getAddress())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                lanServers.add(new ServerData(lan.getMotd(), lan.getAddress(), ServerData.Type.LAN));
            }
        }
    }

    private void refresh() {
        icons.close();
        pinger.removeAll();
        try {
            servers.load();
        } catch (Exception ignored) {
        }
        selectedKey = null;
        pingAll();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
        int before = lanServers.size();
        drainLan();
        if (lanServers.size() != before) rebuildWidgets();
    }

    @Override
    public void removed() {
        pinger.removeAll();
        icons.close();
        if (lanDetector != null) {
            lanDetector.interrupt();
            lanDetector = null;
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Connects to a server (saved or LAN). Package-visible for the form screen. */
    void join(ServerData data) {
        if (data == null) return;
        String address = data.ip;
        if (!ServerAddress.isValidAddress(address)) {
            confirm("Bad Address", "\"" + address + "\" is not a valid server address.", "OK", false, null);
            return;
        }
        try {
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address), data,
                    false, new TransferState(Map.of(), Map.of(), false));
        } catch (RuntimeException error) {
            confirm("Couldn't Connect", String.valueOf(error.getMessage()), "OK", false, null);
        }
    }

    private void edit(ServerData data) {
        if (data == null || data.isLan()) return;
        minecraft.gui.setScreen(LarpServerFormScreen.edit(this, data));
    }

    private void askDelete(ServerData data) {
        if (data == null || data.isLan()) return;
        confirm("Delete Server?",
                "\"" + data.name + "\" will be removed from your list.", "Delete", true, () -> {
                    servers.remove(data);
                    servers.save();
                    selectedKey = null;
                    rebuildWidgets();
                });
    }

    /** Adds a new entry; called by the form screen. Package-visible. */
    void addServer(ServerData data) {
        servers.add(data, false);
    }

    /** Called by the form screen after add/edit. Package-visible. */
    void onFormSaved() {
        servers.save();
        pingAll();
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float shown = reveal();
        if (shown <= 0f) return;
        drawHeader(graphics, shown);
        updateActions();
        drawList(graphics, mouseX, mouseY, delta, shown);
    }
}
