# Java任务通知服务

这是一个Java任务完成后自动发送通知到钉钉、飞书等平台的工具。

## Redis点赞组件

项目还包含一个高性能的Redis点赞组件，支持防刷、缓存、排行榜等功能。详细信息请查看 [REDIS_LIKE.md](REDIS_LIKE.md)。

## 功能特性

- ✅ 钉钉机器人通知
- ✅ 飞书机器人通知
- ✅ 微信公众号通知
- ✅ 任务执行成功通知
- ✅ 任务执行失败通知
- ✅ 自定义消息通知
- ✅ 多平台同时通知
- ✅ 异常捕获和重试
- ✅ 执行时间统计

## 项目结构

```
JavaTest/
├── src/main/java/com/notification/
│   ├── service/           # 通知服务接口
│   ├── service/impl/      # 通知服务实现
│   ├── manager/           # 通知管理器
│   ├── executor/          # 任务执行器
│   └── Main.java          # 示例程序
├── src/main/resources/
│   └── config.properties  # 配置文件
├── pom.xml                # Maven配置
└── README.md             # 使用说明
```

## 快速开始

### 1. 配置机器人

#### 钉钉机器人配置
1. 在钉钉群中添加自定义机器人
2. 获取Webhook地址和密钥
3. 修改 `config.properties` 文件

#### 飞书机器人配置
1. 在飞书群中添加自定义机器人
2. 获取Webhook地址
3. 修改 `config.properties` 文件

### 2. 修改配置

编辑 `src/main/resources/config.properties` 文件：

```properties
# 钉钉配置
dingtalk.webhook=https://oapi.dingtalk.com/robot/send?access_token=你的token
dingtalk.secret=你的密钥

# 飞书配置
feishu.webhook=https://open.feishu.cn/open-apis/bot/v2/hook/你的webhook_id
```

### 3. 编译和运行

```bash
# 编译项目
mvn clean compile

# 运行通知服务示例
mvn exec:java -Dexec.mainClass="com.notification.Main"

# 运行Redis点赞组件示例
mvn exec:java -Dexec.mainClass="com.redis.like.RedisLikeExample"

# 打包
mvn clean package
```

### 4. 使用示例

#### 基本使用

```java
// 初始化通知管理器
NotificationManager manager = new NotificationManager();

// 发送普通消息
manager.sendToAll("任务完成！", "通知标题");

// 发送任务完成通知
manager.sendTaskCompletionToAll("数据同步", "成功", 2500);

// 发送异常通知
manager.sendErrorToAll("文件处理", "文件不存在");
```

#### 集成到任务中

```java
// 使用任务执行器
TaskExecutor executor = new TaskExecutor(manager);

// 执行带通知的任务
executor.executeWithNotification(() -> {
    // 你的任务逻辑
    return "任务结果";
}, "任务名称");
```

#### 单独使用服务

```java
// 钉钉通知
NotificationService dingTalk = new DingTalkNotificationService(webhookUrl, secret);
dingTalk.sendTaskCompletionNotification("数据备份", "成功", 3000);

// 飞书通知
NotificationService feishu = new FeishuNotificationService(webhookUrl);
feishu.sendErrorNotification("文件上传", "网络超时");
```

## 环境要求

- Java 8 或更高版本
- Maven 3.6 或更高版本
- 网络连接（用于发送HTTP请求）

## 依赖库

- Apache HttpClient 4.5.14
- Jackson Databind 2.15.2
- SLF4J Simple 1.7.36
- JUnit 4.13.2 (测试)

## 注意事项

1. **网络配置**：确保服务器能够访问钉钉和飞书的API地址
2. **频率限制**：注意机器人的消息频率限制
3. **安全设置**：妥善保管Webhook密钥，不要提交到版本控制
4. **异常处理**：程序会自动重试3次，建议添加监控
5. **时区设置**：确保服务器时区正确，消息时间才准确

## 故障排查

### 常见问题

1. **连接超时**
   - 检查网络连接
   - 确认防火墙设置
   - 验证Webhook地址是否正确

2. **认证失败**
   - 确认access_token是否正确
   - 检查钉钉密钥是否正确

3. **消息格式错误**
   - 检查消息内容是否包含敏感词
   - 确认消息格式符合平台要求

### 日志查看

程序使用SLF4J记录日志，可以通过以下方式查看：

```java
// 启用调试日志
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
```

## 扩展支持

可以轻松添加对其他平台的支持：

1. 实现 `NotificationService` 接口
2. 在 `NotificationManager` 中添加新服务
3. 更新配置文件

示例：企业微信、Slack、Discord等平台