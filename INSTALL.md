# PriorityQueue 安装指南

## 前置要求

- Java 17 或更高版本
- Velocity 3.0+ 或 BungeeCord/Waterfall 1.16+

## 安装步骤

### 1. 编译插件

在项目根目录下运行:

```bash
./gradlew build
```

Windows 用户运行:
```bash
gradlew.bat build
```

编译完成后,插件 jar 文件将位于 `build/libs/` 目录中。

### 2. 安装插件

将编译好的 jar 文件 `PriorityQueue-1.0.0.jar` 放入:

- **Velocity**: `velocity/plugins/` 目录
- **BungeeCord/Waterfall**: `bungeecord/plugins/` 目录

### 3. 配置

重启代理服务器,插件会自动生成配置文件 `plugins/PriorityQueue/config.yml`。

编辑配置文件:

```yaml
queue:
  max-size: 1000              # 队列最大人数
  slots-per-interval: 5      # 每批次允许连接的人数
  interval: 3000             # 批次间隔(毫秒)
  target-server: "survival"   # 队列连接的目标服务器

priority:
  default: 0                 # 默认优先级
  levels:
    vip: 1                   # VIP 优先级
    mvp: 2                   # MVP 优先级
    admin: 3                 # 管理员优先级
```

### 4. 配置权限

在你的权限插件(LuckPerms 等)中分配权限:

| 权限 | 说明 |
|------|------|
| `priorityqueue.bypass` | 绕过队列直接进入 |
| `priorityqueue.vip` | VIP 优先级 |
| `priorityqueue.mvp` | MVP 优先级 |
| `priorityqueue.admin` | 管理员优先级 |
| `priorityqueue.*` | 所有权限 |

LuckPerms 示例:

```bash
# 给 VIP 组设置权限
lp group permission set vip priorityqueue.vip true

# 给 MVP 组设置权限
lp group permission set mvp priorityqueue.mvp true

# 给管理员组设置权限
lp group permission set admin priorityqueue.admin true
```

### 5. 重载配置

修改配置后,执行命令重载:

```
/queue reload
```

## 命令使用

| 命令 | 说明 |
|------|------|
| `/queue` | 查看当前队列位置 |
| `/queue leave` | 离开队列 |
| `/queue info` | 查看队列信息 |
| `/queue reload` | 重载配置(需管理员权限) |

## 常见问题

### Q: 玩家无法进入服务器?

A: 检查以下几点:
1. 确保 `target-server` 配置正确
2. 检查队列是否已满(`max-size`)
3. 查看控制台是否有错误日志

### Q: 权限不生效?

A: 确保你的权限插件已正确配置,并且服务器已重载权限。

### Q: 如何调整队列速度?

A: 修改 `config.yml` 中的:
- `slots-per-interval`: 每批次允许连接的人数(越大越快)
- `interval`: 批次间隔毫秒数(越小越快)

### Q: 如何让特定玩家直接进入?

A: 给该玩家 `priorityqueue.bypass` 权限。
