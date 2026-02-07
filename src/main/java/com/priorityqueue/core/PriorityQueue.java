package com.priorityqueue.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PriorityQueue {

    @Getter
    private final int maxSize;

    @Getter
    private final int slotsPerInterval;

    @Getter
    private final int interval;

    private final NavigableMap<Integer, List<QueuePlayer>> queueByPriority;
    private final Map<UUID, QueuePlayer> playersByUUID;
    private final AtomicInteger positionCounter;

    public PriorityQueue(int maxSize, int slotsPerInterval, int interval) {
        this.maxSize = maxSize;
        this.slotsPerInterval = slotsPerInterval;
        this.interval = interval;
        this.queueByPriority = new TreeMap<>(Collections.reverseOrder());
        this.playersByUUID = new ConcurrentHashMap<>();
        this.positionCounter = new AtomicInteger(1);
    }

    /**
     * Add a player to the queue
     * @param player The player to add
     * @return true if added, false if queue is full
     */
    public synchronized boolean addToQueue(QueuePlayer player) {
        if (playersByUUID.containsKey(player.getUuid())) {
            return true; // Already in queue
        }

        if (playersByUUID.size() >= maxSize) {
            return false; // Queue is full
        }

        playersByUUID.put(player.getUuid(), player);
        player.setPosition(positionCounter.getAndIncrement());

        queueByPriority.computeIfAbsent(player.getPriority(), k -> new ArrayList<>())
                .add(player);

        return true;
    }

    /**
     * Remove a player from the queue
     * @param uuid The player's UUID
     * @return The removed player, or null if not found
     */
    public synchronized QueuePlayer removeFromQueue(UUID uuid) {
        QueuePlayer player = playersByUUID.remove(uuid);
        if (player == null) {
            return null;
        }

        List<QueuePlayer> priorityList = queueByPriority.get(player.getPriority());
        if (priorityList != null) {
            priorityList.remove(player);
            if (priorityList.isEmpty()) {
                queueByPriority.remove(player.getPriority());
            }
        }

        return player;
    }

    /**
     * Check if a player is in the queue
     */
    public boolean isInQueue(UUID uuid) {
        return playersByUUID.containsKey(uuid);
    }

    /**
     * Get a player's position in the queue
     */
    public int getPlayerPosition(UUID uuid) {
        QueuePlayer player = playersByUUID.get(uuid);
        return player != null ? player.getPosition() : -1;
    }

    /**
     * Get a player from the queue
     */
    public QueuePlayer getPlayer(UUID uuid) {
        return playersByUUID.get(uuid);
    }

    /**
     * Get the next batch of players to connect
     */
    public synchronized List<QueuePlayer> getNextBatch() {
        List<QueuePlayer> batch = new ArrayList<>();
        int remaining = slotsPerInterval;

        for (Map.Entry<Integer, List<QueuePlayer>> entry : queueByPriority.entrySet()) {
            List<QueuePlayer> priorityList = entry.getValue();
            Iterator<QueuePlayer> iterator = priorityList.iterator();

            while (iterator.hasNext() && remaining > 0) {
                QueuePlayer player = iterator.next();
                batch.add(player);
                playersByUUID.remove(player.getUuid());
                iterator.remove();
                remaining--;
            }

            if (priorityList.isEmpty()) {
                queueByPriority.remove(entry.getKey());
            }

            if (remaining <= 0) {
                break;
            }
        }

        return batch;
    }

    /**
     * Get the current queue size
     */
    public int getQueueSize() {
        return playersByUUID.size();
    }

    /**
     * Clear the entire queue
     */
    public synchronized void clearQueue() {
        queueByPriority.clear();
        playersByUUID.clear();
        positionCounter.set(1);
    }

    /**
     * Get all players in the queue
     */
    public synchronized List<QueuePlayer> getAllPlayers() {
        return new ArrayList<>(playersByUUID.values());
    }
}
