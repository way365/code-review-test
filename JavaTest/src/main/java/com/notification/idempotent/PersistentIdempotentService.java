package com.notification.idempotent;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化消息幂等服务
 * 基于SQLite实现消息幂等性，支持进程重启后的幂等性保持
 */
public class PersistentIdempotentService {
    private final Connection connection;
    private final ConcurrentHashMap<String, Boolean> localCache;

    public PersistentIdempotentService(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(url);
        this.localCache = new ConcurrentHashMap<>();
        initializeTable();
    }

    private void initializeTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS message_idempotent (" +
                "message_key VARCHAR(255) PRIMARY KEY, " +
                "processed BOOLEAN DEFAULT FALSE, " +
                "result TEXT, " +
                "created_time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "updated_time DATETIME DEFAULT CURRENT_TIMESTAMP)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize idempotent table", e);
        }
    }

    /**
     * 检查消息是否已处理
     * @param messageKey 消息唯一键
     * @return true 如果消息已处理
     */
    public boolean isProcessed(String messageKey) {
        // 先检查本地缓存
        if (localCache.containsKey(messageKey)) {
            return localCache.get(messageKey);
        }

        // 再检查数据库
        String sql = "SELECT processed FROM message_idempotent WHERE message_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageKey);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                boolean processed = rs.getBoolean("processed");
                localCache.put(messageKey, processed);
                return processed;
            }
            
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check message status", e);
        }
    }

    /**
     * 标记消息为处理中
     * @param messageKey 消息唯一键
     * @return true 如果成功标记，false 如果消息已存在
     */
    public boolean markProcessing(String messageKey) {
        if (isProcessed(messageKey)) {
            return false;
        }

        String sql = "INSERT OR IGNORE INTO message_idempotent (message_key, processed) VALUES (?, FALSE)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageKey);
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                localCache.put(messageKey, false);
                return true;
            }
            
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark message as processing", e);
        }
    }

    /**
     * 标记消息为已处理
     * @param messageKey 消息唯一键
     * @param result 处理结果
     */
    public void markProcessed(String messageKey, String result) {
        String sql = "INSERT OR REPLACE INTO message_idempotent (message_key, processed, result, updated_time) VALUES (?, TRUE, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageKey);
            pstmt.setString(2, result);
            pstmt.executeUpdate();
            
            localCache.put(messageKey, true);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark message as processed", e);
        }
    }

    /**
     * 获取处理结果
     * @param messageKey 消息唯一键
     * @return 处理结果，如果未处理返回null
     */
    public String getProcessingResult(String messageKey) {
        String sql = "SELECT result FROM message_idempotent WHERE message_key = ? AND processed = TRUE";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageKey);
            ResultSet rs = pstmt.executeQuery();
            
            return rs.next() ? rs.getString("result") : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get processing result", e);
        }
    }

    /**
     * 移除消息记录
     * @param messageKey 消息唯一键
     */
    public void remove(String messageKey) {
        String sql = "DELETE FROM message_idempotent WHERE message_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageKey);
            pstmt.executeUpdate();
            localCache.remove(messageKey);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove message record", e);
        }
    }

    /**
     * 获取统计信息
     */
    public String getStatistics() {
        String sql = "SELECT COUNT(*) as total, SUM(CASE WHEN processed = TRUE THEN 1 ELSE 0 END) as processed FROM message_idempotent";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                int total = rs.getInt("total");
                int processed = rs.getInt("processed");
                return String.format("总消息数: %d, 已处理: %d, 处理中: %d", 
                    total, processed, total - processed);
            }
            return "无统计数据";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get statistics", e);
        }
    }

    /**
     * 清理过期消息（保留7天）
     */
    public void cleanupExpired() {
        String sql = "DELETE FROM message_idempotent WHERE created_time < datetime('now', '-7 days')";
        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            System.out.println("清理过期消息: " + deleted + " 条");
            
            // 清理本地缓存
            localCache.clear();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cleanup expired messages", e);
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}