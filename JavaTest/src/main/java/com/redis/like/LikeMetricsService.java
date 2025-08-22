package com.redis.like;

import redis.clients.jedis.JedisPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 点赞性能监控和统计服务
 * 提供详细的性能指标和健康检查
 */
public class LikeMetricsService {
    
    private final JedisPool jedisPool;
    private final ScheduledExecutorService scheduler;
    private final Map<String, MetricData> metrics;
    private final LongAdder totalRequests;
    private final LongAdder totalErrors;
    private final AtomicLong lastResetTime;
    
    // 性能阈值配置
    private final long slowQueryThreshold = 100; // ms
    private final double errorRateThreshold = 0.05; // 5%
    private final long memoryThreshold = 100 * 1024 * 1024; // 100MB
    
    public LikeMetricsService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.metrics = new ConcurrentHashMap<>();
        this.totalRequests = new LongAdder();
        this.totalErrors = new LongAdder();
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());
        
        startMetricsCollection();
    }
    
    /**
     * 指标数据
     */
    public static class MetricData {
        private final LongAdder count;
        private final LongAdder errorCount;
        private final AtomicLong totalTime;
        private final AtomicLong maxTime;
        private final AtomicLong minTime;
        private final Queue<Long> recentTimes;
        
        public MetricData() {
            this.count = new LongAdder();
            this.errorCount = new LongAdder();
            this.totalTime = new AtomicLong(0);
            this.maxTime = new AtomicLong(0);
            this.minTime = new AtomicLong(Long.MAX_VALUE);
            this.recentTimes = new ConcurrentLinkedQueue<>();
        }
        
        public void recordRequest(long duration, boolean success) {
            count.increment();
            if (!success) {
                errorCount.increment();
            }
            
            totalTime.addAndGet(duration);
            maxTime.updateAndGet(current -> Math.max(current, duration));
            minTime.updateAndGet(current -> Math.min(current, duration));
            
            // 保留最近100次的响应时间
            recentTimes.offer(duration);
            if (recentTimes.size() > 100) {
                recentTimes.poll();
            }
        }
        
        public long getCount() { return count.sum(); }
        public long getErrorCount() { return errorCount.sum(); }
        public double getErrorRate() { 
            long total = getCount();
            return total > 0 ? (double) getErrorCount() / total : 0.0;
        }
        public double getAverageTime() {
            long total = getCount();
            return total > 0 ? (double) totalTime.get() / total : 0.0;
        }
        public long getMaxTime() { return maxTime.get(); }
        public long getMinTime() { 
            long min = minTime.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        
        public double getP95Time() {
            List<Long> times = new ArrayList<>(recentTimes);
            if (times.isEmpty()) return 0.0;
            
            times.sort(Long::compareTo);
            int index = (int) Math.ceil(times.size() * 0.95) - 1;
            return index >= 0 ? times.get(index) : 0.0;
        }
        
        public void reset() {
            count.reset();
            errorCount.reset();
            totalTime.set(0);
            maxTime.set(0);
            minTime.set(Long.MAX_VALUE);
            recentTimes.clear();
        }
    }
    
    /**
     * 健康状态
     */
    public enum HealthStatus {
        HEALTHY, WARNING, CRITICAL
    }
    
    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private final HealthStatus status;
        private final String message;
        private final Map<String, Object> details;
        
        public HealthCheckResult(HealthStatus status, String message, Map<String, Object> details) {
            this.status = status;
            this.message = message;
            this.details = details;
        }
        
        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    /**
     * 记录请求指标
     */
    public void recordRequest(String operation, long duration, boolean success) {
        totalRequests.increment();
        if (!success) {
            totalErrors.increment();
        }
        
        MetricData metric = metrics.computeIfAbsent(operation, k -> new MetricData());
        metric.recordRequest(duration, success);
    }
    
    /**
     * 获取操作指标
     */
    public Map<String, Object> getOperationMetrics(String operation) {
        MetricData metric = metrics.get(operation);
        if (metric == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", metric.getCount());
        result.put("errorCount", metric.getErrorCount());
        result.put("errorRate", metric.getErrorRate());
        result.put("averageTime", metric.getAverageTime());
        result.put("maxTime", metric.getMaxTime());
        result.put("minTime", metric.getMinTime());
        result.put("p95Time", metric.getP95Time());
        
        return result;
    }
    
    /**
     * 获取所有指标
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new HashMap<>();
        
        // 全局统计
        Map<String, Object> global = new HashMap<>();
        global.put("totalRequests", totalRequests.sum());
        global.put("totalErrors", totalErrors.sum());
        global.put("globalErrorRate", totalRequests.sum() > 0 ? 
            (double) totalErrors.sum() / totalRequests.sum() : 0.0);
        global.put("uptime", System.currentTimeMillis() - lastResetTime.get());
        allMetrics.put("global", global);
        
        // 各操作统计
        Map<String, Object> operations = new HashMap<>();
        for (Map.Entry<String, MetricData> entry : metrics.entrySet()) {
            operations.put(entry.getKey(), getOperationMetrics(entry.getKey()));
        }
        allMetrics.put("operations", operations);
        
        return allMetrics;
    }
    
    /**
     * 执行健康检查
     */
    public HealthCheckResult healthCheck() {
        Map<String, Object> details = new HashMap<>();
        List<String> issues = new ArrayList<>();
        
        // 检查错误率
        double globalErrorRate = totalRequests.sum() > 0 ? 
            (double) totalErrors.sum() / totalRequests.sum() : 0.0;
        details.put("globalErrorRate", globalErrorRate);
        
        if (globalErrorRate > errorRateThreshold) {
            issues.add("全局错误率过高: " + String.format("%.2f%%", globalErrorRate * 100));
        }
        
        // 检查慢查询
        List<String> slowOperations = new ArrayList<>();
        for (Map.Entry<String, MetricData> entry : metrics.entrySet()) {
            MetricData metric = entry.getValue();
            if (metric.getAverageTime() > slowQueryThreshold) {
                slowOperations.add(entry.getKey() + ":" + String.format("%.1fms", metric.getAverageTime()));
            }
        }
        details.put("slowOperations", slowOperations);
        
        if (!slowOperations.isEmpty()) {
            issues.add("存在慢查询操作: " + String.join(", ", slowOperations));
        }
        
        // 检查Redis连接
        try (var jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            details.put("redisConnection", "OK");
            
            // 检查Redis内存使用
            String memoryInfo = jedis.info("memory");
            if (memoryInfo.contains("used_memory:")) {
                String[] lines = memoryInfo.split("\r\n");
                for (String line : lines) {
                    if (line.startsWith("used_memory:")) {
                        long usedMemory = Long.parseLong(line.split(":")[1]);
                        details.put("redisMemoryUsed", usedMemory);
                        
                        if (usedMemory > memoryThreshold) {
                            issues.add("Redis内存使用过高: " + (usedMemory / 1024 / 1024) + "MB");
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            issues.add("Redis连接异常: " + e.getMessage());
            details.put("redisConnection", "ERROR: " + e.getMessage());
        }
        
        details.put("issues", issues);
        
        // 确定健康状态
        HealthStatus status;
        String message;
        
        if (issues.isEmpty()) {
            status = HealthStatus.HEALTHY;
            message = "系统运行正常";
        } else if (issues.size() <= 2) {
            status = HealthStatus.WARNING;
            message = "发现 " + issues.size() + " 个警告";
        } else {
            status = HealthStatus.CRITICAL;
            message = "发现 " + issues.size() + " 个严重问题";
        }
        
        return new HealthCheckResult(status, message, details);
    }
    
    /**
     * 获取性能报告
     */
    public Map<String, Object> getPerformanceReport() {
        Map<String, Object> report = new HashMap<>();
        
        // 基本统计
        report.put("metrics", getAllMetrics());
        
        // 健康检查
        HealthCheckResult health = healthCheck();
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("status", health.getStatus().name());
        healthMap.put("message", health.getMessage());
        healthMap.put("details", health.getDetails());
        report.put("health", healthMap);
        
        // 性能建议
        List<String> recommendations = generateRecommendations();
        report.put("recommendations", recommendations);
        
        report.put("timestamp", System.currentTimeMillis());
        report.put("uptime", System.currentTimeMillis() - lastResetTime.get());
        
        return report;
    }
    
    /**
     * 生成性能建议
     */
    private List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        // 基于指标生成建议
        double globalErrorRate = totalRequests.sum() > 0 ? 
            (double) totalErrors.sum() / totalRequests.sum() : 0.0;
        
        if (globalErrorRate > 0.01) {
            recommendations.add("错误率较高，建议检查错误日志并优化错误处理");
        }
        
        for (Map.Entry<String, MetricData> entry : metrics.entrySet()) {
            MetricData metric = entry.getValue();
            String operation = entry.getKey();
            
            if (metric.getAverageTime() > slowQueryThreshold) {
                recommendations.add("操作 '" + operation + "' 响应时间较慢，建议优化");
            }
            
            if (metric.getErrorRate() > 0.05) {
                recommendations.add("操作 '" + operation + "' 错误率过高，建议检查");
            }
            
            if (metric.getP95Time() > slowQueryThreshold * 2) {
                recommendations.add("操作 '" + operation + "' P95响应时间过长，可能存在性能瓶颈");
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("系统性能良好，无需特别优化");
        }
        
        return recommendations;
    }
    
    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        totalRequests.reset();
        totalErrors.reset();
        metrics.values().forEach(MetricData::reset);
        lastResetTime.set(System.currentTimeMillis());
    }
    
    /**
     * 启动指标收集
     */
    private void startMetricsCollection() {
        // 定期清理过期指标
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupOldMetrics();
            } catch (Exception e) {
                System.err.println("指标清理任务执行失败: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.HOURS);
        
        // 定期输出性能报告
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> report = getPerformanceReport();
                System.out.println("=== 点赞系统性能报告 ===");
                System.out.println("健康状态: " + ((Map<?, ?>) report.get("health")).get("status"));
                System.out.println("总请求数: " + ((Map<?, ?>) ((Map<?, ?>) report.get("metrics")).get("global")).get("totalRequests"));
                System.out.println("全局错误率: " + String.format("%.2f%%", 
                    (Double) ((Map<?, ?>) ((Map<?, ?>) report.get("metrics")).get("global")).get("globalErrorRate") * 100));
            } catch (Exception e) {
                System.err.println("性能报告生成失败: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * 清理过期指标
     */
    private void cleanupOldMetrics() {
        // 这里可以实现更复杂的清理逻辑
        // 例如移除长时间未使用的操作指标
        long currentTime = System.currentTimeMillis();
        long oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1);
        
        // 简单实现：如果指标长时间没有更新，就清理
        // 这里可以根据具体需求调整清理策略
    }
    
    /**
     * 关闭服务
     */
    public void close() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("关闭指标服务被中断: " + e.getMessage());
        }
    }
}