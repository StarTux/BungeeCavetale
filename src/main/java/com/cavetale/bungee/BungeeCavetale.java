package com.cavetale.bungee;

import com.winthier.connect.Client;
import com.winthier.connect.Connect;
import com.winthier.connect.ConnectHandler;
import com.winthier.connect.Message;
import com.winthier.connect.OnlinePlayer;
import com.winthier.connect.ServerConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ConnectedPlayer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
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
    private List<Command> remoteCommands = new ArrayList<>();

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new Command("bcavetale", "admin", new String[0]) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    if (args.length == 0) return;
                    switch(args[0]) {
                    case "reload":
                        sender.sendMessage("Reloading BungeeCavetale config");
                        loadConfigs();
                        break;
                    case "status":
                        sender.sendMessage("Clients");
                        for (Client client: connect.getClients()) {
                            sender.sendMessage(client.getName() + " " + client.getStatus());
                        }
                        sender.sendMessage("Servers");
                        for (ServerConnection server: connect.getServer().getConnections()) {
                            sender.sendMessage(server.getName() + " " + server.getStatus());
                        }
                    }
                }
            });
        connect = new Connect("bungee", new File(getDataFolder(), "servers.txt"), this);
        connect.start();
        getProxy().getScheduler().runAsync(this, this);
        loadConfigs();
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

    void onRemoteCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer)sender;
        String[] nargs;
        switch (label) {
        case "game": case "games":
            nargs = new String[args.length + 1];
            nargs[0] = "game";
            for (int i = 0; i < args.length; i += 1) {
                nargs[i + 1] = args[i];
            }
            break;
        default:
            nargs = new String[args.length + 2];
            nargs[0] = "game";
            nargs[1] = label;
            for (int i = 0; i < args.length; i += 1) {
                nargs[i + 2] = args[i];
            }
        }
        connect.sendRemoteCommand(new OnlinePlayer(player.getUniqueId(), player.getName()), "daemon", nargs);
    }

    void loadConfigs() {
        try {
            for (Command command: remoteCommands) getProxy().getPluginManager().unregisterCommand(command);
            remoteCommands.clear();
            BufferedReader br = new BufferedReader(new FileReader(new File(getDataFolder(), "remote_commands.txt")));
            String line;
            while (null != (line = br.readLine())) {
                if (line.startsWith("#")) continue;
                final String label = line;
                remoteCommands.add(new Command(line, "", new String[0]) {
                        @Override public void execute(final CommandSender sender, final String[] args) {
                            onRemoteCommand(sender, label, args);
                        }
                    });
            }
            for (Command command: remoteCommands) getProxy().getPluginManager().registerCommand(this, command);
        } catch (Exception e) {
            e.printStackTrace();
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
        if (targetName.startsWith("game")) return;
        if (targetName.equals("hub")) return;
        origins.put(event.getPlayer().getUniqueId(), targetName);
    }

    @EventHandler
    public void onServerKickEvent(ServerKickEvent event) {
        String from = event.getKickedFrom().getName();
        if (from.equals("hub")) return;
        String origin = origins.get(event.getPlayer().getUniqueId());
        ServerInfo cancelServer = null;
        if (from.startsWith("game")) {
            if (origin != null && !origin.equals(from)) cancelServer = getProxy().getServerInfo(origin);
            if (cancelServer == null) cancelServer = getProxy().getServerInfo("hub");
        } else {
            cancelServer = getProxy().getServerInfo("hub");
        }
        if (cancelServer == null) return;
        event.setCancelled(true);
        event.setCancelServer(cancelServer);
    }

    // Connect Handler

    @Override
    public void runThread(Runnable task) {
        ProxyServer.getInstance().getScheduler().runAsync(this, task);
    }

    @Override
    public void handleClientConnect(Client client) { }

    @Override
    public void handleClientDisconnect(Client client) { }

    @Override
    public void handleServerConnect(ServerConnection connection) { }

    @Override
    public void handleServerDisconnect(ServerConnection connection) { }

    @Override
    public void handleMessage(Message message) { }

    @Override
    public void handleRemoteCommand(OnlinePlayer sender, String server, String[] args) { }
}
