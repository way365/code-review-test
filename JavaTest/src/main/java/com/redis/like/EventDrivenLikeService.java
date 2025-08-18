package com.redis.like;

import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件驱动的点赞服务
 * 在基础点赞服务上增加了事件监听机制
 */
public class EventDrivenLikeService extends RedisLikeService {
    
    private final List<LikeEventListener> listeners;
    
    public EventDrivenLikeService(JedisPool jedisPool) {
        super(jedisPool);
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * 添加事件监听器
     */
    public void addEventListener(LikeEventListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 移除事件监听器
     */
    public void removeEventListener(LikeEventListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 带事件的点赞
     */
    @Override
    public LikeResult like(String targetId, String userId, String ip) {
        long oldCount = getLikeCount(targetId);
        LikeResult result = super.like(targetId, userId, ip);
        
        if (result.isSuccess()) {
            // 触发点赞事件
            LikeEvent event = new LikeEvent(targetId, userId, ip, 
                System.currentTimeMillis(), result.getCurrentCount());
            notifyLikeEvent(event);
            
            // 触发计数变化事件
            CountChangeEvent countEvent = new CountChangeEvent(
                targetId, oldCount, result.getCurrentCount(), System.currentTimeMillis());
            notifyCountChangeEvent(countEvent);
        }
        
        return result;
    }
    
    /**
     * 带事件的取消点赞
     */
    @Override
    public LikeResult unlike(String targetId, String userId) {
        long oldCount = getLikeCount(targetId);
        LikeResult result = super.unlike(targetId, userId);
        
        if (result.isSuccess()) {
            // 触发取消点赞事件
            UnlikeEvent event = new UnlikeEvent(targetId, userId, 
                System.currentTimeMillis(), result.getCurrentCount());
            notifyUnlikeEvent(event);
            
            // 触发计数变化事件
            CountChangeEvent countEvent = new CountChangeEvent(
                targetId, oldCount, result.getCurrentCount(), System.currentTimeMillis());
            notifyCountChangeEvent(countEvent);
        }
        
        return result;
    }
    
    /**
     * 通知点赞事件
     */
    private void notifyLikeEvent(LikeEvent event) {
        for (LikeEventListener listener : listeners) {
            try {
                listener.onLike(event);
            } catch (Exception e) {
                System.err.println("点赞事件处理失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 通知取消点赞事件
     */
    private void notifyUnlikeEvent(UnlikeEvent event) {
        for (LikeEventListener listener : listeners) {
            try {
                listener.onUnlike(event);
            } catch (Exception e) {
                System.err.println("取消点赞事件处理失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 通知计数变化事件
     */
    private void notifyCountChangeEvent(CountChangeEvent event) {
        for (LikeEventListener listener : listeners) {
            try {
                listener.onCountChange(event);
            } catch (Exception e) {
                System.err.println("计数变化事件处理失败: " + e.getMessage());
            }
        }
    }
}