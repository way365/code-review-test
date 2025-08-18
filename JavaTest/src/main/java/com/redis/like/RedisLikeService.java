package com.redis.like;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.params.ZRangeParams;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis点赞服务
 * 功能：点赞/取消点赞、统计、排行榜、防刷机制
 */
public class RedisLikeService {
    
    private final JedisPool jedisPool;
    private final ScheduledExecutorService scheduler;
    private final Map<String, LikeConfig> configs;
    
    // Redis键前缀
    private static final String LIKE_SET_KEY = "likes:";
    private static final String LIKE_COUNT_KEY = "like_count:";
    private static final String LIKE_RANK_KEY = "like_rank:";
    private static final String USER_LIKE_KEY = "user_likes:";
    private static final String IP_LIMIT_KEY = "ip_limit:";
    private static final String USER_LIMIT_KEY = "user_limit:";
    
    public RedisLikeService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.configs = new ConcurrentHashMap<>();
        
        // 启动定时清理任务
        startCleanupTask();
    }
    
    /**
     * 点赞配置类
     */
    public static class LikeConfig {
        private final String keyPrefix;
        private final int maxLikesPerUser;
        private final int maxLikesPerIP;
        private final int timeWindowSeconds;
        private final boolean enableAntiBrush;
        
        public LikeConfig(String keyPrefix) {
            this(keyPrefix, 100, 50, 3600, true);
        }
        
        public LikeConfig(String keyPrefix, int maxLikesPerUser, int maxLikesPerIP, 
                         int timeWindowSeconds, boolean enableAntiBrush) {
            this.keyPrefix = keyPrefix;
            this.maxLikesPerUser = maxLikesPerUser;
            this.maxLikesPerIP = maxLikesPerIP;
            this.timeWindowSeconds = timeWindowSeconds;
            this.enableAntiBrush = enableAntiBrush;
        }
        
        // Getters
        public String getKeyPrefix() { return keyPrefix; }
        public int getMaxLikesPerUser() { return maxLikesPerUser; }
        public int getMaxLikesPerIP() { return maxLikesPerIP; }
        public int getTimeWindowSeconds() { return timeWindowSeconds; }
        public boolean isEnableAntiBrush() { return enableAntiBrush; }
    }
    
    /**
     * 点赞结果
     */
    public static class LikeResult {
        private final boolean success;
        private final String message;
        private final long currentCount;
        
        public LikeResult(boolean success, String message, long currentCount) {
            this.success = success;
            this.message = message;
            this.currentCount = currentCount;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getCurrentCount() { return currentCount; }
    }
    
    /**
     * 点赞记录
     */
    public static class LikeRecord {
        private final String userId;
        private final String targetId;
        private final long timestamp;
        
        public LikeRecord(String userId, String targetId, long timestamp) {
            this.userId = userId;
            this.targetId = targetId;
            this.timestamp = timestamp;
        }
        
        public String getUserId() { return userId; }
        public String getTargetId() { return targetId; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 注册点赞配置
     */
    public void registerConfig(String key, LikeConfig config) {
        configs.put(key, config);
    }
    
    /**
     * 点赞
     */
    public LikeResult like(String targetId, String userId, String ip) {
        return like(targetId, userId, ip, "default");
    }
    
    public LikeResult like(String targetId, String userId, String ip, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            // 防刷检查
            if (config.isEnableAntiBrush() && !checkAntiBrush(jedis, userId, ip, config)) {
                return new LikeResult(false, "操作过于频繁，请稍后再试", 0);
            }
            
            String likeSetKey = LIKE_SET_KEY + config.getKeyPrefix() + targetId;
            String likeCountKey = LIKE_COUNT_KEY + config.getKeyPrefix() + targetId;
            String likeRankKey = LIKE_RANK_KEY + config.getKeyPrefix();
            String userLikeKey = USER_LIKE_KEY + userId;
            
            // 检查是否已点赞
            if (jedis.sismember(likeSetKey, userId)) {
                long count = jedis.scard(likeSetKey);
                return new LikeResult(false, "已点赞", count);
            }
            
            // 执行点赞操作
            jedis.multi()
                .sadd(likeSetKey, userId)
                .incr(likeCountKey)
                .zincrby(likeRankKey, 1, targetId)
                .sadd(userLikeKey, targetId)
                .exec();
            
            // 设置过期时间（防止数据无限增长）
            jedis.expire(likeSetKey, 86400 * 30); // 30天
            jedis.expire(likeCountKey, 86400 * 30);
            jedis.expire(userLikeKey, 86400 * 30);
            
            long count = jedis.scard(likeSetKey);
            return new LikeResult(true, "点赞成功", count);
        }
    }
    
    /**
     * 取消点赞
     */
    public LikeResult unlike(String targetId, String userId) {
        return unlike(targetId, userId, "default");
    }
    
    public LikeResult unlike(String targetId, String userId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String likeSetKey = LIKE_SET_KEY + config.getKeyPrefix() + targetId;
            String likeCountKey = LIKE_COUNT_KEY + config.getKeyPrefix() + targetId;
            String likeRankKey = LIKE_RANK_KEY + config.getKeyPrefix();
            String userLikeKey = USER_LIKE_KEY + userId;
            
            // 检查是否已点赞
            if (!jedis.sismember(likeSetKey, userId)) {
                long count = jedis.scard(likeSetKey);
                return new LikeResult(false, "未点赞", count);
            }
            
            // 执行取消点赞操作
            jedis.multi()
                .srem(likeSetKey, userId)
                .decr(likeCountKey)
                .zincrby(likeRankKey, -1, targetId)
                .srem(userLikeKey, targetId)
                .exec();
            
            long count = jedis.scard(likeSetKey);
            return new LikeResult(true, "取消点赞成功", count);
        }
    }
    
    /**
     * 获取点赞数
     */
    public long getLikeCount(String targetId) {
        return getLikeCount(targetId, "default");
    }
    
    public long getLikeCount(String targetId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String likeCountKey = LIKE_COUNT_KEY + config.getKeyPrefix() + targetId;
            String count = jedis.get(likeCountKey);
            return count != null ? Long.parseLong(count) : 0;
        }
    }
    
    /**
     * 检查是否已点赞
     */
    public boolean hasLiked(String targetId, String userId) {
        return hasLiked(targetId, userId, "default");
    }
    
    public boolean hasLiked(String targetId, String userId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String likeSetKey = LIKE_SET_KEY + config.getKeyPrefix() + targetId;
            return jedis.sismember(likeSetKey, userId);
        }
    }
    
    /**
     * 获取排行榜
     */
    public List<Map.Entry<String, Long>> getRankings(int limit) {
        return getRankings(limit, "default");
    }
    
    public List<Map.Entry<String, Long>> getRankings(int limit, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String likeRankKey = LIKE_RANK_KEY + config.getKeyPrefix();
            Set<Tuple> rankings = jedis.zrevrangeWithScores(likeRankKey, 0, limit - 1);
            
            return rankings.stream()
                .map(tuple -> new AbstractMap.SimpleEntry<>(
                    tuple.getElement(), 
                    (long) tuple.getScore()))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 获取用户点赞列表
     */
    public Set<String> getUserLikes(String userId) {
        return getUserLikes(userId, "default");
    }
    
    public Set<String> getUserLikes(String userId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String userLikeKey = USER_LIKE_KEY + userId;
            return jedis.smembers(userLikeKey);
        }
    }
    
    /**
     * 批量获取点赞状态
     */
    public Map<String, Boolean> batchCheckLikes(Set<String> targetIds, String userId) {
        return batchCheckLikes(targetIds, userId, "default");
    }
    
    public Map<String, Boolean> batchCheckLikes(Set<String> targetIds, String userId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Boolean> results = new HashMap<>();
            
            for (String targetId : targetIds) {
                String likeSetKey = LIKE_SET_KEY + config.getKeyPrefix() + targetId;
                results.put(targetId, jedis.sismember(likeSetKey, userId));
            }
            
            return results;
        }
    }
    
    /**
     * 获取点赞详情
     */
    public List<LikeRecord> getLikeDetails(String targetId) {
        return getLikeDetails(targetId, "default");
    }
    
    public List<LikeRecord> getLikeDetails(String targetId, String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            String likeSetKey = LIKE_SET_KEY + config.getKeyPrefix() + targetId;
            Set<String> userIds = jedis.smembers(likeSetKey);
            
            return userIds.stream()
                .map(userId -> new LikeRecord(userId, targetId, System.currentTimeMillis()))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 防刷检查
     */
    private boolean checkAntiBrush(Jedis jedis, String userId, String ip, LikeConfig config) {
        String ipLimitKey = IP_LIMIT_KEY + ip;
        String userLimitKey = USER_LIMIT_KEY + userId;
        
        // IP限制检查
        String ipCount = jedis.get(ipLimitKey);
        if (ipCount != null && Integer.parseInt(ipCount) >= config.getMaxLikesPerIP()) {
            return false;
        }
        
        // 用户限制检查
        String userCount = jedis.get(userLimitKey);
        if (userCount != null && Integer.parseInt(userCount) >= config.getMaxLikesPerUser()) {
            return false;
        }
        
        // 增加计数
        jedis.incr(ipLimitKey);
        jedis.expire(ipLimitKey, config.getTimeWindowSeconds());
        
        jedis.incr(userLimitKey);
        jedis.expire(userLimitKey, config.getTimeWindowSeconds());
        
        return true;
    }
    
    /**
     * 清理过期数据
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // 清理过期的防刷记录
                jedis.keys(IP_LIMIT_KEY + "*").forEach(key -> {
                    if (jedis.ttl(key) == -1) {
                        jedis.expire(key, 3600); // 设置默认过期时间
                    }
                });
                
                jedis.keys(USER_LIMIT_KEY + "*").forEach(key -> {
                    if (jedis.ttl(key) == -1) {
                        jedis.expire(key, 3600);
                    }
                });
            } catch (Exception e) {
                // 记录错误但不中断任务
                System.err.println("清理任务执行失败: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats(String configKey) {
        LikeConfig config = configs.getOrDefault(configKey, new LikeConfig(configKey));
        
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Object> stats = new HashMap<>();
            
            // 获取所有点赞目标
            String likeRankKey = LIKE_RANK_KEY + config.getKeyPrefix();
            long totalTargets = jedis.zcard(likeRankKey);
            stats.put("totalTargets", totalTargets);
            
            // 获取总点赞数
            long totalLikes = jedis.zrangeWithScores(likeRankKey, 0, -1).stream()
                .mapToLong(tuple -> (long) tuple.getScore())
                .sum();
            stats.put("totalLikes", totalLikes);
            
            // 获取热门内容
            List<Map.Entry<String, Long>> topContent = getRankings(10, configKey);
            stats.put("topContent", topContent);
            
            return stats;
        }
    }
    
    /**
     * 关闭服务
     */
    public void close() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}