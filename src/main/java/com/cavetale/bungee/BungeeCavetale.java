package com.cavetale.bungee;

import com.google.gson.Gson;
import com.winthier.connect.Connect;
import com.winthier.connect.ConnectHandler;
import com.winthier.connect.Message;
import com.winthier.connect.OnlinePlayer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public final class BungeeCavetale extends Plugin implements ConnectHandler, Listener, Runnable {
    private final Map<UUID, String> origins = new HashMap<>();
    private Connect connect;
    private LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final List<Command> serverCommands = new ArrayList<>();
    private Properties connectProperties;
    Gson gson = new Gson();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager()
            .registerCommand(this, new Command("bcavetale", "admin", new String[0]) {
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
                            Object obj = gson.fromJson(sb.toString(), Object.class);
                            connect.send(args[1], args[2], obj);
                            sender.sendMessage("Sent to " + args[1] + ": " + args[2] + ": " + obj);
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

    void onServerCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) sender;
        ServerInfo server = getProxy().getServerInfo(label);
        if (server == null) return;
        player.connect(server);
    }

    void loadConfigs() {
        String line;
        try {
            // Server commands
            serverCommands.clear();
            for (Command command: serverCommands) {
                getProxy().getPluginManager().unregisterCommand(command);
            }
            File file = new File(getDataFolder(), "server_commands.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                while (null != (line = br.readLine())) {
                    if (line.startsWith("#")) continue;
                    final String label = line;
                    serverCommands.add(new Command(label, "", new String[0]) {
                            @Override public void execute(CommandSender sender, String[] args) {
                                onServerCommand(sender, label, args);
                            }
                        });
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            for (Command command: serverCommands) {
                System.out.println("[Cavetale] Registering server command \""
                                   + command.getName() + "\"...");
                getProxy().getPluginManager().registerCommand(this, command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    void sendPlayerList() {
        Map<String, Object> serverMap = new HashMap<>();
        for (ServerInfo serverInfo: getProxy().getServers().values()) {
            Map<String, Object> playerMap = new HashMap<>();
            serverMap.put(serverInfo.getName(), playerMap);
            for (ProxiedPlayer player: serverInfo.getPlayers()) {
                playerMap.put(player.getUniqueId().toString(), player.getName());
            }
        }
        connect.broadcast("BUNGEE_PLAYER_LIST", serverMap);
    }

    // Listener

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent event) {
        String targetName = event.getTarget().getName();
        if (targetName.equals("hub")) return;
        origins.put(event.getPlayer().getUniqueId(), targetName);
    }

    @EventHandler
    public void onServerKickEvent(ServerKickEvent event) {
        String from = event.getKickedFrom().getName();
        if (from.equals("hub")) return;
        String origin = origins.get(event.getPlayer().getUniqueId());
        ServerInfo cancelServer = null;
        cancelServer = getProxy().getServerInfo("hub");
        if (cancelServer == null) return;
        event.setCancelled(true);
        event.setCancelServer(cancelServer);
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        UUID uuid = event.getConnection().getUniqueId();
        String name = event.getConnection().getName();
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", uuid.toString());
        map.put("name", name);
        tasks.add(() -> connect.broadcastAll("BUNGEE_PLAYER_JOIN", map));
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", event.getPlayer().getUniqueId().toString());
        map.put("name", event.getPlayer().getName());
        tasks.add(() -> connect.broadcastAll("BUNGEE_PLAYER_QUIT", map));
    }

    // --- Connect Handler

    @Override
    public void handleRemoteConnect(String remote) { }

    @Override
    public void handleRemoteDisconnect(String remote) { }

    @Override
    public void handleMessage(Message message) { }

    @Override
    public void handleRemoteCommand(OnlinePlayer sender, String server, String[] args) { }
}
