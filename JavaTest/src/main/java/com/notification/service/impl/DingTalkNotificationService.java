package com.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.config.NotificationConfig;
import com.notification.http.HttpConnectionManager;
import com.notification.service.EnhancedNotificationService;
import org.apache.hc.client5.http.classic.methods.CloseableHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 钉钉通知服务实现
 */
public class DingTalkNotificationService implements EnhancedNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DingTalkNotificationService.class);
    
    private final String webhookUrl;
    private final String secret;
    private final ObjectMapper objectMapper;
    private final HttpConnectionManager connectionManager;
    private final NotificationConfig config;
    
    public DingTalkNotificationService(String webhookUrl, String secret) {
        this.webhookUrl = webhookUrl;
        this.secret = secret;
        this.objectMapper = new ObjectMapper();
        this.connectionManager = HttpConnectionManager.getInstance();
        this.config = NotificationConfig.getInstance();
    }
    
    @Override
    public boolean sendNotification(String message, String title) {
        Map<String, Object> content = new HashMap<>();
        content.put("msgtype", "markdown");
        
        Map<String, String> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", message);
        content.put("markdown", markdown);
        
        return sendRequest(content);
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration) {
        String message = String.format(
            "## 🎯 任务完成通知\n" +
            "**任务名称**: %s\n" +
            "**执行状态**: %s\n" +
            "**执行时长**: %d 毫秒\n" +
            "**完成时间**: %s",
            taskName, status, duration, new java.util.Date()
        );
        return sendNotification(message, "任务完成通知");
    }
    
    @Override
    public boolean sendErrorNotification(String taskName, String errorMessage) {
        String message = String.format(
            "## ❌ 任务异常通知\n" +
            "**任务名称**: %s\n" +
            "**错误信息**: %s\n" +
            "**发生时间**: %s",
            taskName, errorMessage, new java.util.Date()
        );
        return sendNotification(message, "任务异常通知");
    }
    
    private boolean sendRequest(Map<String, Object> content) {
        CloseableHttpClient httpClient = connectionManager.getHttpClient();
        
        try {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            
            String jsonContent = objectMapper.writeValueAsString(content);
            StringEntity entity = new StringEntity(jsonContent, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                if (statusCode == 200) {
                    logger.info("钉钉消息发送成功: {}", responseBody);
                    return true;
                } else {
                    logger.error("钉钉消息发送失败，状态码: {}, 响应: {}", statusCode, responseBody);
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("发送钉钉消息异常", e);
            return false;
        } catch (Exception e) {
            logger.error("钉钉消息发送未知异常", e);
            return false;
        }
    }
    
    // 实现EnhancedNotificationService的新方法
    
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
        // 根据优先级添加前缀标识
        String priorityPrefix = getPriorityPrefix(priority);
        String enhancedTitle = priorityPrefix + " " + title;
        return sendNotification(message, enhancedTitle);
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration, Priority priority) {
        String priorityPrefix = getPriorityPrefix(priority);
        String message = String.format(
            "## %s 🎯 任务完成通知\n" +
            "**任务名称**: %s\n" +
            "**执行状态**: %s\n" +
            "**执行时长**: %d 毫秒\n" +
            "**完成时间**: %s",
            priorityPrefix, taskName, status, duration, new java.util.Date()
        );
        return sendNotification(message, "任务完成通知");
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
            return sendNotificationAsync(message, title, Priority.NORMAL);
        }
        return sendDelayedNotification(message, title, delay);
    }
    
    @Override
    public String getServiceStatus() {
        return String.format("DingTalkNotificationService[webhook=%s, poolStats=%s]", 
                           maskWebhookUrl(webhookUrl), connectionManager.getPoolStats());
    }
    
    @Override
    public boolean testConnection() {
        try {
            return sendNotification("连接测试", "钉钉服务连接测试");
        } catch (Exception e) {
            logger.warn("钉钉服务连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 获取优先级前缀
     * @param priority 优先级
     * @return 前缀字符串
     */
    private String getPriorityPrefix(Priority priority) {
        switch (priority) {
            case URGENT:
                return "🚨[紧急]";
            case HIGH:
                return "⚠️[高优先级]";
            case LOW:
                return "ℹ️[低优先级]";
            case NORMAL:
            default:
                return "📝[普通]";
        }
    }
    
    /**
     * 屏蔽Webhook URL中的敏感信息
     * @param url 原始URL
     * @return 屏蔽后的URL
     */
    private String maskWebhookUrl(String url) {
        if (url == null || url.length() <= 10) {
            return "***";
        }
        return url.substring(0, 10) + "***" + url.substring(url.length() - 10);
    }
}