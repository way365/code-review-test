package com.notification.manager;

import com.notification.config.NotificationConfig;
import com.notification.metrics.MonitoredNotificationService;
import com.notification.ratelimit.RateLimitedNotificationService;
import com.notification.service.NotificationService;
import com.notification.service.RetryableNotificationService;
import com.notification.service.impl.DingTalkNotificationService;
import com.notification.service.impl.FeishuNotificationService;
import com.notification.service.impl.WeChatNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强版通知管理器
 * 集成配置管理、监控、重试、限流等功能
 */
public class NotificationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationManager.class);
    
    private final Map<String, NotificationService> services = new ConcurrentHashMap<>();
    private final NotificationConfig config;
    private final AsyncNotificationManager asyncManager;
    
    public NotificationManager() {
        this.config = NotificationConfig.getInstance();
        this.asyncManager = new AsyncNotificationManager();
        
        // 初始化默认服务
        initializeDefaultServices();
        
        logger.info("增强版通知管理器已初始化，已加载 {} 个服务", services.size());
    }
    
    /**
     * 添加通知服务（带装饰器）
     * @param name 服务名称
     * @param service 通知服务实例
     * @param enableRetry 是否启用重试
     * @param enableRateLimit 是否启用限流
     * @param enableMonitoring 是否启用监控
     */
    public void addService(String name, NotificationService service, 
                          boolean enableRetry, boolean enableRateLimit, boolean enableMonitoring) {
        NotificationService wrappedService = service;
        
        // 应用装饰器（按顺序：重试 -> 限流 -> 监控）
        if (enableRetry) {
            wrappedService = new RetryableNotificationService(wrappedService);
            logger.debug("为服务 {} 启用重试功能", name);
        }
        
        if (enableRateLimit) {
            wrappedService = new RateLimitedNotificationService(wrappedService);
            logger.debug("为服务 {} 启用限流功能", name);
        }
        
        if (enableMonitoring) {
            wrappedService = new MonitoredNotificationService(wrappedService, name);
            logger.debug("为服务 {} 启用监控功能", name);
        }
        
        services.put(name, wrappedService);
        asyncManager.addService(name, wrappedService);
        
        logger.info("已添加通知服务: {} (重试: {}, 限流: {}, 监控: {})", 
                   name, enableRetry, enableRateLimit, enableMonitoring);
    }
    
    /**
     * 获取通知服务
     * @param name 服务名称
     * @return 通知服务实例
     */
    public NotificationService getService(String name) {
        return services.get(name);
    }
    
    /**
     * 发送通知到所有服务（同步）
     * @param message 消息内容
     * @param title 消息标题
     * @return 发送结果
     */
    public Map<String, Boolean> sendToAll(String message, String title) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        
        // 并发发送到所有服务
        CompletableFuture<Void>[] futures = services.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                try {
                    boolean success = entry.getValue().sendNotification(message, title);
                    results.put(entry.getKey(), success);
                } catch (Exception e) {
                    logger.error("发送通知到服务 {} 异常", entry.getKey(), e);
                    results.put(entry.getKey(), false);
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        long successCount = results.values().stream().mapToLong(success -> success ? 1 : 0).sum();
        logger.info("发送通知完成 - 总数: {}, 成功: {}, 失败: {}", 
                   results.size(), successCount, results.size() - successCount);
        
        return results;
    }
    
    /**
     * 发送任务完成通知到所有服务（同步）
     * @param taskName 任务名称
     * @param status 任务状态
     * @param duration 执行时长
     * @return 发送结果
     */
    public Map<String, Boolean> sendTaskCompletionToAll(String taskName, String status, long duration) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        
        CompletableFuture<Void>[] futures = services.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                try {
                    boolean success = entry.getValue().sendTaskCompletionNotification(taskName, status, duration);
                    results.put(entry.getKey(), success);
                } catch (Exception e) {
                    logger.error("发送任务完成通知到服务 {} 异常", entry.getKey(), e);
                    results.put(entry.getKey(), false);
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        return results;
    }
    
    /**
     * 发送异常通知到所有服务（同步）
     * @param taskName 任务名称
     * @param errorMessage 错误信息
     * @return 发送结果
     */
    public Map<String, Boolean> sendErrorToAll(String taskName, String errorMessage) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        
        CompletableFuture<Void>[] futures = services.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                try {
                    boolean success = entry.getValue().sendErrorNotification(taskName, errorMessage);
                    results.put(entry.getKey(), success);
                } catch (Exception e) {
                    logger.error("发送异常通知到服务 {} 异常", entry.getKey(), e);
                    results.put(entry.getKey(), false);
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        return results;
    }
    
    private void initializeDefaultServices() {
        try {
            // 钉钉服务
            String dingTalkWebhook = config.getDingTalkWebhook();
            String dingTalkSecret = config.getDingTalkSecret();
            if (!dingTalkWebhook.isEmpty()) {
                DingTalkNotificationService dingTalkService = new DingTalkNotificationService(dingTalkWebhook, dingTalkSecret);
                addService("dingtalk", dingTalkService);
            }
            
            // 飞书服务
            String feishuWebhook = config.getFeishuWebhook();
            if (!feishuWebhook.isEmpty()) {
                FeishuNotificationService feishuService = new FeishuNotificationService(feishuWebhook);
                addService("feishu", feishuService);
            }
            
            // 微信服务
            String wechatAppId = config.getWeChatAppId();
            String wechatAppSecret = config.getWeChatAppSecret();
            String wechatTemplateId = config.getWeChatTemplateId();
            String wechatOpenId = config.getWeChatOpenId();
            
            if (!wechatAppId.isEmpty() && !wechatAppSecret.isEmpty() && 
                !wechatTemplateId.isEmpty() && !wechatOpenId.isEmpty()) {
                WeChatNotificationService wechatService = new WeChatNotificationService(
                    wechatAppId, wechatAppSecret, wechatTemplateId, wechatOpenId);
                addService("wechat", wechatService);
            }
            
            logger.info("默认通知服务初始化完成，共加载 {} 个服务", services.size());
    /**
     * 添加通知服务（默认启用所有功能）
     * @param name 服务名称
     * @param service 通知服务实例
     */
    public void addService(String name, NotificationService service) {
        addService(name, service, true, true, true);
    }
    
    /**
     * 异步发送通知到所有服务
     * @param message 消息内容
     * @param title 消息标题
     * @return 发送结果Future
     */
    public CompletableFuture<Map<String, Boolean>> sendToAllAsync(String message, String title) {
        return CompletableFuture.supplyAsync(() -> sendToAll(message, title));
    }
    
    /**
     * 获取异步管理器
     * @return 异步通知管理器
     */
    public AsyncNotificationManager getAsyncManager() {
        return asyncManager;
    }
    
    /**
     * 获取所有服务的状态
     * @return 服务状态映射
     */
    public Map<String, String> getServicesStatus() {
        Map<String, String> statusMap = new HashMap<>();
        
        services.forEach((name, service) -> {
            try {
                if (service instanceof com.notification.service.EnhancedNotificationService) {
                    statusMap.put(name, ((com.notification.service.EnhancedNotificationService) service).getServiceStatus());
                } else {
                    statusMap.put(name, service.getClass().getSimpleName() + "[基础服务]");
                }
            } catch (Exception e) {
                statusMap.put(name, "状态获取失败: " + e.getMessage());
            }
        });
        
        return statusMap;
    }
    
    /**
     * 测试所有服务连接
     * @return 连接测试结果
     */
    public Map<String, Boolean> testAllConnections() {
        Map<String, Boolean> results = new HashMap<>();
        
        services.forEach((name, service) -> {
            try {
                if (service instanceof com.notification.service.EnhancedNotificationService) {
                    results.put(name, ((com.notification.service.EnhancedNotificationService) service).testConnection());
                } else {
                    // 发送测试消息
                    results.put(name, service.sendNotification("连接测试", "系统测试"));
                }
            } catch (Exception e) {
                logger.warn("测试服务 {} 连接异常", name, e);
                results.put(name, false);
            }
        });
        
        return results;
    }
    
    /**
     * 优雅关闭
     */
    public void shutdown() {
        logger.info("正在关闭通知管理器...");
        
        try {
            asyncManager.shutdown();
        } catch (Exception e) {
            logger.warn("关闭异步管理器异常", e);
        }
        
        services.clear();
        logger.info("通知管理器已关闭");
    }