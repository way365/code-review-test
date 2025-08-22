package com.notification.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * 通知指标监控系统
 * 提供发送成功率、响应时间、错误统计等监控指标
 */
public class NotificationMetrics {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationMetrics.class);
    
    private static volatile NotificationMetrics instance;
    
    // 全局统计
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalResponseTime = new LongAdder();
    
    // 按服务统计
    private final Map<String, ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
    
    // 按优先级统计
    private final Map<String, PriorityMetrics> priorityMetricsMap = new ConcurrentHashMap<>();
    
    // 错误统计
    private final Map<String, AtomicLong> errorCountMap = new ConcurrentHashMap<>();
    
    // 响应时间分布统计
    private final ResponseTimeDistribution responseTimeDistribution = new ResponseTimeDistribution();
    
    // 时间窗口统计（最近一小时）
    private final TimeWindowStats timeWindowStats = new TimeWindowStats();
    
    private NotificationMetrics() {
        // 启动定期统计任务
        startPeriodicLogging();
    }
    
    /**
     * 获取单例实例
     * @return NotificationMetrics实例
     */
    public static NotificationMetrics getInstance() {
        if (instance == null) {
            synchronized (NotificationMetrics.class) {
                if (instance == null) {
                    instance = new NotificationMetrics();
                }
            }
        }
        return instance;
    }
    
    /**
     * 记录请求指标
     * @param serviceName 服务名称
     * @param success 是否成功
     * @param responseTimeMs 响应时间（毫秒）
     * @param priority 优先级
     * @param errorMessage 错误信息（如果有）
     */
    public void recordRequest(String serviceName, boolean success, long responseTimeMs, 
                            String priority, String errorMessage) {
        // 全局统计
        totalRequests.increment();
        totalResponseTime.add(responseTimeMs);
        
        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
            if (errorMessage != null) {\n                errorCountMap.computeIfAbsent(errorMessage, k -> new AtomicLong()).incrementAndGet();\n            }\n        }\n        \n        // 按服务统计\n        serviceMetricsMap.computeIfAbsent(serviceName, ServiceMetrics::new)\n                        .recordRequest(success, responseTimeMs);\n        \n        // 按优先级统计\n        priorityMetricsMap.computeIfAbsent(priority, PriorityMetrics::new)\n                         .recordRequest(success, responseTimeMs);\n        \n        // 响应时间分布\n        responseTimeDistribution.record(responseTimeMs);\n        \n        // 时间窗口统计\n        timeWindowStats.record(success, responseTimeMs);\n    }\n    \n    /**\n     * 获取全局统计信息\n     * @return 全局统计\n     */\n    public GlobalStats getGlobalStats() {\n        long total = totalRequests.sum();\n        long successful = successfulRequests.sum();\n        long failed = failedRequests.sum();\n        long totalTime = totalResponseTime.sum();\n        \n        double successRate = total > 0 ? (double) successful / total * 100 : 0.0;\n        double avgResponseTime = total > 0 ? (double) totalTime / total : 0.0;\n        \n        return new GlobalStats(total, successful, failed, successRate, avgResponseTime);\n    }\n    \n    /**\n     * 获取服务统计信息\n     * @param serviceName 服务名称\n     * @return 服务统计\n     */\n    public ServiceMetrics getServiceStats(String serviceName) {\n        return serviceMetricsMap.get(serviceName);\n    }\n    \n    /**\n     * 获取所有服务统计信息\n     * @return 所有服务统计\n     */\n    public Map<String, ServiceMetrics> getAllServiceStats() {\n        return new HashMap<>(serviceMetricsMap);\n    }\n    \n    /**\n     * 获取优先级统计信息\n     * @return 优先级统计\n     */\n    public Map<String, PriorityMetrics> getPriorityStats() {\n        return new HashMap<>(priorityMetricsMap);\n    }\n    \n    /**\n     * 获取错误统计信息\n     * @return 错误统计（按错误消息分组）\n     */\n    public Map<String, Long> getErrorStats() {\n        return errorCountMap.entrySet().stream()\n                .collect(Collectors.toMap(\n                    Map.Entry::getKey,\n                    entry -> entry.getValue().get()\n                ));\n    }\n    \n    /**\n     * 获取响应时间分布统计\n     * @return 响应时间分布\n     */\n    public ResponseTimeDistribution getResponseTimeDistribution() {\n        return responseTimeDistribution;\n    }\n    \n    /**\n     * 获取时间窗口统计\n     * @return 时间窗口统计\n     */\n    public TimeWindowStats getTimeWindowStats() {\n        return timeWindowStats;\n    }\n    \n    /**\n     * 重置所有统计信息\n     */\n    public void reset() {\n        totalRequests.reset();\n        successfulRequests.reset();\n        failedRequests.reset();\n        totalResponseTime.reset();\n        serviceMetricsMap.clear();\n        priorityMetricsMap.clear();\n        errorCountMap.clear();\n        responseTimeDistribution.reset();\n        timeWindowStats.reset();\n        \n        logger.info(\"通知指标已重置\");\n    }\n    \n    /**\n     * 生成统计报告\n     * @return 格式化的统计报告\n     */\n    public String generateReport() {\n        StringBuilder report = new StringBuilder();\n        \n        report.append(\"\\n=== 通知服务监控报告 ===\").append(\"\\n\");\n        report.append(\"生成时间: \").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\"))).append(\"\\n\\n\");\n        \n        // 全局统计\n        GlobalStats globalStats = getGlobalStats();\n        report.append(\"全局统计:\\n\");\n        report.append(String.format(\"  总请求数: %d\\n\", globalStats.getTotalRequests()));\n        report.append(String.format(\"  成功请求: %d\\n\", globalStats.getSuccessfulRequests()));\n        report.append(String.format(\"  失败请求: %d\\n\", globalStats.getFailedRequests()));\n        report.append(String.format(\"  成功率: %.2f%%\\n\", globalStats.getSuccessRate()));\n        report.append(String.format(\"  平均响应时间: %.2fms\\n\\n\", globalStats.getAvgResponseTime()));\n        \n        // 服务统计\n        report.append(\"服务统计:\\n\");\n        serviceMetricsMap.forEach((serviceName, metrics) -> {\n            report.append(String.format(\"  %s: 成功率=%.2f%%, 平均响应=%.2fms, 总请求=%d\\n\", \n                serviceName, metrics.getSuccessRate(), metrics.getAvgResponseTime(), metrics.getTotalRequests()));\n        });\n        \n        // 优先级统计\n        if (!priorityMetricsMap.isEmpty()) {\n            report.append(\"\\n优先级统计:\\n\");\n            priorityMetricsMap.forEach((priority, metrics) -> {\n                report.append(String.format(\"  %s: 成功率=%.2f%%, 平均响应=%.2fms, 总请求=%d\\n\", \n                    priority, metrics.getSuccessRate(), metrics.getAvgResponseTime(), metrics.getTotalRequests()));\n            });\n        }\n        \n        // 错误统计（Top 5）\n        if (!errorCountMap.isEmpty()) {\n            report.append(\"\\n错误统计 (Top 5):\\n\");\n            errorCountMap.entrySet().stream()\n                    .sorted(Map.Entry.<String, AtomicLong>comparingByValue((a, b) -> Long.compare(b.get(), a.get())))\n                    .limit(5)\n                    .forEach(entry -> {\n                        report.append(String.format(\"  %s: %d次\\n\", entry.getKey(), entry.getValue().get()));\n                    });\n        }\n        \n        // 响应时间分布\n        report.append(\"\\n响应时间分布:\\n\");\n        ResponseTimeDistribution distribution = getResponseTimeDistribution();\n        report.append(String.format(\"  <100ms: %d次\\n\", distribution.getUnder100ms()));\n        report.append(String.format(\"  100-500ms: %d次\\n\", distribution.getBetween100And500ms()));\n        report.append(String.format(\"  500-1000ms: %d次\\n\", distribution.getBetween500And1000ms()));\n        report.append(String.format(\"  1000-5000ms: %d次\\n\", distribution.getBetween1000And5000ms()));\n        report.append(String.format(\"  >5000ms: %d次\\n\", distribution.getOver5000ms()));\n        \n        return report.toString();\n    }\n    \n    /**\n     * 启动定期日志记录\n     */\n    private void startPeriodicLogging() {\n        Timer timer = new Timer(\"notification-metrics-logger\", true);\n        timer.scheduleAtFixedRate(new TimerTask() {\n            @Override\n            public void run() {\n                if (totalRequests.sum() > 0) {\n                    logger.info(generateReport());\n                }\n            }\n        }, 300000, 300000); // 每5分钟记录一次\n    }\n    \n    /**\n     * 全局统计信息\n     */\n    public static class GlobalStats {\n        private final long totalRequests;\n        private final long successfulRequests;\n        private final long failedRequests;\n        private final double successRate;\n        private final double avgResponseTime;\n        \n        public GlobalStats(long totalRequests, long successfulRequests, long failedRequests, \n                         double successRate, double avgResponseTime) {\n            this.totalRequests = totalRequests;\n            this.successfulRequests = successfulRequests;\n            this.failedRequests = failedRequests;\n            this.successRate = successRate;\n            this.avgResponseTime = avgResponseTime;\n        }\n        \n        public long getTotalRequests() { return totalRequests; }\n        public long getSuccessfulRequests() { return successfulRequests; }\n        public long getFailedRequests() { return failedRequests; }\n        public double getSuccessRate() { return successRate; }\n        public double getAvgResponseTime() { return avgResponseTime; }\n    }\n    \n    /**\n     * 服务统计信息\n     */\n    public static class ServiceMetrics {\n        private final String serviceName;\n        private final LongAdder totalRequests = new LongAdder();\n        private final LongAdder successfulRequests = new LongAdder();\n        private final LongAdder totalResponseTime = new LongAdder();\n        \n        public ServiceMetrics(String serviceName) {\n            this.serviceName = serviceName;\n        }\n        \n        public void recordRequest(boolean success, long responseTimeMs) {\n            totalRequests.increment();\n            totalResponseTime.add(responseTimeMs);\n            if (success) {\n                successfulRequests.increment();\n            }\n        }\n        \n        public String getServiceName() { return serviceName; }\n        public long getTotalRequests() { return totalRequests.sum(); }\n        public long getSuccessfulRequests() { return successfulRequests.sum(); }\n        public long getFailedRequests() { return totalRequests.sum() - successfulRequests.sum(); }\n        \n        public double getSuccessRate() {\n            long total = totalRequests.sum();\n            return total > 0 ? (double) successfulRequests.sum() / total * 100 : 0.0;\n        }\n        \n        public double getAvgResponseTime() {\n            long total = totalRequests.sum();\n            return total > 0 ? (double) totalResponseTime.sum() / total : 0.0;\n        }\n    }\n    \n    /**\n     * 优先级统计信息\n     */\n    public static class PriorityMetrics extends ServiceMetrics {\n        public PriorityMetrics(String priority) {\n            super(priority);\n        }\n        \n        public String getPriority() {\n            return getServiceName();\n        }\n    }\n    \n    /**\n     * 响应时间分布统计\n     */\n    public static class ResponseTimeDistribution {\n        private final LongAdder under100ms = new LongAdder();\n        private final LongAdder between100And500ms = new LongAdder();\n        private final LongAdder between500And1000ms = new LongAdder();\n        private final LongAdder between1000And5000ms = new LongAdder();\n        private final LongAdder over5000ms = new LongAdder();\n        \n        public void record(long responseTimeMs) {\n            if (responseTimeMs < 100) {\n                under100ms.increment();\n            } else if (responseTimeMs < 500) {\n                between100And500ms.increment();\n            } else if (responseTimeMs < 1000) {\n                between500And1000ms.increment();\n            } else if (responseTimeMs < 5000) {\n                between1000And5000ms.increment();\n            } else {\n                over5000ms.increment();\n            }\n        }\n        \n        public void reset() {\n            under100ms.reset();\n            between100And500ms.reset();\n            between500And1000ms.reset();\n            between1000And5000ms.reset();\n            over5000ms.reset();\n        }\n        \n        public long getUnder100ms() { return under100ms.sum(); }\n        public long getBetween100And500ms() { return between100And500ms.sum(); }\n        public long getBetween500And1000ms() { return between500And1000ms.sum(); }\n        public long getBetween1000And5000ms() { return between1000And5000ms.sum(); }\n        public long getOver5000ms() { return over5000ms.sum(); }\n    }\n    \n    /**\n     * 时间窗口统计（滑动窗口）\n     */\n    public static class TimeWindowStats {\n        private final Queue<TimePoint> timePoints = new LinkedList<>();\n        private final long windowSizeMs = 3600000; // 1小时\n        \n        private static class TimePoint {\n            final long timestamp;\n            final boolean success;\n            final long responseTime;\n            \n            TimePoint(long timestamp, boolean success, long responseTime) {\n                this.timestamp = timestamp;\n                this.success = success;\n                this.responseTime = responseTime;\n            }\n        }\n        \n        public synchronized void record(boolean success, long responseTime) {\n            long now = System.currentTimeMillis();\n            timePoints.offer(new TimePoint(now, success, responseTime));\n            \n            // 移除过期数据\n            while (!timePoints.isEmpty() && timePoints.peek().timestamp < now - windowSizeMs) {\n                timePoints.poll();\n            }\n        }\n        \n        public synchronized void reset() {\n            timePoints.clear();\n        }\n        \n        public synchronized long getRequestCount() {\n            return timePoints.size();\n        }\n        \n        public synchronized double getSuccessRate() {\n            if (timePoints.isEmpty()) {\n                return 0.0;\n            }\n            long successCount = timePoints.stream()\n                    .mapToLong(tp -> tp.success ? 1 : 0)\n                    .sum();\n            return (double) successCount / timePoints.size() * 100;\n        }\n        \n        public synchronized double getAvgResponseTime() {\n            if (timePoints.isEmpty()) {\n                return 0.0;\n            }\n            return timePoints.stream()\n                    .mapToLong(tp -> tp.responseTime)\n                    .average()\n                    .orElse(0.0);\n        }\n    }\n}"