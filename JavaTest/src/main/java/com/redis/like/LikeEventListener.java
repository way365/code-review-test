package com.redis.like;

import java.util.EventListener;

/**
 * 点赞事件监听器接口
 */
public interface LikeEventListener extends EventListener {
    
    /**
     * 点赞事件回调
     * @param event 点赞事件
     */
    void onLike(LikeEvent event);
    
    /**
     * 取消点赞事件回调
     * @param event 取消点赞事件
     */
    void onUnlike(UnlikeEvent event);
    
    /**
     * 点赞数变化事件回调
     * @param event 点赞数变化事件
     */
    void onCountChange(CountChangeEvent event);
}

/**
 * 点赞事件
 */
public class LikeEvent {
    private final String targetId;
    private final String userId;
    private final String ip;
    private final long timestamp;
    private final long currentCount;
    
    public LikeEvent(String targetId, String userId, String ip, long timestamp, long currentCount) {
        this.targetId = targetId;
        this.userId = userId;
        this.ip = ip;
        this.timestamp = timestamp;
        this.currentCount = currentCount;
    }
    
    public String getTargetId() { return targetId; }
    public String getUserId() { return userId; }
    public String getIp() { return ip; }
    public long getTimestamp() { return timestamp; }
    public long getCurrentCount() { return currentCount; }
}

/**
 * 取消点赞事件
 */
public class UnlikeEvent {
    private final String targetId;
    private final String userId;
    private final long timestamp;
    private final long currentCount;
    
    public UnlikeEvent(String targetId, String userId, long timestamp, long currentCount) {
        this.targetId = targetId;
        this.userId = userId;
        this.timestamp = timestamp;
        this.currentCount = currentCount;
    }
    
    public String getTargetId() { return targetId; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
    public long getCurrentCount() { return currentCount; }
}

/**
 * 点赞数变化事件
 */
public class CountChangeEvent {
    private final String targetId;
    private final long oldCount;
    private final long newCount;
    private final long timestamp;
    
    public CountChangeEvent(String targetId, long oldCount, long newCount, long timestamp) {
        this.targetId = targetId;
        this.oldCount = oldCount;
        this.newCount = newCount;
        this.timestamp = timestamp;
    }
    
    public String getTargetId() { return targetId; }
    public long getOldCount() { return oldCount; }
    public long getNewCount() { return newCount; }
    public long getTimestamp() { return timestamp; }
    public long getDelta() { return newCount - oldCount; }
}