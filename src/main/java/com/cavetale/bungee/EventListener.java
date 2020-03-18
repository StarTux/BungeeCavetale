package com.cavetale.bungee;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ClientConnectEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
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

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            map.put(key, "");
        } else if (value instanceof String) {
            map.put(key, value);
        } else if (value instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) value;
            Map<String, String> map2 = new LinkedHashMap<>();
            map2.put("uuid", player.getUniqueId().toString());
            map2.put("name", player.getName());
            map2.put("locale", str(player.getLocale()));
            map2.put("server", player.getServer().getInfo().getName());
            map.put(key, map2);
        } else if (value instanceof PendingConnection) {
            PendingConnection con = (PendingConnection) value;
            Map<String, String> map2 = new LinkedHashMap<>();
            map2.put("uuid", con.getUniqueId().toString());
            map2.put("name", con.getName());
            map2.put("socketAddress", con.getAddress().toString());
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
    public void onClientConnect(ClientConnectEvent event) {
        Map<String, Object> map = map(event);
        put(map, "type", event);
        put(map, "socketAddress", event.getSocketAddress());
        put(map, "listener", event.getListener());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerhandshake(PlayerHandshakeEvent event) {
        Map<String, Object> map = map(event);
        put(map, "connection", event.getConnection());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnected(ServerConnectedEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        put(map, "server", event.getServer().getInfo().getName());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnect(ServerConnectEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        put(map, "server", event.getTarget().getName());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(ServerDisconnectEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        put(map, "server", event.getTarget().getName());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerKick(ServerKickEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        put(map, "server", event.getKickedFrom().getName());
        put(map, "cause", event.getCause());
        put(map, "reason", event.getKickReason());
        put(map, "state", event.getState());
        plugin.broadcastAll(CHANNEL, map);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerSwitch(ServerSwitchEvent event) {
        Map<String, Object> map = map(event);
        put(map, "player", event.getPlayer());
        plugin.broadcastAll(CHANNEL, map);
    }
}
