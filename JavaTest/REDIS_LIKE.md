# Redis点赞组件

## 概述

Redis点赞组件是一个高性能、可扩展的点赞系统，基于Redis实现，支持点赞/取消点赞、排行榜、防刷机制、事件监听等功能。

## 核心特性

- ✅ **高性能**: 基于Redis内存存储，毫秒级响应
- ✅ **防刷机制**: 基于IP和用户的频率限制
- ✅ **事件驱动**: 支持点赞事件监听和处理
- ✅ **缓存优化**: 本地缓存+Redis缓存混合方案
- ✅ **多场景支持**: 支持文章、评论、视频等不同场景
- ✅ **排行榜**: 实时点赞排行榜功能
- ✅ **批量操作**: 支持批量查询和检查

## 架构设计

```
┌─────────────────────────┐
│  RedisLikeManager       │  统一管理器
├─────────────────────────┤
│  EventDrivenLikeService │  事件驱动服务
├─────────────────────────┤
│  RedisLikeService       │  核心点赞服务
├─────────────────────────┤
│  LikeCacheService       │  缓存服务
├─────────────────────────┤
│  Redis                 │  数据存储
└─────────────────────────┘
```

## 快速开始

### 1. 基本使用

```java
// 创建点赞管理器
RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);

// 点赞
RedisLikeService.LikeResult result = likeManager.like("article_123", "user_456", "192.168.1.100");
System.out.println("点赞结果: " + result.getMessage());

// 获取点赞数
long count = likeManager.getLikeCount("article_123");
System.out.println("点赞数: " + count);

// 取消点赞
likeManager.unlike("article_123", "user_456");
```

### 2. 事件监听

```java
// 添加事件监听器
likeManager.addEventListener(new LikeEventListener() {
    @Override
    public void onLike(LikeEvent event) {
        System.out.println("用户 " + event.getUserId() + " 点赞了 " + event.getTargetId());
    }
    
    @Override
    public void onUnlike(UnlikeEvent event) {
        System.out.println("用户 " + event.getUserId() + " 取消了点赞");
    }
    
    @Override
    public void onCountChange(CountChangeEvent event) {
        System.out.println("点赞数变化: " + event.getDelta());
    }
});
```

### 3. 多场景配置

```java
// 文章点赞配置
RedisLikeService.LikeConfig articleConfig = new RedisLikeService.LikeConfig(
    "articles", 100, 50, 3600, true
);
likeManager.registerConfig("articles", articleConfig);

// 评论点赞配置
RedisLikeService.LikeConfig commentConfig = new RedisLikeService.LikeConfig(
    "comments", 50, 30, 1800, true
);
likeManager.registerConfig("comments", commentConfig);

// 使用不同配置
likeManager.like("article_123", "user_456", "192.168.1.100", "articles");
likeManager.like("comment_001", "user_456", "192.168.1.100", "comments");
```

## 配置参数

### RedisLikeService.LikeConfig

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| keyPrefix | String | - | Redis键前缀 |
| maxLikesPerUser | int | 100 | 每用户最大点赞数 |
| maxLikesPerIP | int | 50 | 每IP最大点赞数 |
| timeWindowSeconds | int | 3600 | 时间窗口（秒） |
| enableAntiBrush | boolean | true | 是否启用防刷 |

### LikeCacheService

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| cacheTtlSeconds | long | 300 | 缓存过期时间（秒） |
| maxCacheSize | int | 10000 | 最大缓存条目数 |

## API文档

### RedisLikeManager

#### 核心方法
```java
// 点赞/取消点赞
LikeResult like(String targetId, String userId, String ip)
LikeResult like(String targetId, String userId, String ip, String configKey)
LikeResult unlike(String targetId, String userId)
LikeResult unlike(String targetId, String userId, String configKey)

// 获取点赞数
long getLikeCount(String targetId)
long getLikeCount(String targetId, String configKey)
long getRealLikeCount(String targetId) // 绕过缓存

// 配置管理
void registerConfig(String key, LikeConfig config)
void addEventListener(LikeEventListener listener)

// 缓存管理
void clearCache()
void invalidateCache(String targetId, String configKey)
```

### RedisLikeService

#### 核心方法
```java
// 点赞操作
LikeResult like(String targetId, String userId, String ip, String configKey)
LikeResult unlike(String targetId, String userId, String configKey)

// 查询方法
long getLikeCount(String targetId, String configKey)
boolean hasLiked(String targetId, String userId, String configKey)
Set<String> getUserLikes(String userId, String configKey)
List<Entry<String, Long>> getRankings(int limit, String configKey)

// 批量操作
Map<String, Boolean> batchCheckLikes(Set<String> targetIds, String userId, String configKey)
```

## 数据结构设计

### Redis键设计

| 键名 | 类型 | 说明 |
|------|------|------|
| `likes:{prefix}:{targetId}` | Set | 点赞用户集合 |
| `like_count:{prefix}:{targetId}` | String | 点赞计数 |
| `like_rank:{prefix}` | Sorted Set | 点赞排行榜 |
| `user_likes:{userId}` | Set | 用户点赞列表 |
| `ip_limit:{ip}` | String | IP限制计数 |
| `user_limit:{userId}` | String | 用户限制计数 |

### 数据示例

```redis
# 文章123的点赞用户
likes:articles:article_123 = ["user_456", "user_789", ...]

# 文章123的点赞数
like_count:articles:article_123 = 156

# 文章排行榜
like_rank:articles = [("article_123", 156), ("article_456", 89), ...]
```

## 防刷机制

### 1. 频率限制
```java
// 每用户限制
new LikeConfig("articles", 100, 50, 3600, true)
// 表示：每用户每小时最多100次点赞

// 每IP限制
new LikeConfig("articles", 100, 50, 3600, true)
// 表示：每IP每小时最多50次点赞
```

### 2. 自定义规则
```java
// 严格模式
LikeConfig strict = new LikeConfig("strict", 5, 10, 60, true);

// 宽松模式
LikeConfig loose = new LikeConfig("loose", 1000, 500, 86400, false);
```

## 缓存优化

### 1. 缓存策略
- **本地缓存**: 减少Redis访问
- **过期机制**: 自动清理过期数据
- **LRU策略**: 防止内存溢出

### 2. 缓存使用
```java
// 使用缓存获取点赞数
long cachedCount = likeManager.getLikeCount("article_123");

// 获取实时数据
long realCount = likeManager.getRealLikeCount("article_123");

// 手动失效缓存
likeManager.invalidateCache("article_123", "articles");
```

## 排行榜功能

### 1. 获取排行榜
```java
// 获取前10名
List<Map.Entry<String, Long>> top10 = likeManager.likeService.getRankings(10);

// 获取前100名
List<Map.Entry<String, Long>> top100 = likeManager.likeService.getRankings(100);
```

### 2. 排行榜格式
```java
// 返回格式
[
  {"key": "article_123", "value": 156},
  {"key": "article_456", "value": 89},
  {"key": "article_789", "value": 67}
]
```

## 运行示例

### 1. 编译项目
```bash
mvn compile
```

### 2. 运行示例
```bash
# 运行完整示例
mvn exec:java -Dexec.mainClass="com.redis.like.example.RedisLikeExample"

# 运行指定示例
mvn exec:java -Dexec.mainClass="com.redis.like.example.RedisLikeExample" -Dexec.args="basic"
```

### 3. Redis环境准备
```bash
# 启动Redis
docker run -d -p 6379:6379 redis:latest

# 或本地安装
redis-server
```

## 性能优化

### 1. 连接池配置
```java
JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(100);      // 最大连接数
poolConfig.setMaxIdle(50);        // 最大空闲连接
poolConfig.setMinIdle(10);        // 最小空闲连接
poolConfig.setTestOnBorrow(true); // 连接验证
```

### 2. 缓存配置
```java
// 高频访问场景
LikeCacheService cache = new LikeCacheService(jedisPool, 60, 50000);

// 低频访问场景
LikeCacheService cache = new LikeCacheService(jedisPool, 600, 1000);
```

## 监控指标

### 1. 统计信息
```java
// 获取统计
Map<String, Object> stats = likeManager.getStats("articles");
// stats包含：
// - totalTargets: 总目标数
// - totalLikes: 总点赞数
// - topContent: 热门内容
```

### 2. 缓存统计
```java
// 获取缓存统计
Map<String, Object> cacheStats = likeManager.getCacheStats();
// cacheStats包含：
// - cacheSize: 缓存大小
// - maxCacheSize: 最大缓存
// - expiredCount: 过期数量
```

## 扩展集成

### 1. 消息队列集成
```java
likeManager.addEventListener(new LikeEventListener() {
    @Override
    public void onLike(LikeEvent event) {
        // 发送到消息队列
        messageQueue.send("like.topic", event);
    }
});
```

### 2. 数据库同步
```java
likeManager.addEventListener(new LikeEventListener() {
    @Override
    public void onLike(LikeEvent event) {
        // 同步到数据库
        database.updateLikeCount(event.getTargetId(), event.getCurrentCount());
    }
});
```

### 3. 实时推送
```java
likeManager.addEventListener(new LikeEventListener() {
    @Override
    public void onCountChange(CountChangeEvent event) {
        // WebSocket推送
        websocket.broadcast(event.getTargetId(), event.getNewCount());
    }
});
```

## 故障排查

### 1. 连接问题
```java
// 检查Redis连接
try (Jedis jedis = likeManager.getJedisPool().getResource()) {
    System.out.println("Redis连接正常: " + jedis.ping());
}
```

### 2. 数据验证
```java
// 验证数据一致性
long redisCount = likeManager.getRealLikeCount("article_123");
long cachedCount = likeManager.getLikeCount("article_123");
System.out.println("Redis数据: " + redisCount + ", 缓存数据: " + cachedCount);
```

### 3. 性能调优
```java
// 监控响应时间
long start = System.currentTimeMillis();
long count = likeManager.getLikeCount("article_123");
long time = System.currentTimeMillis() - start;
System.out.println("查询耗时: " + time + "ms");
```

## 最佳实践

### 1. 配置建议
- **高频场景**: 缓存TTL 1-5分钟
- **低频场景**: 缓存TTL 10-30分钟
- **严格防刷**: 每用户每小时50次，每IP每小时100次
- **宽松防刷**: 每用户每天1000次，每IP每天500次

### 2. 监控策略
- 定期清理过期数据
- 监控Redis内存使用
- 监控缓存命中率
- 监控防刷触发频率

### 3. 部署建议
- Redis集群部署提高可用性
- 使用Redis哨兵模式
- 配置合理的内存限制
- 定期备份Redis数据

## 技术栈

- **Java 8+**: 核心语言
- **Jedis**: Redis客户端
- **Redis**: 数据存储
- **ScheduledExecutorService**: 定时任务
- **ConcurrentHashMap**: 并发数据结构

## 版本历史

### v1.0.0 (当前)
- 基本点赞功能
- 防刷机制
- 排行榜功能
- 事件监听
- 缓存优化
- 完整示例和文档

### 后续计划
- 分布式锁支持
- 更多缓存策略
- 批量操作优化
- 监控面板
- 更多统计指标

## 许可证

MIT License - 可自由使用和修改