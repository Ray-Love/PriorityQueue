package com.priorityqueue.core;

import lombok.Data;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Data
public class Config {

    // Queue settings
    private int maxSize;
    private int slotsPerInterval;
    private int interval;
    private String targetServer;
    private int positionUpdateInterval;

    // Database settings
    private String databaseType;
    private String dbHost;
    private int dbPort;
    private String dbDatabase;
    private String dbUsername;
    private String dbPassword;

    // Priority settings
    private int defaultPriority;
    private Map<String, Integer> priorityLevels;

    // Messages
    private String messageQueueJoin;
    private String messageQueuePosition;
    private String messageQueuePositionUpdate;
    private String messageQueueComplete;
    private String messageQueueLeft;
    private String messageQueueFull;
    private String messageQueueInfo;
    private String messageNoPermission;
    private String messageConfigReloaded;

    public static Config load(File configFile) throws IOException {
        Config config = new Config();

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .file(configFile)
                .build();

        CommentedConfigurationNode node = loader.load();

        // Load queue settings
        config.maxSize = node.node("queue", "max-size").getInt(1000);
        config.slotsPerInterval = node.node("queue", "slots-per-interval").getInt(5);
        config.interval = node.node("queue", "interval").getInt(3000);
        config.targetServer = node.node("queue", "target-server").getString("survival");
        config.positionUpdateInterval = node.node("queue", "position-update-interval").getInt(5);

        // Load database settings
        config.databaseType = node.node("database", "type").getString("sqlite");
        config.dbHost = node.node("database", "mysql", "host").getString("localhost");
        config.dbPort = node.node("database", "mysql", "port").getInt(3306);
        config.dbDatabase = node.node("database", "mysql", "database").getString("priorityqueue");
        config.dbUsername = node.node("database", "mysql", "username").getString("root");
        config.dbPassword = node.node("database", "mysql", "password").getString("password");

        // Load priority settings
        config.defaultPriority = node.node("priority", "default").getInt(0);
        config.priorityLevels = new HashMap<>();
        CommentedConfigurationNode levelsNode = node.node("priority", "levels");
        for (CommentedConfigurationNode level : levelsNode.childrenMap().values()) {
            config.priorityLevels.put(level.key().toString(), level.getInt());
        }

        // Load messages
        config.messageQueueJoin = node.node("messages", "queue-join").getString("&7Connected to the queue. Position: &e{position}");
        config.messageQueuePosition = node.node("messages", "queue-position").getString("&7Position in queue: &e{position}");
        config.messageQueuePositionUpdate = node.node("messages", "queue-position-update").getString("&7Position in queue: &e{position} / {total}");
        config.messageQueueComplete = node.node("messages", "queue-complete").getString("&7You are next! Connecting...");
        config.messageQueueLeft = node.node("messages", "queue-left").getString("&7You have been removed from the queue.");
        config.messageQueueFull = node.node("messages", "queue-full").getString("&cQueue is full. Try again later.");
        config.messageQueueInfo = node.node("messages", "queue-info").getString("&7=== Queue Info ===&r\n&7Queue size: &e{size}\n&7Slots per interval: &e{slots}\n&7Interval: &e{interval}ms");
        config.messageNoPermission = node.node("messages", "no-permission").getString("&cYou don't have permission to use this command.");
        config.messageConfigReloaded = node.node("messages", "config-reloaded").getString("&aConfiguration reloaded successfully.");

        return config;
    }

    public String formatMessage(String message, Map<String, String> placeholders) {
        String formatted = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatted;
    }
}
