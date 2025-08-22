package com.redis.like;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis点赞管理器
 * 统一管理点赞服务的配置和实例
 */
public class RedisLikeManager {
    
    private final JedisPool jedisPool;
    private final EventDrivenLikeService likeService;
    private final LikeCacheService cacheService;
    private final Map<String, RedisLikeService.LikeConfig> configs;
    
    public RedisLikeManager(String redisHost, int redisPort, String redisPassword) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
        } else {
            this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
        }
        
        this.likeService = new EventDrivenLikeService(jedisPool);
        this.cacheService = new LikeCacheService(jedisPool, 300, 10000); // 5分钟缓存，1万条上限
        this.configs = new ConcurrentHashMap<>();
        
        // 注册默认配置
        registerConfig("default", new RedisLikeService.LikeConfig("default"));
    }
    
    /**
     * 默认构造函数（无密码）
     */
    public RedisLikeManager(String redisHost, int redisPort) {
        this(redisHost, redisPort, null);
    }
    
    /**
     * 构造函数，支持自定义Jedis连接池配置
     */
    public RedisLikeManager(String redisHost, int redisPort, JedisPoolConfig poolConfig) {
        this(redisHost, redisPort, null, poolConfig);
    }
    
    /**
     * 构造函数，支持配置参数
     */
    public RedisLikeManager(String redisHost, int redisPort, String redisPassword, JedisPoolConfig poolConfig) {
        if (poolConfig == null) {
            poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(100);
            poolConfig.setMaxIdle(50);
            poolConfig.setMinIdle(10);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
        }
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
        } else {
            this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
        }
        
        this.likeService = new EventDrivenLikeService(jedisPool);
        this.cacheService = new LikeCacheService(jedisPool, 300, 10000); // 5分钟缓存，1万条上限
        this.configs = new ConcurrentHashMap<>();
        
        // 注册默认配置
        registerConfig("default", new RedisLikeService.LikeConfig("default"));
    }
    
    /**
     * 注册点赞配置
     */
    public void registerConfig(String key, RedisLikeService.LikeConfig config) {
        configs.put(key, config);
        likeService.registerConfig(key, config);
    }
    
    /**
     * 点赞
     */
    public RedisLikeService.LikeResult like(String targetId, String userId, String ip) {
        return likeService.like(targetId, userId, ip);
    }
    
    public RedisLikeService.LikeResult like(String targetId, String userId, String ip, String configKey) {
        return likeService.like(targetId, userId, ip, configKey);
    }
    
    /**
     * 取消点赞
     */
    public RedisLikeService.LikeResult unlike(String targetId, String userId) {
        return likeService.unlike(targetId, userId);
    }
    
    public RedisLikeService.LikeResult unlike(String targetId, String userId, String configKey) {
        return likeService.unlike(targetId, userId, configKey);
    }
    
    /**
     * 获取点赞数（带缓存）
     */
    public long getLikeCount(String targetId) {
        return getLikeCount(targetId, "default");
    }
    
    public long getLikeCount(String targetId, String configKey) {
        return cacheService.getCachedLikeCount(targetId, configKey);
    }
    
    /**
     * 获取实时点赞数（绕过缓存）
     */
    public long getRealLikeCount(String targetId) {
        return getRealLikeCount(targetId, "default");
    }
    
    public long getRealLikeCount(String targetId, String configKey) {
        return likeService.getLikeCount(targetId, configKey);
    }
    
    /**
     * 添加事件监听器
     */
    public void addEventListener(LikeEventListener listener) {
        likeService.addEventListener(listener);
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats(String configKey) {
        return likeService.getStats(configKey);
    }
    
    /**
     * 获取用户点赞历史记录
     */
    public List<String> getUserLikeHistory(String userId) {
        return likeService.getUserLikeHistory(userId);
    }
    
    /**
     * 获取缓存统计
     */
    public Map<String, Object> getCacheStats() {
        return cacheService.getCacheStats();
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        cacheService.clearCache();
    }
    
    /**
     * 使指定目标的缓存失效
     */
    public void invalidateCache(String targetId, String configKey) {
        cacheService.invalidateCache(targetId, configKey);
    }
    
    /**
     * 关闭所有服务
     */
    public void close() {
        try {
            likeService.close();
            cacheService.close();
            if (jedisPool != null) {
                jedisPool.close();
            }
        } catch (Exception e) {
            System.err.println("关闭RedisLikeManager失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取JedisPool（用于高级操作）
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}