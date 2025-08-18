package com.mysql.monitor;

import java.util.concurrent.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 复制延迟告警管理器
 * 管理告警规则和通知
 */
public class ReplicationAlertManager {
    private static final Logger logger = Logger.getLogger(ReplicationAlertManager.class.getName());
    
    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, AlertHistory> alertHistory = new ConcurrentHashMap<>();
    private boolean isRunning = false;
    private ScheduledFuture<?> checkTask;

    /**
     * 添加告警规则
     */
    public void addRule(String name, AlertRule rule) {
        rules.put(name, rule);
        logger.info("Added alert rule: " + name);
    }

    /**
     * 移除告警规则
     */
    public void removeRule(String name) {
        rules.remove(name);
        alertHistory.remove(name);
        logger.info("Removed alert rule: " + name);
    }

    /**
     * 启动告警管理
     */
    public void start() {
        if (!isRunning) {
            isRunning = true;
            checkTask = scheduler.scheduleAtFixedRate(
                this::checkAllRules,
                0,
                10,
                TimeUnit.SECONDS
            );
            logger.info("Started replication alert manager");
        }
    }

    /**
     * 停止告警管理
     */
    public void stop() {
        if (isRunning) {
            isRunning = false;
            if (checkTask != null) {
                checkTask.cancel(false);
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Stopped replication alert manager");
        }
    }

    /**
     * 检查所有告警规则
     */
    private void checkAllRules() {
        for (Map.Entry<String, AlertRule> entry : rules.entrySet()) {
            try {
                checkRule(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error checking rule: " + entry.getKey(), e);
            }
        }
    }

    /**
     * 检查单个告警规则
     */
    private void checkRule(String ruleName, AlertRule rule) {
        try {
            long delay = rule.getMonitor().getReplicationDelay();
            AlertHistory history = alertHistory.computeIfAbsent(ruleName, k -> new AlertHistory());
            
            boolean shouldAlert = false;
            
            switch (rule.getType()) {
                case DELAY_THRESHOLD:
                    shouldAlert = delay > rule.getThreshold();
                    break;
                case DELAY_INCREASE:
                    shouldAlert = history.getLastDelay() != -1 && 
                                 (delay - history.getLastDelay()) > rule.getThreshold();
                    break;
                case REPLICATION_STOPPED:
                    try {
                        MySQLReplicationMonitor.ReplicationStatus status = 
                            rule.getMonitor().getDetailedStatus();
                        shouldAlert = !status.isSlaveIORunning() || !status.isSlaveSQLRunning();
                    } catch (Exception e) {
                        shouldAlert = true;
                    }
                    break;
            }
            
            if (shouldAlert) {
                if (!history.isAlerting() || 
                    (System.currentTimeMillis() - history.getLastAlertTime()) > rule.getCooldownMillis()) {
                    
                    AlertEvent event = new AlertEvent();
                    event.setRuleName(ruleName);
                    event.setRule(rule);
                    event.setCurrentDelay(delay);
                    event.setPreviousDelay(history.getLastDelay());
                    event.setTimestamp(System.currentTimeMillis());
                    
                    notifyAlert(event);
                    history.setAlerting(true);
                    history.setLastAlertTime(System.currentTimeMillis());
                }
            } else {
                if (history.isAlerting()) {
                    // 恢复通知
                    RecoveryEvent recovery = new RecoveryEvent();
                    recovery.setRuleName(ruleName);
                    recovery.setCurrentDelay(delay);
                    recovery.setTimestamp(System.currentTimeMillis());
                    
                    notifyRecovery(recovery);
                    history.setAlerting(false);
                }
            }
            
            history.setLastDelay(delay);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking rule: " + ruleName, e);
        }
    }

    /**
     * 通知告警事件
     */
    private void notifyAlert(AlertEvent event) {
        for (AlertListener listener : listeners) {
            try {
                listener.onAlert(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error notifying alert listener", e);
            }
        }
    }

    /**
     * 通知恢复事件
     */
    private void notifyRecovery(RecoveryEvent event) {
        for (AlertListener listener : listeners) {
            try {
                listener.onRecovery(event);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error notifying recovery listener", e);
            }
        }
    }

    /**
     * 添加告警监听器
     */
    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除告警监听器
     */
    public void removeListener(AlertListener listener) {
        listeners.remove(listener);
    }

    /**
     * 获取告警历史
     */
    public Map<String, AlertHistory> getAlertHistory() {
        return new HashMap<>(alertHistory);
    }

    /**
     * 获取所有规则
     */
    public Map<String, AlertRule> getRules() {
        return new HashMap<>(rules);
    }

    /**
     * 告警规则
     */
    public static class AlertRule {
        private final MySQLReplicationMonitor monitor;
        private final AlertType type;
        private final long threshold;
        private final long cooldownMillis;
        private final String description;

        public AlertRule(MySQLReplicationMonitor monitor, AlertType type, long threshold, long cooldownSeconds, String description) {
            this.monitor = monitor;
            this.type = type;
            this.threshold = threshold;
            this.cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);
            this.description = description;
        }

        public MySQLReplicationMonitor getMonitor() { return monitor; }
        public AlertType getType() { return type; }
        public long getThreshold() { return threshold; }
        public long getCooldownMillis() { return cooldownMillis; }
        public String getDescription() { return description; }
    }

    /**
     * 告警类型
     */
    public enum AlertType {
        DELAY_THRESHOLD,      // 延迟阈值
        DELAY_INCREASE,        // 延迟增加
        REPLICATION_STOPPED    // 复制停止
    }

    /**
     * 告警事件
     */
    public static class AlertEvent {
        private String ruleName;
        private AlertRule rule;
        private long currentDelay;
        private long previousDelay;
        private long timestamp;

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        
        public AlertRule getRule() { return rule; }
        public void setRule(AlertRule rule) { this.rule = rule; }
        
        public long getCurrentDelay() { return currentDelay; }
        public void setCurrentDelay(long currentDelay) { this.currentDelay = currentDelay; }
        
        public long getPreviousDelay() { return previousDelay; }
        public void setPreviousDelay(long previousDelay) { this.previousDelay = previousDelay; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "AlertEvent{" +
                    "ruleName='" + ruleName + '\'' +
                    ", currentDelay=" + currentDelay +
                    ", previousDelay=" + previousDelay +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 恢复事件
     */
    public static class RecoveryEvent {
        private String ruleName;
        private long currentDelay;
        private long timestamp;

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        
        public long getCurrentDelay() { return currentDelay; }
        public void setCurrentDelay(long currentDelay) { this.currentDelay = currentDelay; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "RecoveryEvent{" +
                    "ruleName='" + ruleName + '\'' +
                    ", currentDelay=" + currentDelay +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 告警历史
     */
    public static class AlertHistory {
        private long lastDelay = -1;
        private boolean isAlerting = false;
        private long lastAlertTime = 0;

        public long getLastDelay() { return lastDelay; }
        public void setLastDelay(long lastDelay) { this.lastDelay = lastDelay; }
        
        public boolean isAlerting() { return isAlerting; }
        public void setAlerting(boolean alerting) { isAlerting = alerting; }
        
        public long getLastAlertTime() { return lastAlertTime; }
        public void setLastAlertTime(long lastAlertTime) { this.lastAlertTime = lastAlertTime; }
    }

    /**
     * 告警监听器接口
     */
    public interface AlertListener {
        void onAlert(AlertEvent event);
        void onRecovery(RecoveryEvent event);
    }
}