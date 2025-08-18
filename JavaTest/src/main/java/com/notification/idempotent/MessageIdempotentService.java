package com.notification.idempotent;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消息幂等服务
 * 确保消息不会因为重复投递而产生副作用
 */
public class MessageIdempotentService {
    private final ConcurrentHashMap<String, MessageRecord> processedMessages;
    private final ScheduledExecutorService cleanupScheduler;
    private final long retentionHours;
    private final AtomicBoolean running;

    public MessageIdempotentService() {
        this(24); // 默认保留24小时
    }

    public MessageIdempotentService(long retentionHours) {
        this.processedMessages = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        this.retentionHours = retentionHours;
        this.running = new AtomicBoolean(false);
        
        // 启动清理任务
        startCleanupTask();
    }

    /**
     * 检查消息是否已处理
     * @param messageId 消息ID
     * @return true 如果消息已处理，false 如果消息未处理
     */
    public boolean isProcessed(String messageId) {
        MessageRecord record = processedMessages.get(messageId);
        return record != null && record.isProcessed();
    }

    /**
     * 标记消息为已处理
     * @param messageId 消息ID
     * @param result 处理结果
     */
    public void markProcessed(String messageId, Object result) {
        processedMessages.put(messageId, new MessageRecord(messageId, true, result));
    }

    /**
     * 标记消息为处理中
     * @param messageId 消息ID
     * @return true 如果成功标记为处理中，false 如果消息已存在
     */
    public boolean markProcessing(String messageId) {
        MessageRecord existing = processedMessages.putIfAbsent(messageId, 
            new MessageRecord(messageId, false, null));
        return existing == null;
    }

    /**
     * 获取消息处理结果
     * @param messageId 消息ID
     * @return 处理结果，如果消息未处理或处理中返回null
     */
    public Object getProcessingResult(String messageId) {
        MessageRecord record = processedMessages.get(messageId);
        return record != null && record.isProcessed() ? record.getResult() : null;
    }

    /**
     * 移除消息记录（用于手动清理）
     * @param messageId 消息ID
     */
    public void remove(String messageId) {
        processedMessages.remove(messageId);
    }

    /**
     * 获取已处理消息数量
     */
    public long getProcessedCount() {
        return processedMessages.values().stream()
                .filter(MessageRecord::isProcessed)
                .count();
    }

    /**
     * 获取处理中消息数量
     */
    public long getProcessingCount() {
        return processedMessages.values().stream()
                .filter(r -> !r.isProcessed())
                .count();
    }

    /**
     * 清理过期消息
     */
    private void startCleanupTask() {
        if (running.compareAndSet(false, true)) {
            cleanupScheduler.scheduleWithFixedDelay(
                this::cleanupExpiredMessages,
                retentionHours, // 首次延迟
                retentionHours, // 执行间隔
                TimeUnit.HOURS
            );
        }
    }

    private void cleanupExpiredMessages() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(retentionHours);
        processedMessages.entrySet().removeIf(entry -> 
            entry.getValue().getCreatedTime().isBefore(cutoffTime)
        );
    }

    /**
     * 停止清理任务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 清空所有消息记录
     */
    public void clearAll() {
        processedMessages.clear();
    }

    /**
     * 消息记录内部类
     */
    private static class MessageRecord {
        private final String messageId;
        private final boolean processed;
        private final Object result;
        private final LocalDateTime createdTime;

        public MessageRecord(String messageId, boolean processed, Object result) {
            this.messageId = messageId;
            this.processed = processed;
            this.result = result;
            this.createdTime = LocalDateTime.now();
        }

        public String getMessageId() {
            return messageId;
        }

        public boolean isProcessed() {
            return processed;
        }

        public Object getResult() {
            return result;
        }

        public LocalDateTime getCreatedTime() {
            return createdTime;
        }
    }
}