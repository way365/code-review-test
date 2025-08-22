package com.redis.like;

import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件驱动的点赞服务
 * 在基础点赞服务上增加了事件监听机制
 */
public class EventDrivenLikeService extends RedisLikeService {
    
    private final List<LikeEventListener> listeners;
    private final ExecutorService eventExecutor;
    private final BlockingQueue<EventTask> eventQueue;
    private final AtomicLong eventProcessedCount;
    private final AtomicLong eventFailedCount;
    private volatile boolean shutdown = false;
    
    public EventDrivenLikeService(JedisPool jedisPool) {
        super(jedisPool);
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.eventQueue = new LinkedBlockingQueue<>(10000); // 事件队列
        this.eventProcessedCount = new AtomicLong(0);
        this.eventFailedCount = new AtomicLong(0);
        
        // 启动事件处理线程
        startEventProcessors();
    }
    
    /**
     * 事件任务
     */
    private static class EventTask {
        private final String type;
        private final Object event;
        private final long timestamp;
        private int retryCount;
        
        public EventTask(String type, Object event) {
            this.type = type;
            this.event = event;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
        }
        
        public String getType() { return type; }
        public Object getEvent() { return event; }
        public long getTimestamp() { return timestamp; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetry() { this.retryCount++; }
    }
    
    /**
     * 启动事件处理器
     */
    private void startEventProcessors() {
        int processorCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        
        for (int i = 0; i < processorCount; i++) {
            eventExecutor.submit(() -> {
                while (!shutdown) {
                    try {
                        EventTask task = eventQueue.poll(1, TimeUnit.SECONDS);
                        if (task != null) {
                            processEvent(task);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("事件处理器错误: " + e.getMessage());
                    }
                }
            });
        }
    }
    
    /**
     * 处理事件
     */
    private void processEvent(EventTask task) {
        try {
            switch (task.getType()) {
                case "LIKE":
                    notifyLikeEvent((LikeEvent) task.getEvent());
                    break;
                case "UNLIKE":
                    notifyUnlikeEvent((UnlikeEvent) task.getEvent());
                    break;
                case "COUNT_CHANGE":
                    notifyCountChangeEvent((CountChangeEvent) task.getEvent());
                    break;
            }
            eventProcessedCount.incrementAndGet();
        } catch (Exception e) {
            eventFailedCount.incrementAndGet();
            
            // 重试机制
            if (task.getRetryCount() < 3) {
                task.incrementRetry();
                try {
                    Thread.sleep(1000 * task.getRetryCount()); // 指数退避
                    eventQueue.offer(task, 1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } else {
                System.err.println("事件处理失败，超过最大重试次数: " + task.getType() + ", " + e.getMessage());
            }
        }
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
            // 异步触发点赞事件
            LikeEvent event = new LikeEvent(targetId, userId, ip, 
                System.currentTimeMillis(), result.getCurrentCount());
            offerEvent("LIKE", event);
            
            // 异步触发计数变化事件
            CountChangeEvent countEvent = new CountChangeEvent(
                targetId, oldCount, result.getCurrentCount(), System.currentTimeMillis());
            offerEvent("COUNT_CHANGE", countEvent);
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
            // 异步触发取消点赞事件
            UnlikeEvent event = new UnlikeEvent(targetId, userId, 
                System.currentTimeMillis(), result.getCurrentCount());
            offerEvent("UNLIKE", event);
            
            // 异步触发计数变化事件
            CountChangeEvent countEvent = new CountChangeEvent(
                targetId, oldCount, result.getCurrentCount(), System.currentTimeMillis());
            offerEvent("COUNT_CHANGE", countEvent);
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
     * 异步提交事件
     */
    private void offerEvent(String type, Object event) {
        EventTask task = new EventTask(type, event);
        try {
            if (!eventQueue.offer(task, 100, TimeUnit.MILLISECONDS)) {
                System.err.println("事件队列已满，丢弃事件: " + type);
                eventFailedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("事件提交被中断: " + type);
        }
    }
    
    /**
     * 获取事件统计信息
     */
    public Map<String, Object> getEventStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("eventProcessedCount", eventProcessedCount.get());
        stats.put("eventFailedCount", eventFailedCount.get());
        stats.put("eventQueueSize", eventQueue.size());
        stats.put("listenersCount", listeners.size());
        return stats;
    }
    
    /**
     * 关闭服务
     */
    @Override
    public void close() {
        shutdown = true;
        
        try {
            if (eventExecutor != null && !eventExecutor.isShutdown()) {
                eventExecutor.shutdown();
                if (!eventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    eventExecutor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("关闭事件服务被中断: " + e.getMessage());
        }
        
        super.close();
    }