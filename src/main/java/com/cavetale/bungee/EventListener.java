package com.cavetale.bungee;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final BungeeCavetale plugin;
    static final String CHANNEL = "bungee";
    private Gson gson = new Gson();
    private boolean debug = false;

    private static void str(Map<String, Object> map, String key, Object o) {
        if (o == null) return;
        map.put(key, o.toString());
    }

    private static void serverInfo(Map<String, Object> map, Server server) {
        if (server == null) return;
        serverInfo(map, server.getInfo());
    }

    private static void serverInfo(Map<String, Object> map, ServerInfo info) {
        if (info == null) return;
        str(map, "server", info.getName());
    }

    private void auto(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            map.put(key, "");
        } else if (value instanceof String) {
            map.put(key, value);
        } else if (value instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) value;
            Map<String, Object> map2 = new LinkedHashMap<>();
            str(map2, "uuid", player.getUniqueId());
            str(map2, "name", player.getName());
            serverInfo(map2, player.getServer());
            map.put(key, map2);
        } else if (value instanceof PendingConnection) {
            PendingConnection con = (PendingConnection) value;
            Map<String, Object> map2 = new LinkedHashMap<>();
            str(map2, "uuid", con.getUniqueId());
            str(map2, "name", con.getName());
            map.put(key, map2);
        } else {
            map.put(key, value.toString());
        }
    }

    private Map<String, Object> map(Event event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", event.getClass().getSimpleName());
        return map;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnected(ServerConnectedEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        serverInfo(map, event.getServer());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnect(ServerConnectEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        serverInfo(map, event.getTarget());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(ServerDisconnectEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        serverInfo(map, event.getTarget());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerKick(ServerKickEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        serverInfo(map, event.getKickedFrom());
        auto(map, "cause", event.getCause());
        auto(map, "reason", BaseComponent.toLegacyText(event.getKickReasonComponent()));
        auto(map, "state", event.getState());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerSwitch(ServerSwitchEvent event) {
        Map<String, Object> map = map(event);
        auto(map, "player", event.getPlayer());
        if (debug) plugin.getLogger().info(gson.toJson(map));
        plugin.broadcastAll(CHANNEL, gson.toJson(map));
    }
}
