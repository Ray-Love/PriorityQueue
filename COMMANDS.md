# PriorityQueue Plugin Commands

## Overview
PriorityQueue is a queue management plugin for BungeeCord, Waterfall, and Velocity proxies. It allows players to queue up for servers with priority levels.

## Commands

### `/queue` or `/pq` or `/q`
Show your current position in the queue.

**Usage:** `/queue`

**Example:**
```
/queue
```
**Response:** `Position in queue: 15`

---

### `/queue leave`
Leave the current queue.

**Usage:** `/queue leave`

**Example:**
```
/queue leave
```
**Response:** `You have been removed from the queue.`

---

### `/queue info`
Display queue statistics including size, slots per batch, and interval.

**Usage:** `/queue info`

**Example:**
```
/queue info
```
**Response:**
```
=== Queue Info ===
Queue size: 50
Slots per interval: 5
Interval: 3000ms
```

---

### `/queue reload` (Admin only)
Reload the plugin configuration.

**Permission:** `priorityqueue.admin`

**Usage:** `/queue reload`

**Example:**
```
/queue reload
```
**Response:** `Configuration reloaded successfully.`

---

## Permissions

| Permission | Description | Default |
|-------------|---------------|----------|
| `priorityqueue.bypass` | Bypass the queue and connect directly | op |
| `priorityqueue.vip` | VIP priority in queue | false |
| `priorityqueue.mvp` | MVP priority in queue | false |
| `priorityqueue.admin` | Admin priority + reload command | op |
| `priorityqueue.use` | Basic queue command usage | true |

---

## Configuration

### Queue Settings
- `max-size`: Maximum number of players in queue (default: 1000)
- `slots-per-interval`: Players allowed per batch (default: 5)
- `interval`: Time between batches in milliseconds (default: 3000)
- `target-server`: Server name to queue for (default: "survival")
- `position-update-interval`: How often to update position in chat, 0 to disable (default: 5 seconds)

### Priority Settings
- `default`: Default priority level (default: 0)
- `levels`: Define priority levels for permissions

Example:
```yaml
priority:
  default: 0
  levels:
    vip: 1
    mvp: 2
    admin: 3
```

### Database Settings
- `type`: Database type - "sqlite" or "mysql" (default: sqlite)
- MySQL configuration:
  ```yaml
  mysql:
    host: "localhost"
    port: 3306
    database: "priorityqueue"
    username: "root"
    password: "password"
  ```

### Messages
All messages support color codes using `&` prefix and placeholders:

**Placeholders:**
- `{position}` - Player's position in queue
- `{total}` - Total number of players in queue
- `{size}` - Current queue size
- `{slots}` - Slots per interval
- `{interval}` - Interval in milliseconds

---

## How It Works

1. **Joining the Queue**: When a player tries to connect to the target server, they are automatically added to the queue
2. **Priority System**: Players with higher priority (VIP, MVP, Admin) are processed first
3. **Batch Processing**: Every `interval` milliseconds, `slots-per-interval` players are connected
4. **Position Updates**: Players receive position updates every `position-update-interval` seconds (if > 0)
5. **Bypass**: Players with `priorityqueue.bypass` permission skip the queue entirely

---

## Tips

- Use `/queue info` to see if the queue is moving
- Set `position-update-interval` to 0 if you don't want position spam
- Adjust `slots-per-interval` based on your server's capacity
- Higher priority numbers = higher in queue
