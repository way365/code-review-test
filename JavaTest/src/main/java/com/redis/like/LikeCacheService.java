package com.redis.like;

import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 点赞缓存服务
 * 提供本地缓存和Redis缓存的混合方案
 */
public class LikeCacheService {
    
    private final JedisPool jedisPool;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CacheEntry> localCache;
    private final long cacheTtlSeconds;
    private final int maxCacheSize;
    
    public LikeCacheService(JedisPool jedisPool, long cacheTtlSeconds, int maxCacheSize) {
        this.jedisPool = jedisPool;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.maxCacheSize = maxCacheSize;
        this.localCache = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        startCacheCleanup();
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final long value;
        private final long timestamp;
        
        public CacheEntry(long value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
        
        public long getValue() { return value; }
        public long getTimestamp() { return timestamp; }
        public boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - timestamp > ttlSeconds * 1000;
        }
    }
    
    /**
     * 获取缓存的点赞数
     */
    public long getCachedLikeCount(String targetId, String configKey) {
        String cacheKey = targetId + ":" + configKey;
        
        // 检查本地缓存
        CacheEntry entry = localCache.get(cacheKey);
        if (entry != null && !entry.isExpired(cacheTtlSeconds)) {
            return entry.getValue();
        }
        
        // 从Redis获取
        try (var jedis = jedisPool.getResource()) {
            String likeCountKey = "like_count:" + configKey + targetId;
            String value = jedis.get(likeCountKey);
            long count = value != null ? Long.parseLong(value) : 0;
            
            // 更新本地缓存
            updateLocalCache(cacheKey, count);
            
            return count;
        }
    }
    
    /**
     * 更新缓存的点赞数
     */
    public void updateCachedLikeCount(String targetId, String configKey, long newCount) {
        String cacheKey = targetId + ":" + configKey;
        updateLocalCache(cacheKey, newCount);
    }
    
    /**
     * 使缓存失效
     */
    public void invalidateCache(String targetId, String configKey) {
        String cacheKey = targetId + ":" + configKey;
        localCache.remove(cacheKey);
    }
    
    /**
     * 批量获取缓存的点赞数
     */
    public Map<String, Long> getCachedLikeCounts(Iterable<String> targetIds, String configKey) {
        Map<String, Long> results = new HashMap<>();
        
        for (String targetId : targetIds) {
            results.put(targetId, getCachedLikeCount(targetId, configKey));
        }
        
        return results;
    }
    
    /**
     * 更新本地缓存
     */
    private void updateLocalCache(String cacheKey, long value) {
        // 检查缓存大小限制
        if (localCache.size() >= maxCacheSize) {
            // 简单的LRU策略：移除最旧的条目
            localCache.entrySet().removeIf(entry -> 
                entry.getValue().isExpired(cacheTtlSeconds));
        }
        
        localCache.put(cacheKey, new CacheEntry(value, System.currentTimeMillis()));
    }
    
    /**
     * 启动缓存清理任务
     */
    private void startCacheCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 清理过期缓存
                localCache.entrySet().removeIf(entry -> 
                    entry.getValue().isExpired(cacheTtlSeconds));
            } catch (Exception e) {
                System.err.println("缓存清理任务执行失败: " + e.getMessage());
            }
        }, cacheTtlSeconds / 2, cacheTtlSeconds / 2, TimeUnit.SECONDS);
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", localCache.size());
        stats.put("maxCacheSize", maxCacheSize);
        stats.put("cacheTtlSeconds", cacheTtlSeconds);
        
        long expiredCount = localCache.values().stream()
            .filter(entry -> entry.isExpired(cacheTtlSeconds))
            .count();
        stats.put("expiredCount", expiredCount);
        
        return stats;
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        localCache.clear();
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