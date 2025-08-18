package com.redis.like.example;

import com.redis.like.*;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis点赞组件使用示例
 */
public class RedisLikeExample {
    
    public static void main(String[] args) {
        // 示例1：基本使用
        basicUsage();
        
        // 示例2：事件监听
        eventListenerExample();
        
        // 示例3：防刷机制
        antiBrushExample();
        
        // 示例4：排行榜功能
        rankingExample();
        
        // 示例5：缓存优化
        cacheExample();
    }
    
    /**
     * 基本使用示例
     */
    public static void basicUsage() {
        System.out.println("=== 基本使用示例 ===");
        
        // 创建Redis连接
        JedisPool jedisPool = new JedisPool("localhost", 6379);
        
        try {
            // 创建点赞服务
            RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
            
            // 点赞操作
            String targetId = "article_123";
            String userId = "user_456";
            String ip = "192.168.1.100";
            
            // 点赞
            RedisLikeService.LikeResult likeResult = likeManager.like(targetId, userId, ip);
            System.out.println("点赞结果: " + likeResult.getMessage() + ", 当前点赞数: " + likeResult.getCurrentCount());
            
            // 检查是否已点赞
            boolean hasLiked = likeManager.likeService.hasLiked(targetId, userId);
            System.out.println("是否已点赞: " + hasLiked);
            
            // 获取点赞数
            long likeCount = likeManager.getLikeCount(targetId);
            System.out.println("点赞数: " + likeCount);
            
            // 获取用户点赞列表
            Set<String> userLikes = likeManager.likeService.getUserLikes(userId);
            System.out.println("用户点赞列表: " + userLikes);
            
            // 取消点赞
            RedisLikeService.LikeResult unlikeResult = likeManager.unlike(targetId, userId);
            System.out.println("取消点赞结果: " + unlikeResult.getMessage());
            
        } finally {
            jedisPool.close();
        }
    }
    
    /**
     * 事件监听示例
     */
    public static void eventListenerExample() {
        System.out.println("\n=== 事件监听示例 ===");
        
        RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
        
        // 添加事件监听器
        likeManager.addEventListener(new LikeEventListener() {
            @Override
            public void onLike(LikeEvent event) {
                System.out.println("[事件] 用户 " + event.getUserId() + " 点赞了 " + event.getTargetId() + 
                    "，当前点赞数: " + event.getCurrentCount());
            }
            
            @Override
            public void onUnlike(UnlikeEvent event) {
                System.out.println("[事件] 用户 " + event.getUserId() + " 取消了点赞 " + event.getTargetId() + 
                    "，当前点赞数: " + event.getCurrentCount());
            }
            
            @Override
            public void onCountChange(CountChangeEvent event) {
                System.out.println("[事件] " + event.getTargetId() + " 点赞数变化: " + 
                    event.getOldCount() + " -> " + event.getNewCount() + 
                    " (变化: " + event.getDelta() + ")");
            }
        });
        
        // 模拟点赞操作
        likeManager.like("post_001", "user_A", "192.168.1.1");
        likeManager.like("post_001", "user_B", "192.168.1.2");
        likeManager.unlike("post_001", "user_A");
        
        likeManager.close();
    }
    
    /**
     * 防刷机制示例
     */
    public static void antiBrushExample() {
        System.out.println("\n=== 防刷机制示例 ===");
        
        RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
        
        // 注册严格的防刷配置
        RedisLikeService.LikeConfig strictConfig = new RedisLikeService.LikeConfig(
            "strict",    // key前缀
            5,           // 每用户最多5次点赞
            10,          // 每IP最多10次点赞
            60,          // 60秒时间窗口
            true         // 启用防刷
        );
        
        likeManager.registerConfig("strict", strictConfig);
        
        // 模拟防刷测试
        String targetId = "video_999";
        String userId = "test_user";
        String ip = "192.168.1.200";
        
        // 正常点赞
        for (int i = 1; i <= 3; i++) {
            RedisLikeService.LikeResult result = likeManager.like(targetId + "_" + i, userId, ip, "strict");
            System.out.println("第" + i + "次点赞: " + result.getMessage());
        }
        
        // 触发防刷
        RedisLikeService.LikeResult brushResult = likeManager.like(targetId + "_6", userId, ip, "strict");
        System.out.println("防刷触发: " + brushResult.getMessage());
        
        likeManager.close();
    }
    
    /**
     * 排行榜功能示例
     */
    public static void rankingExample() {
        System.out.println("\n=== 排行榜功能示例 ===");
        
        RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
        
        // 模拟多个内容的点赞
        String[] contentIds = {"content_A", "content_B", "content_C", "content_D", "content_E"};
        
        for (int i = 0; i < contentIds.length; i++) {
            for (int j = 0; j <= i * 10; j++) {
                likeManager.like(contentIds[i], "user_" + j, "192.168.1." + j);
            }
        }
        
        // 获取排行榜
        List<Map.Entry<String, Long>> rankings = likeManager.likeService.getRankings(5);
        System.out.println("点赞排行榜:");
        for (int i = 0; i < rankings.size(); i++) {
            Map.Entry<String, Long> entry = rankings.get(i);
            System.out.println("  " + (i + 1) + ". " + entry.getKey() + ": " + entry.getValue() + " 点赞");
        }
        
        // 获取统计信息
        Map<String, Object> stats = likeManager.getStats("default");
        System.out.println("统计信息: " + stats);
        
        likeManager.close();
    }
    
    /**
     * 缓存优化示例
     */
    public static void cacheExample() {
        System.out.println("\n=== 缓存优化示例 ===");
        
        RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
        
        String targetId = "cache_test";
        
        // 测试缓存性能
        long startTime = System.currentTimeMillis();
        
        // 第一次查询（从Redis获取）
        long count1 = likeManager.getLikeCount(targetId);
        long time1 = System.currentTimeMillis() - startTime;
        System.out.println("第一次查询: " + count1 + " (耗时: " + time1 + "ms)");
        
        // 第二次查询（从缓存获取）
        startTime = System.currentTimeMillis();
        long count2 = likeManager.getLikeCount(targetId);
        long time2 = System.currentTimeMillis() - startTime;
        System.out.println("第二次查询: " + count2 + " (耗时: " + time2 + "ms)");
        
        // 获取缓存统计
        Map<String, Object> cacheStats = likeManager.getCacheStats();
        System.out.println("缓存统计: " + cacheStats);
        
        // 测试实时数据
        likeManager.like(targetId, "cache_user", "192.168.1.1");
        
        // 缓存失效后重新获取
        likeManager.invalidateCache(targetId, "default");
        long realCount = likeManager.getRealLikeCount(targetId);
        System.out.println("实时点赞数: " + realCount);
        
        likeManager.close();
    }
    
    /**
     * 实际业务场景示例
     */
    public static void realWorldExample() {
        System.out.println("\n=== 实际业务场景示例 ===");
        
        RedisLikeManager likeManager = new RedisLikeManager("localhost", 6379);
        
        // 1. 文章点赞系统
        String articleConfig = "articles";
        RedisLikeService.LikeConfig articleConfigObj = new RedisLikeService.LikeConfig(
            "articles", 100, 50, 3600, true
        );
        likeManager.registerConfig(articleConfig, articleConfigObj);
        
        // 2. 评论点赞系统
        String commentConfig = "comments";
        RedisLikeService.LikeConfig commentConfigObj = new RedisLikeService.LikeConfig(
            "comments", 50, 30, 1800, true
        );
        likeManager.registerConfig(commentConfig, commentConfigObj);
        
        // 模拟业务操作
        String articleId = "article_20240101";
        String commentId = "comment_001";
        
        // 文章点赞
        likeManager.like(articleId, "user_A", "192.168.1.100", articleConfig);
        likeManager.like(articleId, "user_B", "192.168.1.101", articleConfig);
        
        // 评论点赞
        likeManager.like(commentId, "user_C", "192.168.1.102", commentConfig);
        
        // 获取统计
        System.out.println("文章点赞数: " + likeManager.getLikeCount(articleId, articleConfig));
        System.out.println("评论点赞数: " + likeManager.getLikeCount(commentId, commentConfig));
        
        // 文章排行榜
        List<Map.Entry<String, Long>> articleRankings = likeManager.likeService.getRankings(10, articleConfig);
        System.out.println("热门文章: " + articleRankings);
        
        likeManager.close();
    }
}