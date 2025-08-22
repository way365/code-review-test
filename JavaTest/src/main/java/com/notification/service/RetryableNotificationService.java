package com.notification.service;

import com.notification.config.NotificationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可重试的通知服务包装器
 * 为任何NotificationService实现添加重试机制
 */
public class RetryableNotificationService implements EnhancedNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryableNotificationService.class);
    
    private final NotificationService delegate;
    private final int maxRetryCount;
    private final long baseDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    
    public RetryableNotificationService(NotificationService delegate) {
        this.delegate = delegate;
        NotificationConfig config = NotificationConfig.getInstance();
        this.maxRetryCount = config.getNotificationRetryCount();
        this.baseDelayMs = config.getNotificationRetryDelay();
        this.backoffMultiplier = 2.0; // 指数退避倍数
        this.maxDelayMs = 30000; // 最大延迟30秒
    }
    
    public RetryableNotificationService(NotificationService delegate, int maxRetryCount, 
                                      long baseDelayMs, double backoffMultiplier, long maxDelayMs) {
        this.delegate = delegate;
        this.maxRetryCount = maxRetryCount;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
    }
    
    @Override
    public boolean sendNotification(String message, String title) {
        return executeWithRetry(() -> delegate.sendNotification(message, title), 
                               "sendNotification", message, title);
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration) {
        return executeWithRetry(() -> delegate.sendTaskCompletionNotification(taskName, status, duration),
                               "sendTaskCompletionNotification", taskName, status, String.valueOf(duration));
    }
    
    @Override
    public boolean sendErrorNotification(String taskName, String errorMessage) {
        return executeWithRetry(() -> delegate.sendErrorNotification(taskName, errorMessage),
                               "sendErrorNotification", taskName, errorMessage);
    }
    
    @Override
    public CompletableFuture<SendResult> sendNotificationAsync(NotificationMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            boolean success = sendNotificationWithPriority(message.getContent(), message.getTitle(), message.getPriority());
            long duration = System.currentTimeMillis() - startTime;
            return new SendResult(success, message.getMessageId(), success ? null : "发送失败", duration);
        });
    }
    
    @Override
    public CompletableFuture<SendResult> sendNotificationAsync(String message, String title, Priority priority) {
        NotificationMessage msg = new NotificationMessage(title, message, priority);
        return sendNotificationAsync(msg);
    }
    
    @Override
    public List<SendResult> sendBatchNotifications(List<NotificationMessage> messages) {
        return messages.stream()
                .map(msg -> {
                    long startTime = System.currentTimeMillis();
                    boolean success = sendNotificationWithPriority(msg.getContent(), msg.getTitle(), msg.getPriority());
                    long duration = System.currentTimeMillis() - startTime;
                    return new SendResult(success, msg.getMessageId(), success ? null : "发送失败", duration);
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public CompletableFuture<List<SendResult>> sendBatchNotificationsAsync(List<NotificationMessage> messages) {
        return CompletableFuture.supplyAsync(() -> sendBatchNotifications(messages));
    }
    
    @Override
    public boolean sendNotificationWithPriority(String message, String title, Priority priority) {
        // 根据优先级调整重试策略
        int adjustedRetryCount = adjustRetryCountByPriority(priority);
        return executeWithRetry(() -> delegate.sendNotification(message, title),
                               "sendNotificationWithPriority", message, title, priority.name(), adjustedRetryCount);
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration, Priority priority) {
        int adjustedRetryCount = adjustRetryCountByPriority(priority);
        return executeWithRetry(() -> delegate.sendTaskCompletionNotification(taskName, status, duration),
                               "sendTaskCompletionNotification", taskName, status, String.valueOf(duration), adjustedRetryCount);
    }
    
    @Override
    public CompletableFuture<SendResult> sendDelayedNotification(String message, String title, long delayMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMillis);
                long startTime = System.currentTimeMillis();
                boolean success = sendNotification(message, title);
                long duration = System.currentTimeMillis() - startTime;
                String messageId = "delayed_" + System.currentTimeMillis();
                return new SendResult(success, messageId, success ? null : "延迟发送失败", duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SendResult(false, "delayed_" + System.currentTimeMillis(), "延迟发送被中断", 0);
            }
        });
    }
    
    @Override
    public CompletableFuture<SendResult> sendScheduledNotification(String message, String title, long scheduleTime) {
        long delay = scheduleTime - System.currentTimeMillis();
        if (delay <= 0) {
            // 立即发送
            return sendNotificationAsync(message, title, Priority.NORMAL);
        }
        return sendDelayedNotification(message, title, delay);
    }
    
    @Override
    public String getServiceStatus() {
        if (delegate instanceof EnhancedNotificationService) {
            return ((EnhancedNotificationService) delegate).getServiceStatus();
        }
        return "RetryableWrapper[delegate=" + delegate.getClass().getSimpleName() + 
               ", maxRetries=" + maxRetryCount + ", baseDelay=" + baseDelayMs + "ms]";
    }
    
    @Override
    public boolean testConnection() {
        if (delegate instanceof EnhancedNotificationService) {
            return ((EnhancedNotificationService) delegate).testConnection();
        }
        // 尝试发送测试消息
        try {
            return delegate.sendNotification("连接测试", "系统测试");
        } catch (Exception e) {
            logger.warn("连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 根据优先级调整重试次数
     * @param priority 消息优先级
     * @return 调整后的重试次数
     */
    private int adjustRetryCountByPriority(Priority priority) {
        switch (priority) {
            case URGENT:
                return maxRetryCount * 2; // 紧急消息重试次数翻倍
            case HIGH:
                return (int) (maxRetryCount * 1.5); // 高优先级增加50%重试
            case LOW:
                return Math.max(1, maxRetryCount / 2); // 低优先级减少重试
            case NORMAL:
            default:
                return maxRetryCount;
        }
    }
    
    /**
     * 执行带重试的操作
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param params 操作参数（用于日志）
     * @return 操作结果
     */
    private boolean executeWithRetry(java.util.function.Supplier<Boolean> operation, String operationName, Object... params) {
        return executeWithRetry(operation, operationName, maxRetryCount, params);
    }
    
    private boolean executeWithRetry(java.util.function.Supplier<Boolean> operation, String operationName, 
                                   int retryCount, Object... params) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                boolean result = operation.get();
                if (result) {
                    if (attempt > 0) {
                        logger.info("操作 {} 在第 {} 次重试后成功", operationName, attempt);
                    }
                    return true;
                }
                
                if (attempt < retryCount) {
                    long delay = calculateDelay(attempt);
                    logger.warn("操作 {} 第 {} 次尝试失败，{} 毫秒后重试", operationName, attempt + 1, delay);
                    Thread.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("操作 {} 被中断", operationName);
                return false;
            } catch (Exception e) {
                lastException = e;
                if (attempt < retryCount) {
                    long delay = calculateDelay(attempt);
                    logger.warn("操作 {} 第 {} 次尝试异常，{} 毫秒后重试: {}", 
                              operationName, attempt + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("操作 {} 重试等待被中断", operationName);
                        return false;
                    }
                }
            }
        }
        
        logger.error("操作 {} 经过 {} 次重试后仍然失败，参数: {}", operationName, retryCount + 1, 
                    java.util.Arrays.toString(params), lastException);
        return false;
    }
    
    /**
     * 计算指数退避延迟时间
     * @param attempt 当前尝试次数（从0开始）
     * @return 延迟时间（毫秒）
     */
    private long calculateDelay(int attempt) {
        // 指数退避 + 随机抖动
        long exponentialDelay = (long) (baseDelayMs * Math.pow(backoffMultiplier, attempt));
        // 添加随机抖动，避免雪崩效应
        long jitter = ThreadLocalRandom.current().nextLong(0, exponentialDelay / 4);
        long totalDelay = exponentialDelay + jitter;
        
        return Math.min(totalDelay, maxDelayMs);
    }
}