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

        // Load priority settings
        config.defaultPriority = node.node("priority", "default").getInt(0);
        config.priorityLevels = new HashMap<>();
        CommentedConfigurationNode levelsNode = node.node("priority", "levels");
        for (CommentedConfigurationNode level : levelsNode.childrenMap().values()) {
            config.priorityLevels.put(level.key().toString(), level.getInt());
        }

        // Load messages
        config.messageQueueJoin = node.node("messages", "queue-join").getString("&a你已加入队列，位置: &e{position}");
        config.messageQueuePosition = node.node("messages", "queue-position").getString("&e你的队列位置: &a{position}");
        config.messageQueuePositionUpdate = node.node("messages", "queue-position-update").getString("&7Position in queue: &e{position} / {total}");
        config.messageQueueComplete = node.node("messages", "queue-complete").getString("&a轮到你进入服务器了！");
        config.messageQueueLeft = node.node("messages", "queue-left").getString("&c你已离开队列");
        config.messageQueueFull = node.node("messages", "queue-full").getString("&c队列已满，请稍后再试");
        config.messageQueueInfo = node.node("messages", "queue-info").getString("&6=== 队列信息 ===&r\n&a当前排队人数: &e{size}\n&a每批次连接: &e{slots}\n&a刷新间隔: &e{interval}ms");
        config.messageNoPermission = node.node("messages", "no-permission").getString("&c你没有权限使用此命令");
        config.messageConfigReloaded = node.node("messages", "config-reloaded").getString("&a配置已重载");

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
