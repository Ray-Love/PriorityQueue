package com.priorityqueue.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuePlayer {
    private UUID uuid;
    private String name;
    private int priority;
    private long joinTime;
    private int position;
}
