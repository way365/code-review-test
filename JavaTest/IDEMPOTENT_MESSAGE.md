# 消息幂等组件

## 概述

消息幂等组件用于确保消息处理的幂等性，防止重复消息导致的业务问题。提供内存级和持久化两种实现，支持高并发场景下的消息去重。

## 核心特性

- **消息去重**: 基于消息唯一键进行去重
- **并发安全**: 支持高并发场景下的幂等性保证
- **持久化支持**: SQLite持久化，支持进程重启后的幂等性保持
- **灵活扩展**: 支持自定义消息ID提取器
- **内存优化**: 自动清理过期消息记录
- **统计监控**: 提供消息处理统计信息

## 架构设计

```
┌─────────────────────────┐
│  IdempotentMessageManager │  单例管理器
├─────────────────────────┤
│  IdempotentMessageProcessor │  幂等处理器
├─────────────────────────┤
│  MessageIdempotentService │  内存幂等服务
├─────────────────────────┤
│  PersistentIdempotentService │  持久化幂等服务
├─────────────────────────┤
│  IdempotentMessageInterceptor │  消息拦截器
└─────────────────────────┘
```

## 快速开始

### 1. 基本使用

```java
// 内存级幂等
IdempotentMessageManager manager = IdempotentMessageManager.getInstance();
manager.registerNotificationService("dingtalk", new DingTalkService());

// 发送幂等消息
boolean sent = manager.sendIdempotentMessage("dingtalk", "订单通知", "订单001已创建");
```

### 2. 持久化幂等

```java
// 持久化幂等
PersistentIdempotentService service = new PersistentIdempotentService("idempotent.db");

// 处理消息
String messageKey = "order_001";
if (service.markProcessing(messageKey)) {
    // 实际业务处理
    service.markProcessed(messageKey, "处理成功");
}
```

### 3. 任务集成

```java
// 任务执行器集成
ReliableTaskExecutor.executeWithReliableNotification(() -> {
    // 业务逻辑
    return "任务结果";
}, "任务完成通知", "dingtalk");
```

## 消息键生成策略

### 默认策略
```java
// 基于消息ID和目标地址
message -> message.getMessageId() + "_" + message.getDestination()
```

### 自定义策略
```java
// 自定义消息键提取器
Function<MessageEntity, String> customExtractor = message -> 
    message.getType() + "_" + message.getContent().hashCode();

IdempotentMessageManager manager = IdempotentMessageManager.getInstance();
manager.registerProcessor("dingtalk", customExtractor);
```

## API文档

### IdempotentMessageManager

#### 注册通知服务
```java
registerNotificationService(String serviceName, NotificationService service)
registerProcessor(String serviceName, Function<MessageEntity, String> messageIdExtractor)
```

#### 发送消息
```java
sendIdempotentMessage(String serviceName, String type, String content)
sendTaskCompletionNotification(String taskId, String serviceName, String result)
sendErrorNotification(String taskId, String serviceName, String error)
```

#### 管理功能
```java
getStatistics()    // 获取统计信息
clearRecords()     // 清理所有记录
start()           // 启动服务
stop()            // 停止服务
```

### PersistentIdempotentService

#### 核心方法
```java
isProcessed(String messageKey)           // 检查是否已处理
markProcessing(String messageKey)        // 标记处理中
markProcessed(String messageKey, String result)  // 标记已处理
getProcessingResult(String messageKey)   // 获取处理结果
remove(String messageKey)               // 移除记录
```

#### 管理方法
```java
getStatistics()     // 获取统计信息
cleanupExpired()    // 清理过期消息
close()            // 关闭连接
```

## 运行示例

### 内存级示例
```bash
mvn compile exec:java -Dexec.mainClass="com.notification.idempotent.example.IdempotentMessageExample"
```

### 持久化示例
```bash
mvn compile exec:java -Dexec.mainClass="com.notification.idempotent.example.PersistentIdempotentExample"
```

## 配置参数

### 内存级配置
- **清理周期**: 默认30分钟
- **过期时间**: 默认24小时
- **最大记录数**: 默认10000条

### 持久化配置
- **数据库路径**: 可配置SQLite文件路径
- **清理周期**: 默认7天
- **连接池**: 支持连接池配置

## 监控运维

### 统计信息
```java
// 获取统计信息
String stats = manager.getStatistics();
// 输出: 总消息数: 100, 已处理: 85, 处理中: 15
```

### 日志监控
- 消息处理状态变更
- 重复消息检测
- 清理任务执行

### 健康检查
```java
// 检查服务状态
boolean isHealthy = manager.isRunning();
int pendingCount = manager.getPendingCount();
```

## 故障排查

### 常见问题

#### 1. 消息重复处理
**原因**: 消息键生成策略冲突
**解决**: 检查消息键的唯一性，调整消息ID提取器

#### 2. 内存溢出
**原因**: 消息记录过多未及时清理
**解决**: 调整清理周期，增加内存限制

#### 3. 数据库锁定
**原因**: 并发访问SQLite
**解决**: 使用连接池，优化事务处理

### 调试工具
```java
// 查看消息记录
Map<String, Object> records = manager.getAllRecords();

// 清理指定消息
manager.clearMessageRecord("order_001");
```

## 扩展支持

### 自定义存储
```java
public class RedisIdempotentService implements IdempotentService {
    // 基于Redis的实现
}
```

### 分布式支持
```java
public class DistributedIdempotentService {
    // 基于分布式锁的实现
}
```

### 监控集成
```java
public class MetricsIdempotentService {
    // 集成Prometheus监控
}
```

## 最佳实践

### 1. 消息键设计
- 使用业务唯一标识符
- 包含时间戳防止冲突
- 考虑业务场景的幂等范围

### 2. 性能优化
- 合理设置本地缓存大小
- 批量清理过期记录
- 使用异步处理减少阻塞

### 3. 监控告警
- 监控重复消息比例
- 设置内存使用率告警
- 监控清理任务执行状态

## 技术栈

- **Java 8+**: 核心语言
- **SQLite**: 持久化存储
- **ConcurrentHashMap**: 内存缓存
- **ScheduledExecutorService**: 定时任务
- **JDBC**: 数据库访问

## 版本历史

### v1.0.0 (当前)
- 内存级消息幂等实现
- 持久化幂等支持
- 消息拦截器功能
- 完整示例和文档