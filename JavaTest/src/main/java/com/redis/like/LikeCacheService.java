package com.redis.like;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 点赞缓存服务
 * 提供本地缓存和Redis缓存的混合方案
 */
public class LikeCacheService {
    
    private final JedisPool jedisPool;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService preloadExecutor;
    private final Map<String, CacheEntry> localCache;
    private final Map<String, CacheEntry> l2Cache; // 二级缓存
    private final long cacheTtlSeconds;
    private final long l2CacheTtlSeconds;
    private final int maxCacheSize;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;
    private final AtomicLong totalRequests;
    private final Set<String> hotKeys; // 热点数据
    
    public LikeCacheService(JedisPool jedisPool, long cacheTtlSeconds, int maxCacheSize) {
        this.jedisPool = jedisPool;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.l2CacheTtlSeconds = cacheTtlSeconds * 5; // 二级缓存更长时间
        this.maxCacheSize = maxCacheSize;
        this.localCache = new ConcurrentHashMap<>();
        this.l2Cache = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.preloadExecutor = Executors.newFixedThreadPool(2);
        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.totalRequests = new AtomicLong(0);
        this.hotKeys = ConcurrentHashMap.newKeySet();
        
        startCacheCleanup();
        startHotKeyDetection();
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final long value;
        private final long timestamp;
        private final AtomicLong accessCount;
        private final AtomicReference<Long> lastAccessTime;
        
        public CacheEntry(long value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
            this.accessCount = new AtomicLong(1);
            this.lastAccessTime = new AtomicReference<>(timestamp);
        }
        
        public long getValue() { 
            accessCount.incrementAndGet();
            lastAccessTime.set(System.currentTimeMillis());
            return value; 
        }
        
        public long getTimestamp() { return timestamp; }
        public long getAccessCount() { return accessCount.get(); }
        public long getLastAccessTime() { return lastAccessTime.get(); }
        
        public boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - timestamp > ttlSeconds * 1000;
        }
        
        public boolean isHot(long threshold) {
            return accessCount.get() >= threshold;
        }
    }
    
    /**
     * 获取缓存的点赞数
     */
    public long getCachedLikeCount(String targetId, String configKey) {
        String cacheKey = targetId + ":" + configKey;
        totalRequests.incrementAndGet();
        
        // 检查一级缓存
        CacheEntry entry = localCache.get(cacheKey);
        if (entry != null && !entry.isExpired(cacheTtlSeconds)) {
            hitCount.incrementAndGet();
            
            // 检查是否为热点数据
            if (entry.isHot(10)) {
                hotKeys.add(cacheKey);
            }
            
            return entry.getValue();
        }
        
        // 检查二级缓存
        CacheEntry l2Entry = l2Cache.get(cacheKey);
        if (l2Entry != null && !l2Entry.isExpired(l2CacheTtlSeconds)) {
            hitCount.incrementAndGet();
            // 从二级缓存升级到一级缓存
            updateLocalCache(cacheKey, l2Entry.getValue());
            return l2Entry.getValue();
        }
        
        missCount.incrementAndGet();
        
        // 从Redis获取
        try (var jedis = jedisPool.getResource()) {
            String likeCountKey = "like_count:" + configKey + targetId;
            String value = jedis.get(likeCountKey);
            long count = value != null ? Long.parseLong(value) : 0;
            
            // 更新缓存
            updateLocalCache(cacheKey, count);
            updateL2Cache(cacheKey, count);
            
            return count;
        } catch (Exception e) {
            System.err.println("获取点赞数失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 更新二级缓存
     */
    private void updateL2Cache(String cacheKey, long value) {
        l2Cache.put(cacheKey, new CacheEntry(value, System.currentTimeMillis()));
    }

    /**
     * 启动热点检测任务
     */
    private void startHotKeyDetection() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 为热点数据预热缓存
                for (String hotKey : hotKeys) {
                    if (!localCache.containsKey(hotKey) || 
                        localCache.get(hotKey).isExpired(cacheTtlSeconds)) {
                        preloadExecutor.submit(() -> {
                            String[] parts = hotKey.split(":");
                            if (parts.length == 2) {
                                getCachedLikeCount(parts[0], parts[1]);
                            }
                        });
                    }
                }
                
                // 清理过期的热点key
                hotKeys.removeIf(key -> {
                    CacheEntry entry = localCache.get(key);
                    return entry == null || !entry.isHot(5);
                });
            } catch (Exception e) {
                System.err.println("热点检测任务执行失败: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 批量预热缓存
     */
    public void preloadCache(Set<String> targetIds, String configKey) {
        CompletableFuture.runAsync(() -> {
            try (var jedis = jedisPool.getResource()) {
                Pipeline pipeline = jedis.pipelined();
                Map<String, Response<String>> responses = new HashMap<>();
                
                for (String targetId : targetIds) {
                    String likeCountKey = "like_count:" + configKey + targetId;
                    responses.put(targetId, pipeline.get(likeCountKey));
                }
                
                pipeline.sync();
                
                for (String targetId : targetIds) {
                    String value = responses.get(targetId).get();
                    long count = value != null ? Long.parseLong(value) : 0;
                    String cacheKey = targetId + ":" + configKey;
                    updateLocalCache(cacheKey, count);
                    updateL2Cache(cacheKey, count);
                }
            } catch (Exception e) {
                System.err.println("批量预热缓存失败: " + e.getMessage());
            }
        }, preloadExecutor);
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
                // 清理过期的一级缓存
                localCache.entrySet().removeIf(entry -> 
                    entry.getValue().isExpired(cacheTtlSeconds));
                
                // 清理过期的二级缓存
                l2Cache.entrySet().removeIf(entry -> 
                    entry.getValue().isExpired(l2CacheTtlSeconds));
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
        stats.put("l2CacheSize", l2Cache.size());
        stats.put("maxCacheSize", maxCacheSize);
        stats.put("cacheTtlSeconds", cacheTtlSeconds);
        stats.put("l2CacheTtlSeconds", l2CacheTtlSeconds);
        stats.put("hitCount", hitCount.get());
        stats.put("missCount", missCount.get());
        stats.put("totalRequests", totalRequests.get());
        stats.put("hitRate", totalRequests.get() > 0 ? 
            (double) hitCount.get() / totalRequests.get() : 0.0);
        stats.put("hotKeysCount", hotKeys.size());
        
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
        l2Cache.clear();
        hotKeys.clear();
        hitCount.set(0);
        missCount.set(0);
        totalRequests.set(0);
    }

    /**
     * 关闭服务
     */
    public void close() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
            
            if (preloadExecutor != null && !preloadExecutor.isShutdown()) {
                preloadExecutor.shutdown();
                if (!preloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    preloadExecutor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("关闭缓存服务被中断: " + e.getMessage());
        }
    }
}