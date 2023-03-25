package com.cavetale.bungee;

import com.cavetale.bungee.message.BungeePlayerKick;
import com.google.gson.Gson;
import com.winthier.connect.Connect;
import com.winthier.connect.ConnectHandler;
import com.winthier.connect.Message;
import com.winthier.connect.payload.OnlinePlayer;
import com.winthier.connect.payload.PlayerServerPayload;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public final class BungeeCavetale extends Plugin implements ConnectHandler, Listener, Runnable {
    private Connect connect;
    private LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private Properties connectProperties;
    private EventListener eventListener = new EventListener(this);
    private Set<UUID> joined = Collections.synchronizedSet(new HashSet<>());
    private Set<UUID> connected = Collections.synchronizedSet(new HashSet<>());
    Gson gson = new Gson();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerListener(this, eventListener);
        getProxy().getPluginManager().registerCommand(this, new Command("bcavetale", "admin", new String[0]) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    if (args.length == 0) return;
                    switch (args[0]) {
                    case "reload":
                        sender.sendMessage(TextComponent
                                           .fromLegacyText("Reloading BungeeCavetale config"));
                        loadConfigs();
                        break;
                    case "status":
                        sender.sendMessage(TextComponent.fromLegacyText("Clients"));
                        for (String remote: connect.listServers()) {
                            sender.sendMessage(TextComponent.fromLegacyText("- " + remote));
                        }
                    case "send":
                        if (args.length >= 4) {
                            StringBuilder sb = new StringBuilder(args[3]);
                            for (int i = 4; i < args.length; i += 1) {
                                sb.append(" ").append(args[i]);
                            }
                            String payload = sb.toString();
                            connect.send(args[1], args[2], payload);
                            sender.sendMessage(TextComponent
                                               .fromLegacyText("Sent to " + args[1] + ": " + args[2] + ": " + payload));
                        }
                    default:
                        break;
                    }
                }
            });
        loadConfigs();
        String connectName = connectProperties.getProperty("server-name", "bungee");
        this.connect = new Connect(connectName, this);
        ProxyServer.getInstance().getScheduler().runAsync(this, this.connect);
        getProxy().getScheduler().runAsync(this, this);
    }

    @Override
    public void run() {
        while (true) {
            try {
                mainLoop();
            } catch (InterruptedException ie) {
                // ignore
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void loadConfigs() {
        connectProperties = new Properties();
        try {
            File file = new File(getDataFolder(), "connect.properties");
            connectProperties.load(new FileInputStream(file));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
        }
    }

    private void mainLoop() throws Exception {
        Runnable task = tasks.poll(1, TimeUnit.SECONDS);
        if (task != null) task.run();
    }

    // Listener

    @EventHandler
    public void onServerKickEvent(ServerKickEvent event) {
        String from = event.getKickedFrom().getName();
        if (from.equals("void")) return;
        boolean fromHub = from.equals("hub");
        ServerInfo cancelServer = null;
        cancelServer = getProxy().getServerInfo(fromHub ? "void" : "hub");
        if (cancelServer == null) return;
        event.setCancelled(true);
        event.setCancelServer(cancelServer);
        event.getPlayer().sendMessage(event.getKickReasonComponent());
    }

    @EventHandler
    public void onLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        joined.add(uuid);
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!joined.remove(uuid)) return;
        connected.add(uuid);
        String name = event.getPlayer().getName();
        String server = event.getServer().getInfo().getName();
        PlayerServerPayload payload = new PlayerServerPayload(new OnlinePlayer(uuid, name, server), server);
        runTask(() -> {
                connect.broadcastAll("BUNGEE_PLAYER_JOIN", gson.toJson(payload));
                getLogger().info("TASK BUNGEE_PLAYER_JOIN " + name + " " + server);
            });
        getLogger().info("BUNGEE_PLAYER_JOIN " + name + " " + server);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!connected.remove(uuid)) return;
        String name = event.getPlayer().getName();
        if (event.getPlayer().getServer() == null) return;
        String server = event.getPlayer().getServer().getInfo().getName();
        PlayerServerPayload payload = new PlayerServerPayload(new OnlinePlayer(uuid, name, server), server);
        runTask(() -> {
                connect.broadcastAll("BUNGEE_PLAYER_QUIT", gson.toJson(payload));
                getLogger().info("TASK BUNGEE_PLAYER_QUIT " + name + " " + server);
            });
        getLogger().info("BUNGEE_PLAYER_QUIT " + name + " " + server);
    }

    public void runTask(Runnable task) {
        tasks.add(task);
    }

    public void broadcastAll(String channel, String payload) {
        runTask(() -> connect.broadcastAll(channel, payload));
    }

    // --- Connect Handler

    @Override
    public void handleRemoteConnect(String remote) { }

    @Override
    public void handleRemoteDisconnect(String remote) { }

    @Override
    public void handleMessage(Message message) {
        switch (message.getChannel()) {
        case "BUNGEE_PLAYER_KICK":
            try {
                BungeePlayerKick bungeePlayerKick = gson.fromJson(message.getPayload(), BungeePlayerKick.class);
                ProxiedPlayer player = getProxy().getPlayer(bungeePlayerKick.getPlayer().getUuid());
                if (player == null) return;
                getLogger().info("Bungee: Kicking player: " + player.getName());
                player.disconnect(bungeePlayerKick.parseMessage());
            } catch (RuntimeException re) {
                System.err.println("handleMessage BUNGEE_PLAYER_KICK: " + message);
                re.printStackTrace();
                return;
            }
            break;
        case "Bans":
            try {
                Ban ban = gson.fromJson(message.getPayload(), Ban.class);
                switch (ban.getType()) {
                case "BAN": case "KICK":
                    ProxiedPlayer player = getProxy().getPlayer(ban.getPlayer().getUuid());
                    if (player == null) return;
                    getLogger().info("Bans: Kicking player: " + player.getName());
                    player.disconnect(TextComponent.fromLegacyText(ban.getMessage()));
                    return;
                default: return;
                }
            } catch (RuntimeException re) {
                System.err.println("handleMessage: " + message);
                re.printStackTrace();
                return;
            }
        default: break;
        }
    }

    @Override
    public void handleRemoteCommand(OnlinePlayer sender, String server, String[] args) { }
}
