package com.notification.example;

import com.notification.manager.NotificationManager;
import com.notification.manager.AsyncNotificationManager;
import com.notification.metrics.NotificationMetrics;
import com.notification.service.EnhancedNotificationService;
import com.notification.service.EnhancedNotificationService.NotificationMessage;
import com.notification.service.EnhancedNotificationService.Priority;
import com.notification.service.EnhancedNotificationService.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 增强通知服务示例
 * 展示优化后的通知功能使用方法
 */
public class EnhancedNotificationExample {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedNotificationExample.class);
    
    public static void main(String[] args) {
        EnhancedNotificationExample example = new EnhancedNotificationExample();
        
        try {
            // 演示各种功能
            example.runBasicUsageDemo();
            example.runAsyncNotificationDemo();
            example.runBatchNotificationDemo();
            example.runPriorityNotificationDemo();
            example.runMonitoringDemo();
            example.runConnectionTestDemo();
            
        } catch (Exception e) {
            logger.error("示例运行异常", e);
        }
    }
    
    /**
     * 基础使用示例
     */
    public void runBasicUsageDemo() {
        logger.info("=== 基础使用示例 ===");
        
        NotificationManager manager = new NotificationManager();
        
        // 发送基础通知
        Map<String, Boolean> results = manager.sendToAll("这是一条测试消息", "系统通知");
        logger.info("基础通知发送结果: {}", results);
        
        // 发送任务完成通知
        Map<String, Boolean> taskResults = manager.sendTaskCompletionToAll(
            "数据同步任务", "成功", 1500);
        logger.info("任务完成通知发送结果: {}", taskResults);
        
        // 发送错误通知
        Map<String, Boolean> errorResults = manager.sendErrorToAll(
            "系统监控", "CPU使用率过高：85%");
        logger.info("错误通知发送结果: {}", errorResults);
    }
    
    /**
     * 异步通知示例
     */
    public void runAsyncNotificationDemo() {
        logger.info("=== 异步通知示例 ===");
        
        NotificationManager manager = new NotificationManager();
        AsyncNotificationManager asyncManager = manager.getAsyncManager();
        
        // 创建异步通知消息
        NotificationMessage message = new NotificationMessage(
            "异步通知测试", 
            "这是一条异步发送的测试消息",
            Priority.HIGH
        );
        
        // 异步发送到钉钉
        CompletableFuture<SendResult> future = asyncManager.sendNotificationAsync("dingtalk", message);
        
        future.whenComplete((result, throwable) -> {
            if (throwable == null) {
                logger.info("异步通知发送完成 - 成功: {}, 耗时: {}ms", 
                           result.isSuccess(), result.getDuration());
            } else {
                logger.error("异步通知发送异常", throwable);
            }
        });
        
        // 延迟通知示例
        CompletableFuture<SendResult> delayedFuture = asyncManager.sendNotificationAsync(
            "dingtalk", 
            new NotificationMessage("延迟通知", "这是一条5秒后发送的消息"), 
            5000
        );
        
        logger.info("延迟通知已提交，5秒后发送");
        
        try {
            // 等待异步操作完成
            SendResult result = future.get(10, TimeUnit.SECONDS);
            logger.info("异步通知结果: {}", result.isSuccess());
            
            SendResult delayedResult = delayedFuture.get(15, TimeUnit.SECONDS);
            logger.info("延迟通知结果: {}", delayedResult.isSuccess());
        } catch (Exception e) {
            logger.error("等待异步结果异常", e);
        }
        
        // 检查异步队列状态
        Map<String, Object> queueStatus = asyncManager.getQueueStatus();
        logger.info("异步队列状态: {}", queueStatus);
    }
    
    /**
     * 批量通知示例
     */
    public void runBatchNotificationDemo() {
        logger.info("=== 批量通知示例 ===");
        
        NotificationManager manager = new NotificationManager();
        AsyncNotificationManager asyncManager = manager.getAsyncManager();
        
        // 创建批量消息
        List<NotificationMessage> messages = Arrays.asList(
            new NotificationMessage("批量消息1", "第一条批量消息", Priority.NORMAL),
            new NotificationMessage("批量消息2", "第二条批量消息", Priority.HIGH),
            new NotificationMessage("批量消息3", "第三条批量消息", Priority.LOW)
        );
        
        // 异步批量发送
        List<CompletableFuture<SendResult>> futures = asyncManager.sendBatchNotificationsAsync(
            "dingtalk", messages);
        
        // 等待所有消息发送完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        logger.info("批量通知发送完成");
                        futures.forEach(future -> {
                            try {
                                SendResult sendResult = future.get();
                                logger.info("批量消息结果: ID={}, 成功={}, 耗时={}ms",
                                           sendResult.getMessageId(), sendResult.isSuccess(), sendResult.getDuration());
                            } catch (Exception e) {
                                logger.error("获取批量消息结果异常", e);
                            }
                        });
                    } else {
                        logger.error("批量通知发送异常", throwable);
                    }
                });
    }
    
    /**
     * 优先级通知示例
     */
    public void runPriorityNotificationDemo() {
        logger.info("=== 优先级通知示例 ===");
        
        NotificationManager manager = new NotificationManager();
        AsyncNotificationManager asyncManager = manager.getAsyncManager();
        
        // 发送不同优先级的消息
        Priority[] priorities = {Priority.LOW, Priority.NORMAL, Priority.HIGH, Priority.URGENT};
        
        for (Priority priority : priorities) {
            NotificationMessage message = new NotificationMessage(
                priority.name() + "优先级消息",
                "这是一条" + priority.name() + "优先级的测试消息",
                priority
            );
            
            CompletableFuture<SendResult> future = asyncManager.sendNotificationAsync("dingtalk", message);
            
            future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    logger.info("{}优先级消息发送完成 - 成功: {}", 
                               priority.name(), result.isSuccess());
                } else {
                    logger.error("{}优先级消息发送异常", priority.name(), throwable);
                }
            });
        }
    }
    
    /**
     * 监控指标示例
     */
    public void runMonitoringDemo() {
        logger.info("=== 监控指标示例 ===");
        
        NotificationManager manager = new NotificationManager();
        NotificationMetrics metrics = NotificationMetrics.getInstance();
        
        // 发送一些测试消息以生成监控数据
        for (int i = 0; i < 5; i++) {
            manager.sendToAll("监控测试消息 " + (i + 1), "监控测试");
            
            try {
                Thread.sleep(100); // 短暂等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 获取全局统计
        NotificationMetrics.GlobalStats globalStats = metrics.getGlobalStats();
        logger.info("全局统计 - 总请求: {}, 成功率: {:.2f}%, 平均响应时间: {:.2f}ms",
                   globalStats.getTotalRequests(), 
                   globalStats.getSuccessRate(), 
                   globalStats.getAvgResponseTime());
        
        // 获取服务统计
        Map<String, NotificationMetrics.ServiceMetrics> serviceStats = metrics.getAllServiceStats();
        serviceStats.forEach((serviceName, serviceMetrics) -> {
            logger.info("服务 {} 统计 - 总请求: {}, 成功率: {:.2f}%, 平均响应时间: {:.2f}ms",
                       serviceName,
                       serviceMetrics.getTotalRequests(),
                       serviceMetrics.getSuccessRate(),
                       serviceMetrics.getAvgResponseTime());
        });
        
        // 获取错误统计
        Map<String, Long> errorStats = metrics.getErrorStats();
        if (!errorStats.isEmpty()) {
            logger.info("错误统计: {}", errorStats);
        }
        
        // 生成完整报告
        String report = metrics.generateReport();
        logger.info("监控报告:\\n{}", report);
    }
    
    /**
     * 连接测试示例
     */
    public void runConnectionTestDemo() {
        logger.info("=== 连接测试示例 ===");
        
        NotificationManager manager = new NotificationManager();
        
        // 获取所有服务状态
        Map<String, String> servicesStatus = manager.getServicesStatus();
        logger.info("服务状态: {}", servicesStatus);
        
        // 测试所有连接
        Map<String, Boolean> connectionResults = manager.testAllConnections();
        logger.info("连接测试结果: {}", connectionResults);
        
        long healthyServices = connectionResults.values().stream()
                .mapToLong(healthy -> healthy ? 1 : 0)
                .sum();
        
        logger.info("健康服务数量: {}/{}", healthyServices, connectionResults.size());
    }
    
    /**
     * 性能测试示例
     */
    public void runPerformanceTest() {
        logger.info("=== 性能测试示例 ===");
        
        NotificationManager manager = new NotificationManager();
        AsyncNotificationManager asyncManager = manager.getAsyncManager();
        
        int messageCount = 100;
        long startTime = System.currentTimeMillis();
        
        // 并发发送大量消息
        CompletableFuture<Void>[] futures = new CompletableFuture[messageCount];
        
        for (int i = 0; i < messageCount; i++) {
            NotificationMessage message = new NotificationMessage(
                "性能测试消息 " + i,
                "这是第 " + i + " 条性能测试消息",
                Priority.NORMAL
            );
            
            futures[i] = asyncManager.sendNotificationAsync("dingtalk", message)
                    .thenAccept(result -> {
                        // 处理结果
                    });
        }
        
        // 等待所有消息发送完成
        CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("性能测试完成 - 发送 {} 条消息，总耗时: {}ms，平均: {:.2f}ms/条",
                       messageCount, duration, (double) duration / messageCount);
            
            // 显示最终监控数据
            NotificationMetrics metrics = NotificationMetrics.getInstance();
            NotificationMetrics.GlobalStats stats = metrics.getGlobalStats();
            logger.info("最终统计 - 总请求: {}, 成功率: {:.2f}%",
                       stats.getTotalRequests(), stats.getSuccessRate());
        });
    }
}