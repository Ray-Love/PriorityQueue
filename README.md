PriorityQueue Plugin
===================

一个支持 Velocity、BungeeCord 和 Waterfall 的 Minecraft 优先队列插件。

## 功能特性

- ✅ 支持 Velocity、BungeeCord、Waterfall
- ✅ 多级优先级系统
- ✅ 队列位置查询
- ✅ 队列通知
- ✅ 数据库持久化存储
- ✅ 可配置的队列大小和每批次连接数
- ✅ 权限系统支持
- ✅ 排队时的位置更新

## 安装

1. 将构建的 jar 文件放入服务器的 `plugins` 文件夹
2. 重启服务器
3. 编辑 `plugins/PriorityQueue/config.yml` 配置文件
4. 重启服务器或使用 `/pq reload` 重载配置

## 配置

```yaml
queue:
  max-size: 1000
  slots-per-interval: 5
  interval: 3000  # 毫秒

priority:
  default: 0
  levels:
    vip: 1
    mvp: 2
    admin: 3

messages:
  queue-join: "&a你已加入队列，位置: &e{position}"
  queue-position: "&e你的队列位置: &a{position}"
  queue-complete: "&a轮到你进入服务器了！"
  queue-left: "&c你已离开队列"
  queue-full: "&c队列已满，请稍后再试"

servers:
  target-server: "survival"  # 队列连接的目标服务器
```

## 权限

- `priorityqueue.bypass` - 绕过队列直接进入
- `priorityqueue.vip` - VIP 优先级
- `priorityqueue.mvp` - MVP 优先级
- `priorityqueue.admin` - 管理员优先级
- `priorityqueue.*` - 所有权限

## 命令

- `/queue` - 查看当前队列位置
- `/queue leave` - 离开队列
- `/queue info` - 查看队列信息
- `/queue reload` - 重载配置 (管理员)

## 构建

```bash
./gradlew build
```

构建后的 jar 文件位于 `build/libs/`
