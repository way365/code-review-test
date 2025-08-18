package com.mysql.monitor;

import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * MySQL主从延迟监控器
 * 实时监控MySQL主从复制延迟，支持多从库监控
 */
public class MySQLReplicationMonitor {
    private static final Logger logger = Logger.getLogger(MySQLReplicationMonitor.class.getName());
    
    private final String masterUrl;
    private final String slaveUrl;
    private final String username;
    private final String password;
    private final long monitorInterval;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong lastDelaySeconds = new AtomicLong(0);
    private final CopyOnWriteArrayList<ReplicationDelayListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> monitorTask;

    public MySQLReplicationMonitor(String masterUrl, String slaveUrl, String username, String password) {
        this(masterUrl, slaveUrl, username, password, 5000); // 默认5秒监控间隔
    }

    public MySQLReplicationMonitor(String masterUrl, String slaveUrl, String username, String password, long monitorInterval) {
        this.masterUrl = masterUrl;
        this.slaveUrl = slaveUrl;
        this.username = username;
        this.password = password;
        this.monitorInterval = monitorInterval;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * 启动监控
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting MySQL replication monitor...");
            monitorTask = scheduler.scheduleAtFixedRate(
                this::checkReplicationDelay,
                0,
                monitorInterval,
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 停止监控
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping MySQL replication monitor...");
            if (monitorTask != null) {
                monitorTask.cancel(false);
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
        }
    }

    /**
     * 检查复制延迟
     */
    private void checkReplicationDelay() {
        try {
            long delay = getReplicationDelay();
            lastDelaySeconds.set(delay);
            
            // 通知监听器
            for (ReplicationDelayListener listener : listeners) {
                listener.onDelayChange(delay);
            }
            
            if (delay > 60) { // 延迟超过60秒告警
                logger.warning("High replication delay detected: " + delay + " seconds");
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking replication delay", e);
            // 通知错误监听器
            for (ReplicationDelayListener listener : listeners) {
                listener.onError(e);
            }
        }
    }

    /**
     * 获取当前复制延迟（秒）
     */
    public long getReplicationDelay() throws SQLException {
        try (Connection masterConn = DriverManager.getConnection(masterUrl, username, password);
             Connection slaveConn = DriverManager.getConnection(slaveUrl, username, password)) {
            
            // 获取主库当前位置
            long masterPosition = getMasterPosition(masterConn);
            
            // 获取从库当前位置
            long slavePosition = getSlavePosition(slaveConn);
            
            // 计算延迟
            return masterPosition - slavePosition;
        }
    }

    /**
     * 获取主库当前binlog位置
     */
    private long getMasterPosition(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW MASTER STATUS")) {
            
            if (rs.next()) {
                return rs.getLong("Position");
            }
            throw new SQLException("Cannot get master status");
        }
    }

    /**
     * 获取从库当前读取位置
     */
    private long getSlavePosition(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS")) {
            
            if (rs.next()) {
                // 获取从库正在读取的主库binlog位置
                String ioRunning = rs.getString("Slave_IO_Running");
                String sqlRunning = rs.getString("Slave_SQL_Running");
                
                if (!"Yes".equalsIgnoreCase(ioRunning) || !"Yes".equalsIgnoreCase(sqlRunning)) {
                    logger.warning("Replication is not running properly");
                    return 0;
                }
                
                return rs.getLong("Read_Master_Log_Pos");
            }
            throw new SQLException("Cannot get slave status");
        }
    }

    /**
     * 获取详细的复制状态
     */
    public ReplicationStatus getDetailedStatus() throws SQLException {
        try (Connection slaveConn = DriverManager.getConnection(slaveUrl, username, password)) {
            return getSlaveStatus(slaveConn);
        }
    }

    private ReplicationStatus getSlaveStatus(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS")) {
            
            if (rs.next()) {
                ReplicationStatus status = new ReplicationStatus();
                status.setSlaveIORunning("Yes".equalsIgnoreCase(rs.getString("Slave_IO_Running")));
                status.setSlaveSQLRunning("Yes".equalsIgnoreCase(rs.getString("Slave_SQL_Running")));
                status.setLastError(rs.getString("Last_Error"));
                status.setSecondsBehindMaster(rs.getLong("Seconds_Behind_Master"));
                status.setMasterLogFile(rs.getString("Master_Log_File"));
                status.setReadMasterLogPos(rs.getLong("Read_Master_Log_Pos"));
                status.setRelayLogFile(rs.getString("Relay_Log_File"));
                status.setRelayLogPos(rs.getLong("Relay_Log_Pos"));
                status.setLastIoError(rs.getString("Last_IO_Error"));
                status.setLastSqlError(rs.getString("Last_SQL_Error"));
                
                return status;
            }
            throw new SQLException("Cannot get slave status");
        }
    }

    /**
     * 添加延迟监听器
     */
    public void addListener(ReplicationDelayListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除延迟监听器
     */
    public void removeListener(ReplicationDelayListener listener) {
        listeners.remove(listener);
    }

    /**
     * 获取最后检测到的延迟
     */
    public long getLastDelaySeconds() {
        return lastDelaySeconds.get();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 复制延迟监听器接口
     */
    public interface ReplicationDelayListener {
        void onDelayChange(long delaySeconds);
        void onError(Exception e);
    }

    /**
     * 复制状态信息
     */
    public static class ReplicationStatus {
        private boolean slaveIORunning;
        private boolean slaveSQLRunning;
        private String lastError;
        private long secondsBehindMaster;
        private String masterLogFile;
        private long readMasterLogPos;
        private String relayLogFile;
        private long relayLogPos;
        private String lastIoError;
        private String lastSqlError;

        // Getters and setters
        public boolean isSlaveIORunning() { return slaveIORunning; }
        public void setSlaveIORunning(boolean slaveIORunning) { this.slaveIORunning = slaveIORunning; }
        
        public boolean isSlaveSQLRunning() { return slaveSQLRunning; }
        public void setSlaveSQLRunning(boolean slaveSQLRunning) { this.slaveSQLRunning = slaveSQLRunning; }
        
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        
        public long getSecondsBehindMaster() { return secondsBehindMaster; }
        public void setSecondsBehindMaster(long secondsBehindMaster) { this.secondsBehindMaster = secondsBehindMaster; }
        
        public String getMasterLogFile() { return masterLogFile; }
        public void setMasterLogFile(String masterLogFile) { this.masterLogFile = masterLogFile; }
        
        public long getReadMasterLogPos() { return readMasterLogPos; }
        public void setReadMasterLogPos(long readMasterLogPos) { this.readMasterLogPos = readMasterLogPos; }
        
        public String getRelayLogFile() { return relayLogFile; }
        public void setRelayLogFile(String relayLogFile) { this.relayLogFile = relayLogFile; }
        
        public long getRelayLogPos() { return relayLogPos; }
        public void setRelayLogPos(long relayLogPos) { this.relayLogPos = relayLogPos; }
        
        public String getLastIoError() { return lastIoError; }
        public void setLastIoError(String lastIoError) { this.lastIoError = lastIoError; }
        
        public String getLastSqlError() { return lastSqlError; }
        public void setLastSqlError(String lastSqlError) { this.lastSqlError = lastSqlError; }

        @Override
        public String toString() {
            return "ReplicationStatus{" +
                    "slaveIORunning=" + slaveIORunning +
                    ", slaveSQLRunning=" + slaveSQLRunning +
                    ", lastError='" + lastError + '\'' +
                    ", secondsBehindMaster=" + secondsBehindMaster +
                    ", masterLogFile='" + masterLogFile + '\'' +
                    ", readMasterLogPos=" + readMasterLogPos +
                    ", relayLogFile='" + relayLogFile + '\'' +
                    ", relayLogPos=" + relayLogPos +
                    ", lastIoError='" + lastIoError + '\'' +
                    ", lastSqlError='" + lastSqlError + '\'' +
                    '}';
        }
    }
}