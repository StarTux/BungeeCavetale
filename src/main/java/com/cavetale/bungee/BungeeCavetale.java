package com.cavetale.bungee;

import com.winthier.connect.Client;
import com.winthier.connect.Connect;
import com.winthier.connect.ConnectHandler;
import com.winthier.connect.Message;
import com.winthier.connect.OnlinePlayer;
import com.winthier.connect.ServerConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
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
    private final List<Command> remoteCommands = new ArrayList<>();
    private final List<Command> serverCommands = new ArrayList<>();
    private Properties bansProperties, playerCacheProperties;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new Command("bcavetale", "admin", new String[0]) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    if (args.length == 0) return;
                    switch (args[0]) {
                    case "reload":
                        sender.sendMessage(TextComponent.fromLegacyText("Reloading BungeeCavetale config"));
                        loadConfigs();
                        break;
                    case "status":
                        sender.sendMessage(TextComponent.fromLegacyText("Clients"));
                        for (Client client: connect.getClients()) {
                            sender.sendMessage(TextComponent.fromLegacyText(client.getName() + " " + client.getStatus()));
                        }
                        sender.sendMessage(TextComponent.fromLegacyText("Servers"));
                        for (ServerConnection server: connect.getServer().getConnections()) {
                            sender.sendMessage(TextComponent.fromLegacyText(server.getName() + " " + server.getStatus()));
                        }
                    default:
                        break;
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

    void onServerCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer)sender;
        ServerInfo server = getProxy().getServerInfo(label);
        if (server == null) return;
        player.connect(server);
    }

    void loadConfigs() {
        try {
            for (Command command: remoteCommands) getProxy().getPluginManager().unregisterCommand(command);
            remoteCommands.clear();
            BufferedReader br;
            String line;
            // Remote commands
            br = new BufferedReader(new FileReader(new File(getDataFolder(), "remote_commands.txt")));
            while (null != (line = br.readLine())) {
                if (line.startsWith("#")) continue;
                final String label = line;
                remoteCommands.add(new Command(line, "", new String[0]) {
                        @Override public void execute(final CommandSender sender, final String[] args) {
                            onRemoteCommand(sender, label, args);
                        }
                    });
            }
            for (Command command: remoteCommands) {
                System.out.println("[Cavetale] Registering remote command \"" + command.getName() + "\"...");
                getProxy().getPluginManager().registerCommand(this, command);
            }
            // Server commands
            serverCommands.clear();
            for (Command command: serverCommands) getProxy().getPluginManager().unregisterCommand(command);
            br = new BufferedReader(new FileReader(new File(getDataFolder(), "server_commands.txt")));
            while (null != (line = br.readLine())) {
                if (line.startsWith("#")) continue;
                final String label = line;
                serverCommands.add(new Command(label, "", new String[0]) {
                        @Override public void execute(CommandSender sender, String[] args) {
                            onServerCommand(sender, label, args);
                        }
                    });
            }
            for (Command command: serverCommands) {
                System.out.println("[Cavetale] Registering server command \"" + command.getName() + "\"...");
                getProxy().getPluginManager().registerCommand(this, command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        bansProperties = new Properties();
        try {
            bansProperties.load(new FileInputStream(new File(getDataFolder(), "bans.properties")));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
        }
        playerCacheProperties = new Properties();
        try {
            playerCacheProperties.load(new FileInputStream(new File(getDataFolder(), "playercache.properties")));
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

    @Value
    static final class Ban {
        String type;
        String admin;
        String reason;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        UUID uuid = event.getConnection().getUniqueId();
        String name = event.getConnection().getName();
        Ban ban = getActiveBan(uuid);
        if (ban != null) {
            String admin = ban.admin;
            if (admin != null) {
                UUID adminId;
                try {
                    adminId = UUID.fromString(admin);
                    PlayerCache cache = findPlayerCache(adminId, null);
                    admin = cache.name;
                } catch (IllegalArgumentException iae) {
                    iae.printStackTrace();
                    admin = null;
                }
            }
            String reason = ban.reason;
            event.setCancelled(true);
            ComponentBuilder builder = new ComponentBuilder("You are banned!").color(ChatColor.RED);
            if (admin != null) {
                builder.append("\nBy: ").color(ChatColor.WHITE)
                    .append(admin).color(ChatColor.GRAY);
            }
            if (reason != null) {
                builder.append("\nReason: ").color(ChatColor.WHITE)
                    .append(reason).color(ChatColor.GRAY);
            }
            builder.append("\nPlease appeal your ban at ").color(ChatColor.WHITE);
            builder.append("https://cavetale.com").color(ChatColor.BLUE);
            event.setCancelReason(builder.create());
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", uuid);
        map.put("name", name);
        connect.broadcast("BUNGEE_PLAYER_LOGIN", map);
    }

    Connection connectionFromProperties(Properties properties) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return null;
        }
        String host = properties.getProperty("host", "localhost");
        String port = properties.getProperty("port", "3306");
        String database = properties.getProperty("database", "Bans");
        String user = properties.getProperty("user", "user");
        String password = properties.getProperty("password", "password");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        Connection connection;
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            return null;
        }
        return connection;
    }

    Ban getActiveBan(UUID player) {
        Connection connection = connectionFromProperties(bansProperties);
        if (connection == null) return null;
        String table = bansProperties.getProperty("table", "bans");
        try {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM `" + table + "` WHERE `player` = '" + player.toString() + "' AND `type` = 'ban' AND (`expiry` IS NULL OR `expiry` < NOW())");
            if (result.next()) {
                return new Ban(result.getString("type"), result.getString("admin"), result.getString("reason"));
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return null;
    }

    @Value
    static final class PlayerCache {
        private UUID uuid;
        private String name;
    }

    PlayerCache findPlayerCache(UUID uuid, String name) {
        Connection connection = connectionFromProperties(playerCacheProperties);
        if (connection == null) return null;
        String table = playerCacheProperties.getProperty("table", "bans");
        try {
            Statement statement = connection.createStatement();
            ResultSet result;
            if (uuid != null) {
                result = statement.executeQuery("SELECT * FROM `" + table + "` WHERE `uuid` = '" + uuid.toString() + "'");
            } else {
                result = statement.executeQuery("SELECT * FROM `" + table + "` WHERE `name` = '" + name + "'");
            }
            if (result.next()) {
                try {
                    uuid = UUID.fromString(result.getString("uuid"));
                } catch (IllegalArgumentException iae) {
                    iae.printStackTrace();
                    return null;
                }
                name = result.getString("name");
                return new PlayerCache(uuid, name);
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
        return null;
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
