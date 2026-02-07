package com.priorityqueue.velocity;

import com.google.inject.Inject;
import com.priorityqueue.core.Config;
import com.priorityqueue.core.Database;
import com.priorityqueue.core.QueueManager;
import com.priorityqueue.core.QueuePlayer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Plugin(id = "priorityqueue", name = "PriorityQueue", version = "1.0.0")
public class PriorityQueueVelocity {

    private final ProxyServer proxy;
    private final Path dataFolder;
    private final Logger logger;

    private Config config;
    private Database database;
    private QueueManager queueManager;
    private final Map<UUID, QueuePlayer> waitingPlayers;
    private ScheduledExecutorService positionUpdateScheduler;

    @Inject
    public PriorityQueueVelocity(ProxyServer proxy, @DataDirectory Path dataFolder, Logger logger) {
        this.proxy = proxy;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.waitingPlayers = new ConcurrentHashMap<>();
    }

    @Subscribe
    public void onProxyInitialization(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        try {
            // Ensure data folder exists
            File dataFolderFile = dataFolder.toFile();
            if (!dataFolderFile.exists()) {
                dataFolderFile.mkdirs();
            }

            // Copy default config if not exists
            File configFile = new File(dataFolderFile, "config.yml");
            if (!configFile.exists()) {
                try (var in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, configFile.toPath());
                    }
                }
            }

            // Load config
            config = Config.load(configFile);

            // Initialize database
            database = new Database(dataFolderFile, config);

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

            // Register command
            QueueCommand queueCommand = new QueueCommand();
            proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("queue")
                    .plugin(this)
                    .build(),
                queueCommand
            );

            log.info("PriorityQueue loaded successfully!");
        } catch (Exception e) {
            log.error("Failed to load PriorityQueue", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        log.info("PriorityQueue shutting down...");
        stopPositionUpdateScheduler();
        if (queueManager != null) {
            queueManager.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer targetServer = event.getResult().getServer().orElse(null);

        if (targetServer == null) {
            return;
        }

        String serverName = targetServer.getServerInfo().getName();

        // Check if connecting to target server
        if (!serverName.equalsIgnoreCase(config.getTargetServer())) {
            return;
        }

        // Check if player has bypass permission
        if (player.hasPermission("priorityqueue.bypass")) {
            return;
        }

        // Check if already in queue
        if (queueManager.getQueue().isInQueue(player.getUniqueId())) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            int position = queueManager.getQueue().getPlayerPosition(player.getUniqueId());
            sendMessage(player, config.getMessageQueuePosition(),
                    Map.of("position", String.valueOf(position)));
            return;
        }

        // Try to add to queue
        int priority = getPlayerPriority(player);
        QueuePlayer queuePlayer = new QueuePlayer(
                player.getUniqueId(),
                player.getUsername(),
                priority,
                System.currentTimeMillis(),
                -1
        );

        if (queueManager.getQueue().addToQueue(queuePlayer)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            int position = queuePlayer.getPosition();
            sendMessage(player, config.getMessageQueueJoin(),
                    Map.of("position", String.valueOf(position)));
        } else {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            sendMessage(player, config.getMessageQueueFull(), Map.of());
        }
    }

    private void processBatch(List<QueuePlayer> players) {
        for (QueuePlayer player : players) {
            Player proxyPlayer = proxy.getPlayer(player.getUuid()).orElse(null);
            if (proxyPlayer != null && proxyPlayer.isActive()) {
                RegisteredServer targetServer = proxy.getServer(config.getTargetServer()).orElse(null);
                if (targetServer != null) {
                    sendMessage(proxyPlayer, config.getMessageQueueComplete(), Map.of());
                    proxyPlayer.createConnectionRequest(targetServer).fireAndForget();
                }
            }
        }
    }

    private int getPlayerPriority(Player player) {
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

    private void startPositionUpdateScheduler() {
        if (config.getPositionUpdateInterval() <= 0) {
            return;
        }

        positionUpdateScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "PriorityQueue-PositionUpdater");
            thread.setDaemon(true);
            return thread;
        });

        positionUpdateScheduler.scheduleAtFixedRate(() -> {
            try {
                List<QueuePlayer> allPlayers = queueManager.getQueue().getAllPlayers();
                for (QueuePlayer player : allPlayers) {
                    Player proxyPlayer = proxy.getPlayer(player.getUuid()).orElse(null);
                    if (proxyPlayer != null && proxyPlayer.isActive()) {
                        int position = queueManager.getQueue().getPlayerPosition(player.getUuid());
                        int total = queueManager.getQueue().getQueueSize();
                        sendMessage(proxyPlayer, config.getMessageQueuePositionUpdate(),
                                Map.of("position", String.valueOf(position), "total", String.valueOf(total)));
                    }
                }
            } catch (Exception e) {
                log.error("Error updating queue positions", e);
            }
        }, config.getPositionUpdateInterval(), config.getPositionUpdateInterval(), TimeUnit.SECONDS);

        log.info("Position update scheduler started with {}s interval", config.getPositionUpdateInterval());
    }

    private void stopPositionUpdateScheduler() {
        if (positionUpdateScheduler != null && !positionUpdateScheduler.isShutdown()) {
            positionUpdateScheduler.shutdown();
            try {
                if (!positionUpdateScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    positionUpdateScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                positionUpdateScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Position update scheduler stopped");
        }
    }

    private void sendMessage(Player player, String message, Map<String, String> placeholders) {
        String formatted = config.formatMessage(message, placeholders);
        // Convert legacy color codes to Adventure components
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(formatted);
        player.sendMessage(component);
    }

    private class QueueCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("This command can only be used by players."));
                return;
            }

            Player player = (Player) source;

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
                    sendMessage(player, "You are not in the queue.", Map.of());
                }
            } else if (args[0].equalsIgnoreCase("info")) {
                // Show queue info
                String info = config.formatMessage(config.getMessageQueueInfo(), Map.of(
                        "size", String.valueOf(queueManager.getQueue().getQueueSize()),
                        "slots", String.valueOf(config.getSlotsPerInterval()),
                        "interval", String.valueOf(config.getInterval())
                ));
                player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand()
                        .deserialize(info));
            } else if (args[0].equalsIgnoreCase("reload")) {
                // Reload config (admin only)
                if (!player.hasPermission("priorityqueue.admin")) {
                    sendMessage(player, config.getMessageNoPermission(), Map.of());
                    return;
                }

                try {
                    File configFile = new File(dataFolder.toFile(), "config.yml");
                    config = Config.load(configFile);
                    stopPositionUpdateScheduler();
                    startPositionUpdateScheduler();
                    queueManager.restart();
                    sendMessage(player, config.getMessageConfigReloaded(), Map.of());
                } catch (Exception e) {
                    log.error("Failed to reload config", e);
                    player.sendMessage(Component.text("Failed to reload config: " + e.getMessage()));
                }
            } else {
                sendMessage(player, "Usage: /queue [leave|info|reload]", Map.of());
            }
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            if (invocation.arguments().length > 0 && invocation.arguments()[0].equalsIgnoreCase("reload")) {
                return invocation.source().hasPermission("priorityqueue.admin");
            }
            return true;
        }
    }
}
