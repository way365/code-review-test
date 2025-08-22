package com.notification.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 增强版通知服务接口
 * 扩展原有NotificationService，添加异步发送、批量发送等高级功能
 */
public interface EnhancedNotificationService extends NotificationService {
    
    /**
     * 消息优先级枚举
     */
    enum Priority {
        LOW(1), NORMAL(2), HIGH(3), URGENT(4);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * 通知消息实体
     */
    class NotificationMessage {
        private String title;
        private String content;
        private Priority priority;
        private long timestamp;
        private String messageId;
        
        public NotificationMessage(String title, String content) {
            this(title, content, Priority.NORMAL);
        }
        
        public NotificationMessage(String title, String content, Priority priority) {
            this.title = title;
            this.content = content;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
            this.messageId = generateMessageId();
        }
        
        private String generateMessageId() {
            return "msg_" + System.currentTimeMillis() + "_" + hashCode();
        }
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Priority getPriority() { return priority; }
        public void setPriority(Priority priority) { this.priority = priority; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
    }
    
    /**
     * 发送结果实体
     */
    class SendResult {
        private boolean success;
        private String messageId;
        private String errorMessage;
        private long timestamp;
        private long duration;
        
        public SendResult(boolean success, String messageId) {
            this.success = success;
            this.messageId = messageId;
            this.timestamp = System.currentTimeMillis();
        }
        
        public SendResult(boolean success, String messageId, String errorMessage, long duration) {
            this(success, messageId);
            this.errorMessage = errorMessage;
            this.duration = duration;
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
    }
    
    /**
     * 异步发送通知消息
     * @param message 通知消息
     * @return 发送结果的CompletableFuture
     */
    CompletableFuture<SendResult> sendNotificationAsync(NotificationMessage message);
    
    /**
     * 异步发送普通通知
     * @param message 消息内容
     * @param title 消息标题
     * @param priority 消息优先级
     * @return 发送结果的CompletableFuture
     */
    CompletableFuture<SendResult> sendNotificationAsync(String message, String title, Priority priority);
    
    /**
     * 批量发送通知
     * @param messages 通知消息列表
     * @return 发送结果列表
     */
    List<SendResult> sendBatchNotifications(List<NotificationMessage> messages);
    
    /**
     * 异步批量发送通知
     * @param messages 通知消息列表
     * @return 发送结果列表的CompletableFuture
     */
    CompletableFuture<List<SendResult>> sendBatchNotificationsAsync(List<NotificationMessage> messages);
    
    /**
     * 发送带优先级的通知
     * @param message 消息内容
     * @param title 消息标题
     * @param priority 消息优先级
     * @return 是否发送成功
     */
    boolean sendNotificationWithPriority(String message, String title, Priority priority);
    
    /**
     * 发送任务完成通知（带优先级）
     * @param taskName 任务名称
     * @param status 任务状态
     * @param duration 执行时长
     * @param priority 消息优先级
     * @return 是否发送成功
     */
    boolean sendTaskCompletionNotification(String taskName, String status, long duration, Priority priority);
    
    /**
     * 发送延迟通知
     * @param message 消息内容
     * @param title 消息标题
     * @param delayMillis 延迟时间（毫秒）
     * @return 发送结果的CompletableFuture
     */
    CompletableFuture<SendResult> sendDelayedNotification(String message, String title, long delayMillis);
    
    /**
     * 发送定时通知
     * @param message 消息内容
     * @param title 消息标题
     * @param scheduleTime 定时发送时间（时间戳）
     * @return 发送结果的CompletableFuture
     */
    CompletableFuture<SendResult> sendScheduledNotification(String message, String title, long scheduleTime);
    
    /**
     * 获取服务状态信息
     * @return 服务状态描述
     */
    String getServiceStatus();
    
    /**
     * 测试连接
     * @return 连接是否正常
     */
    boolean testConnection();
}