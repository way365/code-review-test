# MySQL主从延迟监控组件

## 概述

MySQL主从延迟监控组件是一个专业的数据库监控工具，用于实时监控MySQL主从复制的延迟情况，支持多源监控、智能告警和高性能连接管理。

## 核心特性

- **实时监控**: 毫秒级延迟检测
- **多源支持**: 同时监控多个主从复制对
- **智能告警**: 基于规则的告警系统
- **连接池**: 高性能数据库连接管理
- **可扩展**: 支持自定义告警通知
- **易集成**: 简单的API接口

## 架构设计

```
┌─────────────────────────┐
│  MultiSourceMonitor     │  多源监控管理
├─────────────────────────┤
│  MySQLReplicationMonitor │  单源监控器
├─────────────────────────┤
│  ReplicationAlertManager│  告警管理器
├─────────────────────────┤
│  MySQLConnectionPool    │  连接池管理
└─────────────────────────┘
```

## 快速开始

### 1. 基本监控

```java
// 创建监控器
MySQLReplicationMonitor monitor = new MySQLReplicationMonitor(
    "jdbc:mysql://master:3306/mysql",  // 主库
    "jdbc:mysql://slave:3306/mysql",   // 从库
    "username",
    "password",
    5000  // 5秒检查间隔
);

// 添加监听器
monitor.addListener(new MySQLReplicationMonitor.ReplicationDelayListener() {
    @Override
    public void onDelayChange(long delaySeconds) {
        System.out.println("延迟: " + delaySeconds + " 秒");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("监控错误: " + e.getMessage());
    }
});

// 启动监控
monitor.start();
```

### 2. 多源监控

```java
MultiSourceMonitor multiMonitor = new MultiSourceMonitor();

// 添加多个监控源
multiMonitor.addMonitor("db1", new MySQLReplicationMonitor(
    "jdbc:mysql://db1-master:3306/mysql",
    "jdbc:mysql://db1-slave:3306/mysql",
    "user", "pass", 5000
));

multiMonitor.addMonitor("db2", new MySQLReplicationMonitor(...));

// 启动所有监控
multiMonitor.startAll();
```

### 3. 告警配置

```java
ReplicationAlertManager alertManager = new ReplicationAlertManager();

// 添加告警规则
alertManager.addRule("high_delay", new AlertRule(
    monitor,
    AlertType.DELAY_THRESHOLD,
    60,    // 60秒阈值
    300,   // 5分钟冷却
    "主从延迟告警"
));

// 添加邮件通知
alertManager.addListener(new AlertListener() {
    @Override
    public void onAlert(AlertEvent event) {
        sendEmailAlert(event);
    }

    @Override
    public void onRecovery(RecoveryEvent event) {
        sendEmailRecovery(event);
    }
});
```

## 配置参数

### 数据库连接
```properties
# 主库连接
master.url=jdbc:mysql://master-host:3306/mysql
master.username=monitor_user
master.password=monitor_pass

# 从库连接
slave.url=jdbc:mysql://slave-host:3306/mysql
slave.username=monitor_user
slave.password=monitor_pass
```

### 监控参数
```properties
# 检查间隔（毫秒）
monitor.interval=5000

# 连接池配置
pool.max.connections=10
pool.timeout.seconds=5
```

### 告警规则
```properties
# 延迟阈值（秒）
alert.delay.threshold=60

# 冷却时间（秒）
alert.cooldown.seconds=300
```

## API文档

### MySQLReplicationMonitor

#### 构造函数
```java
MySQLReplicationMonitor(String masterUrl, String slaveUrl, String username, String password)
MySQLReplicationMonitor(String masterUrl, String slaveUrl, String username, String password, long monitorInterval)
```

#### 核心方法
```java
void start()                    // 启动监控
void stop()                     // 停止监控
long getReplicationDelay()      // 获取当前延迟
ReplicationStatus getDetailedStatus()  // 获取详细状态
void addListener(ReplicationDelayListener listener)  // 添加监听器
```

### MultiSourceMonitor

#### 核心方法
```java
void addMonitor(String name, MySQLReplicationMonitor monitor)
void removeMonitor(String name)
void startAll()
void stopAll()
Map<String, MonitorStatus> getAllStatuses()
```

### ReplicationAlertManager

#### 核心方法
```java
void addRule(String name, AlertRule rule)
void removeRule(String name)
void start()
void stop()
void addListener(AlertListener listener)
```

### MySQLConnectionPool

#### 核心方法
```java
Connection getConnection()
void close()
PoolStatus getStatus()
```

## 告警规则类型

### 1. 延迟阈值告警
```java
new AlertRule(monitor, AlertType.DELAY_THRESHOLD, 60, 300, "延迟超过60秒")
```

### 2. 延迟增加告警
```java
new AlertRule(monitor, AlertType.DELAY_INCREASE, 10, 60, "延迟增加超过10秒")
```

### 3. 复制停止告警
```java
new AlertRule(monitor, AlertType.REPLICATION_STOPPED, 0, 60, "复制进程停止")
```

## 运行示例

### 编译项目
```bash
mvn compile
```

### 运行示例
```bash
# 运行基本示例
mvn exec:java -Dexec.mainClass="com.mysql.monitor.example.MySQLMonitorExample"

# 运行指定示例
mvn exec:java -Dexec.mainClass="com.mysql.monitor.example.MySQLMonitorExample" -Dexec.args="basic"
```

### Docker测试环境
```bash
# 启动MySQL主从环境
docker-compose up -d

# 运行监控
java -cp target/mysql-monitor.jar com.mysql.monitor.example.MySQLMonitorExample
```

## 监控指标

### 核心指标
- **Seconds_Behind_Master**: 从库延迟时间（秒）
- **Slave_IO_Running**: IO线程状态
- **Slave_SQL_Running**: SQL线程状态
- **Read_Master_Log_Pos**: 读取位置

### 告警指标
- **延迟阈值**: 超过设定值触发告警
- **延迟增长率**: 延迟快速增加触发告警
- **复制状态**: 复制停止触发告警

## 性能优化

### 连接池优化
```java
// 合理配置连接池大小
MySQLConnectionPool pool = new MySQLConnectionPool(
    url, username, password,
    20,  // 最大连接数
    "optimized_pool"
);
```

### 监控频率优化
```java
// 根据业务需求调整监控频率
MySQLReplicationMonitor monitor = new MySQLReplicationMonitor(
    masterUrl, slaveUrl, username, password,
    10000  // 10秒检查间隔，减少数据库压力
);
```

## 故障排查

### 常见问题

#### 1. 连接失败
**症状**: 无法连接数据库
**解决**: 
- 检查网络连通性
- 验证用户名密码
- 确认MySQL配置允许远程连接

#### 2. 权限问题
**症状**: 无法执行SHOW SLAVE STATUS
**解决**:
```sql
GRANT REPLICATION CLIENT ON *.* TO 'monitor_user'@'%';
FLUSH PRIVILEGES;
```

#### 3. 监控无数据
**症状**: 延迟始终为0
**解决**:
- 确认主从复制已配置
- 检查binlog格式
- 验证复制用户权限

### 调试工具
```java
// 获取详细状态
ReplicationStatus status = monitor.getDetailedStatus();
System.out.println("详细状态: " + status);

// 手动检查延迟
long delay = monitor.getReplicationDelay();
System.out.println("当前延迟: " + delay + " 秒");
```

## 扩展集成

### 邮件告警集成
```java
alertManager.addListener(new AlertListener() {
    @Override
    public void onAlert(AlertEvent event) {
        EmailSender.send("dba@company.com", 
                        "MySQL延迟告警", 
                        event.toString());
    }
});
```

### 监控平台集成
```java
// Prometheus指标
alertManager.addListener(new AlertListener() {
    @Override
    public void onAlert(AlertEvent event) {
        PrometheusMetrics.counter("mysql_replication_delay").inc();
    }
});
```

### Web界面集成
```java
// REST API端点
@GetMapping("/api/mysql/delay/{source}")
public Map<String, Object> getDelay(@PathVariable String source) {
    return multiMonitor.getAllStatuses().get(source).toMap();
}
```

## 最佳实践

### 1. 监控策略
- 核心业务库：5秒检查间隔
- 一般业务库：30秒检查间隔
- 日志库：60秒检查间隔

### 2. 告警策略
- 延迟阈值：根据业务容忍度设置
- 冷却时间：避免告警风暴
- 分级告警：不同级别不同通知方式

### 3. 运维策略
- 定期维护：清理告警历史
- 容量规划：根据监控源数量调整资源
- 灾备方案：监控服务高可用部署

## 技术栈

- **Java 8+**: 核心语言
- **MySQL Connector/J**: MySQL驱动
- **ScheduledExecutorService**: 定时任务
- **Concurrent Collections**: 并发数据结构
- **JDBC**: 数据库连接

## 版本历史

### v1.0.0 (当前)
- 基本监控功能
- 多源监控支持
- 告警管理系统
- 连接池管理
- 完整示例和文档

### 后续计划
- Web管理界面
- 历史数据存储
- 集群监控支持
- 更多告警渠道

## 许可证

MIT License - 可自由使用和修改