# 可靠消息必达组件 (Reliable Message MQ)

## 概述

这是一个基于本地消息表 + 定时任务的MQ必达组件，确保消息100%可靠投递。即使网络异常、服务宕机，也能保证消息最终送达。

## 核心特性

- ✅ **100%消息必达**：本地消息表 + 定时重试机制
- ✅ **失败重试**：指数退避算法，智能重试间隔
- ✅ **多平台支持**：钉钉、飞书、微信公众号
- ✅ **任务集成**：与任务执行器无缝集成
- ✅ **死亡消息**：达到最大重试次数后标记为死亡
- ✅ **SQLite存储**：轻量级本地数据库，无需额外部署
- ✅ **定时任务**：30秒间隔自动检查待发送消息

## 架构设计

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│  ReliableMessage │───▶│   Notification  │
│                 │    │     Service      │    │    Services     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                       ┌──────────────────┐
                       │  Message Table   │
                       │  (SQLite)        │
                       └──────────────────┘
```

## 快速开始

### 1. 基本使用

```java
// 获取单例
ReliableMessageManager manager = ReliableMessageManager.getInstance();

// 注册通知服务
manager.registerNotificationService("dingtalk", 
    new DingTalkNotificationService(webhook, secret));

// 启动服务
manager.start();

// 发送可靠消息
manager.sendReliableMessage("dingtalk", webhook, "Hello World");

// 停止服务
manager.stop();
```

### 2. 任务集成使用

```java
ReliableMessageManager manager = ReliableMessageManager.getInstance();
ReliableTaskExecutor executor = new ReliableTaskExecutor(manager);

// 注册服务并启动...

// 执行任务并发送可靠通知
String result = executor.executeWithReliableNotification(
    "数据同步任务",
    "dingtalk",
    webhook,
    () -> {
        // 任务逻辑
        return "同步完成";
    }
);
```

### 3. 多平台支持

```java
// 钉钉
manager.sendDingTalkMessage(webhook, "钉钉消息");

// 飞书
manager.sendFeishuMessage(webhook, "飞书消息");

// 微信
manager.sendWeChatMessage(openId, "微信消息");
```

## 消息表结构

```sql
CREATE TABLE reliable_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id VARCHAR(64) UNIQUE NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status INTEGER DEFAULT 0, -- 0待发送 1已发送 2发送失败 3已死亡
    retry_count INTEGER DEFAULT 0,
    max_retry INTEGER DEFAULT 3,
    next_retry_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);
```

## 重试策略

| 重试次数 | 延迟时间 | 说明 |
|---------|----------|------|
| 第1次   | 立即发送 | 首次尝试 |
| 第2次   | 30秒后   | 第一次重试 |
| 第3次   | 60秒后   | 第二次重试 |
| 第4次   | 120秒后  | 第三次重试 |
| 第5次   | 240秒后  | 最终重试 |

## 配置参数

在 `config.properties` 中配置各平台参数：

```properties
# 钉钉配置
dingtalk.webhook=https://oapi.dingtalk.com/robot/send

# 飞书配置
feishu.webhook=https://open.feishu.cn/open-apis/bot/v2/hook

# 微信配置
wechat.appid=your_appid
wechat.appsecret=your_appsecret
wechat.templateid=your_templateid
wechat.openid=your_openid
```

## 运行示例

```bash
# 编译项目
mvn compile

# 运行示例
mvn exec:java -Dexec.mainClass="com.notification.mq.example.ReliableMessageExample"

# 带参数运行
mvn exec:java -Dexec.mainClass="com.notification.mq.example.ReliableMessageExample" \
  -Ddingtalk.webhook=https://oapi.dingtalk.com/robot/send \
  -Ddingtalk.secret=your_secret
```

## API文档

### ReliableMessageManager

| 方法 | 说明 |
|------|------|
| `getInstance()` | 获取单例实例 |
| `start()` | 启动消息服务 |
| `stop()` | 停止消息服务 |
| `registerNotificationService(type, service)` | 注册通知服务 |
| `sendReliableMessage(type, destination, content)` | 发送可靠消息 |
| `sendTaskCompletion(type, destination, task, duration)` | 发送任务完成通知 |
| `sendError(type, destination, task, error)` | 发送任务失败通知 |

### ReliableTaskExecutor

| 方法 | 说明 |
|------|------|
| `executeWithReliableNotification(taskName, type, destination, task)` | 执行并发送可靠通知 |

## 监控与运维

### 查看消息状态

```java
// 通过SQLite工具查看
sqlite3 reliable_message.db
> SELECT * FROM reliable_message ORDER BY create_time DESC;
```

### 死亡消息处理

死亡消息（status=3）需要人工介入：
- 检查网络连接
- 验证配置参数
- 手动重新发送
- 调整重试策略

### 性能监控

- 消息积压监控
- 重试频率监控
- 死亡消息统计
- 发送成功率统计

## 故障排查

### 常见问题

1. **消息未发送**
   - 检查网络连接
   - 验证webhook配置
   - 查看error_message字段

2. **重试过于频繁**
   - 调整重试间隔
   - 增加最大重试次数
   - 检查服务端限制

3. **SQLite锁定**
   - 避免并发写入
   - 使用连接池
   - 增加超时时间

### 调试模式

```java
// 开启调试日志
System.setProperty("debug", "true");
```

## 扩展支持

### 自定义消息类型

```java
// 实现NotificationService接口
public class CustomNotificationService implements NotificationService {
    @Override
    public void sendNotification(String content) {
        // 自定义发送逻辑
    }
}

// 注册使用
manager.registerNotificationService("custom", new CustomNotificationService());
```

### 自定义重试策略

```java
// 扩展ReliableMessageService
public class CustomRetryStrategy extends ReliableMessageService {
    @Override
    protected long calculateDelay(int retryCount) {
        return retryCount * 60L; // 线性重试
    }
}
```

## 最佳实践

1. **消息幂等性**：确保消息ID唯一
2. **合理重试**：避免过于频繁的重试
3. **监控告警**：设置死亡消息告警
4. **配置管理**：集中管理各平台配置
5. **日志记录**：详细记录发送日志

## 技术栈

- **数据库**：SQLite 3.45.1
- **HTTP客户端**：Apache HttpClient 5.3
- **JSON处理**：Jackson 2.17.0
- **定时任务**：ScheduledExecutorService
- **日志**：SLF4J + Logback

## 版本历史

- v1.0.0：基础消息必达功能
- v1.1.0：多平台支持
- v1.2.0：任务执行器集成
- v1.3.0：指数退避重试