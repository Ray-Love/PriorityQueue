package com.priorityqueue.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class QueueManager {

    @Getter
    private final PriorityQueue queue;

    private final ScheduledExecutorService scheduler;
    private final Consumer<List<QueuePlayer>> onBatchReady;

    private boolean running;

    public QueueManager(int maxSize, int slotsPerInterval, int interval,
                       Consumer<List<QueuePlayer>> onBatchReady) {
        this.queue = new PriorityQueue(maxSize, slotsPerInterval, interval);
        this.scheduler = new ScheduledThreadPoolExecutor(1);
        this.onBatchReady = onBatchReady;
        this.running = false;
    }

    /**
     * Start the queue processing
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<QueuePlayer> batch = queue.getNextBatch();
                if (!batch.isEmpty()) {
                    log.info("Processing batch of {} players", batch.size());
                    onBatchReady.accept(batch);
                }
            } catch (Exception e) {
                log.error("Error processing queue batch", e);
            }
        }, queue.getInterval(), queue.getInterval(), TimeUnit.MILLISECONDS);

        log.info("Queue manager started with {}ms interval", queue.getInterval());
    }

    /**
     * Stop the queue processing
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Queue manager stopped");
    }

    /**
     * Restart the queue manager
     */
    public void restart() {
        stop();
        start();
    }

    /**
     * Check if the queue is running
     */
    public boolean isRunning() {
        return running;
    }
}
