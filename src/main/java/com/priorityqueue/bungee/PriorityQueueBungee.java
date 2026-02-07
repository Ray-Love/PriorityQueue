package com.priorityqueue.bungee;

import com.priorityqueue.core.Config;
import com.priorityqueue.core.Database;
import com.priorityqueue.core.QueueManager;
import com.priorityqueue.core.QueuePlayer;
import lombok.extern.slf4j.Slf4j;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class PriorityQueueBungee extends Plugin {

    private Config config;
    private Database database;
    private QueueManager queueManager;
    private final Map<java.util.UUID, QueuePlayer> waitingPlayers;
    private ScheduledExecutorService positionUpdateScheduler;

    public PriorityQueueBungee() {
        this.waitingPlayers = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {
        try {
            // Ensure data folder exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Copy default config if not exists
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    }
                }
            }

            // Load config
            config = Config.load(configFile);

            // Initialize database
            database = new Database(getDataFolder(), config);

            // Initialize queue manager
            queueManager = new QueueManager(
                    config.getMaxSize(),
                    config.getSlotsPerInterval(),
                    config.getInterval(),
                    this::processBatch
            );

            // Start queue
            queueManager.start();

            // Start position update scheduler
            startPositionUpdateScheduler();

            // Register commands
            getProxy().getPluginManager().registerCommand(this, new QueueCommand());

            // Register listeners
            getProxy().getPluginManager().registerListener(this, new QueueListener());

            log.info("PriorityQueue loaded successfully!");
        } catch (Exception e) {
            log.error("Failed to load PriorityQueue", e);
        }
    }

    @Override
    public void onDisable() {
        stopPositionUpdateScheduler();
        if (queueManager != null) {
            queueManager.stop();
        }
        if (database != null) {
            database.close();
        }
        log.info("PriorityQueue disabled!");
    }

    private void processBatch(List<QueuePlayer> players) {
        for (QueuePlayer player : players) {
            ProxiedPlayer proxyPlayer = getProxy().getPlayer(player.getUuid());
            if (proxyPlayer != null && proxyPlayer.isConnected()) {
                ServerInfo targetServer = getProxy().getServerInfo(config.getTargetServer());
                if (targetServer != null) {
                    sendMessage(proxyPlayer, config.getMessageQueueComplete());
                    proxyPlayer.connect(targetServer);
                }
            }
        }
    }

    private int getPlayerPriority(ProxiedPlayer player) {
        // Check permissions in order
        if (player.hasPermission("priorityqueue.admin")) {
            return config.getPriorityLevels().getOrDefault("admin", 3);
        }
        if (player.hasPermission("priorityqueue.mvp")) {
            return config.getPriorityLevels().getOrDefault("mvp", 2);
        }
        if (player.hasPermission("priorityqueue.vip")) {
            return config.getPriorityLevels().getOrDefault("vip", 1);
        }

        // Check database cache
        Integer cachedPriority = database.getPriority(player.getUniqueId().toString());
        if (cachedPriority != null) {
            return cachedPriority;
        }

        return config.getDefaultPriority();
    }

    private String formatMessage(String message, Map<String, String> placeholders) {
        String formatted = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    private void sendMessage(ProxiedPlayer player, String message) {
        sendMessage(player, message, Map.of());
    }

    private void sendMessage(ProxiedPlayer player, String message, Map<String, String> placeholders) {
        player.sendMessage(formatMessage(message, placeholders));
    }

    private void startPositionUpdateScheduler() {
        if (positionUpdateScheduler != null && !positionUpdateScheduler.isShutdown()) {
            return;
        }
        positionUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
        positionUpdateScheduler.scheduleAtFixedRate(() -> {
            try {
                for (Map.Entry<java.util.UUID, QueuePlayer> entry : waitingPlayers.entrySet()) {
                    ProxiedPlayer player = getProxy().getPlayer(entry.getKey());
                    if (player != null && player.isConnected()) {
                        int position = queueManager.getQueue().getPlayerPosition(entry.getKey());
                        int total = queueManager.getQueue().getQueueSize();
                        if (position > 0) {
                            sendMessage(player, config.getMessageQueuePositionUpdate(),
                                    Map.of("position", String.valueOf(position), "total", String.valueOf(total)));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error updating queue positions", e);
            }
        }, config.getPositionUpdateInterval(), config.getPositionUpdateInterval(), TimeUnit.SECONDS);
    }

    private void stopPositionUpdateScheduler() {
        if (positionUpdateScheduler != null && !positionUpdateScheduler.isShutdown()) {
            positionUpdateScheduler.shutdown();
            try {
                if (!positionUpdateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    positionUpdateScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                positionUpdateScheduler.shutdownNow();
            }
        }
    }

    private class QueueListener implements Listener {
        @EventHandler(priority = net.md_5.bungee.event.EventPriority.LOWEST)
        public void onServerConnect(ServerConnectEvent event) {
            if (!(event.getPlayer() instanceof ProxiedPlayer)) {
                return;
            }

            ProxiedPlayer player = event.getPlayer();
            ServerInfo targetServer = event.getTarget();

            // Check if connecting to target server
            if (!targetServer.getName().equalsIgnoreCase(config.getTargetServer())) {
                return;
            }

            // Check if player has bypass permission
            if (player.hasPermission("priorityqueue.bypass")) {
                return;
            }

            // Check if already in queue
            if (queueManager.getQueue().isInQueue(player.getUniqueId())) {
                event.setCancelled(true);
                int position = queueManager.getQueue().getPlayerPosition(player.getUniqueId());
                sendMessage(player, config.getMessageQueuePosition(),
                        Map.of("position", String.valueOf(position)));
                return;
            }

            // Try to add to queue
            int priority = getPlayerPriority(player);
            QueuePlayer queuePlayer = new QueuePlayer(
                    player.getUniqueId(),
                    player.getName(),
                    priority,
                    System.currentTimeMillis(),
                    -1
            );

            if (queueManager.getQueue().addToQueue(queuePlayer)) {
                event.setCancelled(true);
                int position = queuePlayer.getPosition();
                sendMessage(player, config.getMessageQueueJoin(),
                        Map.of("position", String.valueOf(position)));
            } else {
                event.setCancelled(true);
                sendMessage(player, config.getMessageQueueFull(), Map.of());
            }
        }
    }

    private class QueueCommand extends Command {
        public QueueCommand() {
            super("queue", "priorityqueue.use", "pq", "q");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return;
            }

            ProxiedPlayer player = (ProxiedPlayer) sender;

            if (args.length == 0) {
                // Show queue position
                if (queueManager.getQueue().isInQueue(player.getUniqueId())) {
                    int position = queueManager.getQueue().getPlayerPosition(player.getUniqueId());
                    sendMessage(player, config.getMessageQueuePosition(),
                            Map.of("position", String.valueOf(position)));
                } else {
                    sendMessage(player, config.getMessageQueueLeft(), Map.of());
                }
            } else if (args[0].equalsIgnoreCase("leave")) {
                // Leave queue
                if (queueManager.getQueue().removeFromQueue(player.getUniqueId()) != null) {
                    sendMessage(player, config.getMessageQueueLeft(), Map.of());
            } else {
                sendMessage(player, ChatColor.RED + "You are not in the queue.");
            }
        } else if (args[0].equalsIgnoreCase("info")) {
            // Show queue info
            String info = formatMessage(config.getMessageQueueInfo(), Map.of(
                    "size", String.valueOf(queueManager.getQueue().getQueueSize()),
                    "slots", String.valueOf(config.getSlotsPerInterval()),
                    "interval", String.valueOf(config.getInterval())
            ));
            player.sendMessage(info);
        } else if (args[0].equalsIgnoreCase("reload")) {
            // Reload config (admin only)
            if (!player.hasPermission("priorityqueue.admin")) {
                sendMessage(player, config.getMessageNoPermission());
                return;
            }

            try {
                File configFile = new File(getDataFolder(), "config.yml");
                config = Config.load(configFile);
                stopPositionUpdateScheduler();
                startPositionUpdateScheduler();
                queueManager.restart();
                sendMessage(player, config.getMessageConfigReloaded());
            } catch (Exception e) {
                log.error("Failed to reload config", e);
                player.sendMessage(ChatColor.RED + "Failed to reload config: " + e.getMessage());
            }
        } else {
            sendMessage(player, ChatColor.YELLOW + "Usage: /queue [leave|info|reload]");
        }
        }
    }
}
