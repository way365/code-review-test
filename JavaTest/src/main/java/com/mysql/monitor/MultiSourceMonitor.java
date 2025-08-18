package com.mysql.monitor;

import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 多源MySQL监控器
 * 支持监控多个主从复制对
 */
public class MultiSourceMonitor {
    private static final Logger logger = Logger.getLogger(MultiSourceMonitor.class.getName());
    
    private final Map<String, MySQLReplicationMonitor> monitors = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final List<MultiSourceListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> summaryTask;

    /**
     * 添加监控源
     */
    public void addMonitor(String name, MySQLReplicationMonitor monitor) {
        monitors.put(name, monitor);
        logger.info("Added monitor: " + name);
    }

    /**
     * 移除监控源
     */
    public void removeMonitor(String name) {
        MySQLReplicationMonitor monitor = monitors.remove(name);
        if (monitor != null) {
            monitor.stop();
            logger.info("Removed monitor: " + name);
        }
    }

    /**
     * 启动所有监控
     */
    public void startAll() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting all monitors...");
            
            for (Map.Entry<String, MySQLReplicationMonitor> entry : monitors.entrySet()) {
                entry.getValue().start();
                
                // 为每个监控器添加监听器
                entry.getValue().addListener(new MySQLReplicationMonitor.ReplicationDelayListener() {
                    @Override
                    public void onDelayChange(long delaySeconds) {
                        notifyDelayChange(entry.getKey(), delaySeconds);
                    }

                    @Override
                    public void onError(Exception e) {
                        notifyError(entry.getKey(), e);
                    }
                });
            }
            
            // 启动定时汇总任务
            summaryTask = scheduler.scheduleAtFixedRate(
                this::generateSummary,
                30,
                30,
                TimeUnit.SECONDS
            );
        }
    }

    /**
     * 停止所有监控
     */
    public void stopAll() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping all monitors...");
            
            for (MySQLReplicationMonitor monitor : monitors.values()) {
                monitor.stop();
            }
            
            if (summaryTask != null) {
                summaryTask.cancel(false);
            }
            
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取所有监控状态
     */
    public Map<String, MonitorStatus> getAllStatuses() {
        Map<String, MonitorStatus> statuses = new HashMap<>();
        
        for (Map.Entry<String, MySQLReplicationMonitor> entry : monitors.entrySet()) {
            MonitorStatus status = new MonitorStatus();
            status.setName(entry.getKey());
            status.setRunning(entry.getValue().isRunning());
            status.setLastDelaySeconds(entry.getValue().getLastDelaySeconds());
            
            try {
                MySQLReplicationMonitor.ReplicationStatus detail = entry.getValue().getDetailedStatus();
                status.setSlaveIORunning(detail.isSlaveIORunning());
                status.setSlaveSQLRunning(detail.isSlaveSQLRunning());
                status.setSecondsBehindMaster(detail.getSecondsBehindMaster());
                status.setLastError(detail.getLastError());
            } catch (Exception e) {
                status.setError(e.getMessage());
                logger.log(Level.WARNING, "Error getting status for " + entry.getKey(), e);
            }
            
            statuses.put(entry.getKey(), status);
        }
        
        return statuses;
    }

    /**
     * 生成汇总报告
     */
    private void generateSummary() {
        Map<String, MonitorStatus> statuses = getAllStatuses();
        
        int totalMonitors = statuses.size();
        int healthyMonitors = 0;
        int delayedMonitors = 0;
        int failedMonitors = 0;
        long maxDelay = 0;
        
        for (MonitorStatus status : statuses.values()) {
            if (status.getError() != null) {
                failedMonitors++;
            } else if (status.getSecondsBehindMaster() > 60) {
                delayedMonitors++;
                maxDelay = Math.max(maxDelay, status.getSecondsBehindMaster());
            } else {
                healthyMonitors++;
            }
        }
        
        SummaryReport report = new SummaryReport();
        report.setTotalMonitors(totalMonitors);
        report.setHealthyMonitors(healthyMonitors);
        report.setDelayedMonitors(delayedMonitors);
        report.setFailedMonitors(failedMonitors);
        report.setMaxDelaySeconds(maxDelay);
        report.setTimestamp(new Date());
        
        notifySummary(report);
    }

    /**
     * 通知延迟变化
     */
    private void notifyDelayChange(String sourceName, long delaySeconds) {
        for (MultiSourceListener listener : listeners) {
            listener.onDelayChange(sourceName, delaySeconds);
        }
    }

    /**
     * 通知错误
     */
    private void notifyError(String sourceName, Exception error) {
        for (MultiSourceListener listener : listeners) {
            listener.onError(sourceName, error);
        }
    }

    /**
     * 通知汇总报告
     */
    private void notifySummary(SummaryReport report) {
        for (MultiSourceListener listener : listeners) {
            listener.onSummary(report);
        }
    }

    /**
     * 添加监听器
     */
    public void addListener(MultiSourceListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(MultiSourceListener listener) {
        listeners.remove(listener);
    }

    /**
     * 获取监控器数量
     */
    public int getMonitorCount() {
        return monitors.size();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 多源监听器接口
     */
    public interface MultiSourceListener {
        void onDelayChange(String sourceName, long delaySeconds);
        void onError(String sourceName, Exception error);
        void onSummary(SummaryReport report);
    }

    /**
     * 监控状态
     */
    public static class MonitorStatus {
        private String name;
        private boolean running;
        private long lastDelaySeconds;
        private boolean slaveIORunning;
        private boolean slaveSQLRunning;
        private long secondsBehindMaster;
        private String lastError;
        private String error;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        
        public long getLastDelaySeconds() { return lastDelaySeconds; }
        public void setLastDelaySeconds(long lastDelaySeconds) { this.lastDelaySeconds = lastDelaySeconds; }
        
        public boolean isSlaveIORunning() { return slaveIORunning; }
        public void setSlaveIORunning(boolean slaveIORunning) { this.slaveIORunning = slaveIORunning; }
        
        public boolean isSlaveSQLRunning() { return slaveSQLRunning; }
        public void setSlaveSQLRunning(boolean slaveSQLRunning) { this.slaveSQLRunning = slaveSQLRunning; }
        
        public long getSecondsBehindMaster() { return secondsBehindMaster; }
        public void setSecondsBehindMaster(long secondsBehindMaster) { this.secondsBehindMaster = secondsBehindMaster; }
        
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        @Override
        public String toString() {
            return "MonitorStatus{" +
                    "name='" + name + '\'' +
                    ", running=" + running +
                    ", lastDelaySeconds=" + lastDelaySeconds +
                    ", slaveIORunning=" + slaveIORunning +
                    ", slaveSQLRunning=" + slaveSQLRunning +
                    ", secondsBehindMaster=" + secondsBehindMaster +
                    ", lastError='" + lastError + '\'' +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    /**
     * 汇总报告
     */
    public static class SummaryReport {
        private int totalMonitors;
        private int healthyMonitors;
        private int delayedMonitors;
        private int failedMonitors;
        private long maxDelaySeconds;
        private Date timestamp;

        // Getters and setters
        public int getTotalMonitors() { return totalMonitors; }
        public void setTotalMonitors(int totalMonitors) { this.totalMonitors = totalMonitors; }
        
        public int getHealthyMonitors() { return healthyMonitors; }
        public void setHealthyMonitors(int healthyMonitors) { this.healthyMonitors = healthyMonitors; }
        
        public int getDelayedMonitors() { return delayedMonitors; }
        public void setDelayedMonitors(int delayedMonitors) { this.delayedMonitors = delayedMonitors; }
        
        public int getFailedMonitors() { return failedMonitors; }
        public void setFailedMonitors(int failedMonitors) { this.failedMonitors = failedMonitors; }
        
        public long getMaxDelaySeconds() { return maxDelaySeconds; }
        public void setMaxDelaySeconds(long maxDelaySeconds) { this.maxDelaySeconds = maxDelaySeconds; }
        
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "SummaryReport{" +
                    "totalMonitors=" + totalMonitors +
                    ", healthyMonitors=" + healthyMonitors +
                    ", delayedMonitors=" + delayedMonitors +
                    ", failedMonitors=" + failedMonitors +
                    ", maxDelaySeconds=" + maxDelaySeconds +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}