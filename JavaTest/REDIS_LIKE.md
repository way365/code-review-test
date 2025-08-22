# Redis点赞组件

一个基于Redis实现的高性能点赞组件，支持防刷、缓存、排行榜等功能。

## 功能特性

1. **基础点赞功能**
   - 点赞/取消点赞
   - 获取点赞数
   - 批量获取点赞数

2. **防刷机制**
   - IP限制
   - 用户限制
   - 最小点赞间隔

3. **缓存优化**
   - 二级缓存（本地缓存+Redis缓存）
   - 热点数据预热
   - 缓存统计

4. **排行榜功能**
   - 实时排行榜
   - 分页查询

5. **事件驱动**
   - 点赞事件监听
   - 取消点赞事件监听
   - 点赞数变化事件监听

6. **加权点赞**
   - 根据用户权重计算点赞数
   - 支持个性化权重配置

7. **点赞历史记录**
   - 记录用户点赞历史
   - 支持历史记录查询

8. **性能监控**
   - 性能指标收集
   - 健康检查
   - 性能报告

## 使用示例

```java
// 创建点赞管理器
RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);

// 注册配置
RedisLikeService.LikeConfig config = new RedisLikeService.LikeConfig(
    "articles",     // key前缀
    100,             // 每用户最多100次点赞
    50,              // 每IP最多50次点赞
    3600,            // 60分钟时间窗口
    true,            // 启用防刷
    5000,            // 最小点赞间隔5秒
    true,            // 启用加权点赞
    true             // 启用点赞历史记录
);
likeManager.registerConfig("articles", config);

// 点赞
RedisLikeService.LikeResult result = likeManager.like("article_001", "user_001", "192.168.1.1", "articles");

// 获取点赞数
long count = likeManager.getLikeCount("article_001", "articles");

// 获取用户点赞历史
List<String> history = likeManager.getUserLikeHistory("user_001");

// 获取排行榜
List<Map.Entry<String, Long>> rankings = likeManager.likeService.getRankings(10, "articles");

// 关闭服务
likeManager.close();
```

## 运行示例

确保Redis服务器正在运行，然后执行以下命令：

```bash
# 编译项目
mvn clean compile

# 运行Redis点赞组件示例
mvn exec:java -Dexec.mainClass="com.redis.like.RedisLikeExample"
```

示例程序将演示以下功能：
1. 创建点赞管理器和配置
2. 执行点赞操作
3. 测试最小点赞间隔限制
4. 查看用户点赞历史
5. 获取统计信息

## 配置参数说明

| 参数名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| keyPrefix | String | "default" | Redis键前缀 |
| maxLikesPerUser | int | 100 | 每用户最大点赞数 |
| maxLikesPerIP | int | 50 | 每IP最大点赞数 |
| timeWindowSeconds | int | 3600 | 时间窗口（秒） |
| enableAntiBrush | boolean | true | 是否启用防刷 |
| minLikeIntervalMs | long | 1000 | 最小点赞间隔（毫秒） |
| enableWeightedLikes | boolean | false | 是否启用加权点赞 |
| enableLikeHistory | boolean | false | 是否启用点赞历史记录 |

## 核心类说明

1. **RedisLikeManager**
   - 统一入口类
   - 管理各个服务组件
   - 提供简洁的API

2. **RedisLikeService**
   - 核心点赞服务
   - 实现点赞/取消点赞逻辑
   - 处理防刷检查

3. **LikeCacheService**
   - 缓存服务
   - 实现二级缓存
   - 热点数据预热

4. **EventDrivenLikeService**
   - 事件驱动服务
   - 处理事件发布/订阅

5. **LikeMetricsService**
   - 性能监控服务
   - 收集性能指标
   - 健康检查

## 性能优化

1. **Pipeline批量操作**
   - 减少网络往返次数
   - 提高批量操作性能

2. **异步处理**
   - 异步点赞操作
   - 非阻塞事件处理

3. **缓存策略**
   - 本地缓存减少Redis访问
   - 热点数据预热
   - 智能缓存失效

4. **连接池管理**
   - Jedis连接池
   - 合理配置连接数

## 注意事项

1. 需要Redis 5.0+版本
2. 建议配置合适的JVM堆内存
3. 生产环境建议使用Redis集群
4. 定期清理过期数据
5. 监控性能指标

## 测试

使用JUnit 5进行单元测试，覆盖核心功能点。