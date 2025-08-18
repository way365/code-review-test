package com.notification.mq.repository;

import com.notification.mq.entity.MessageEntity;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageRepository {
    private final Connection connection;

    public MessageRepository(Connection connection) {
        this.connection = connection;
        initializeTable();
    }

    private void initializeTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS reliable_message (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id VARCHAR(64) UNIQUE NOT NULL," +
                "message_type VARCHAR(32) NOT NULL," +
                "destination VARCHAR(255) NOT NULL," +
                "content TEXT NOT NULL," +
                "status INTEGER DEFAULT 0," +
                "retry_count INTEGER DEFAULT 0," +
                "max_retry INTEGER DEFAULT 3," +
                "next_retry_time DATETIME," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "error_message TEXT" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize message table", e);
        }
    }

    public void save(MessageEntity message) {
        String sql = "INSERT INTO reliable_message (message_id, message_type, destination, content, status, retry_count, max_retry, next_retry_time, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, message.getMessageId());
            pstmt.setString(2, message.getMessageType());
            pstmt.setString(3, message.getDestination());
            pstmt.setString(4, message.getContent());
            pstmt.setInt(5, message.getStatus());
            pstmt.setInt(6, message.getRetryCount());
            pstmt.setInt(7, message.getMaxRetry());
            pstmt.setTimestamp(8, Timestamp.valueOf(message.getNextRetryTime()));
            pstmt.setString(9, message.getErrorMessage());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save message", e);
        }
    }

    public List<MessageEntity> findPendingMessages() {
        String sql = "SELECT * FROM reliable_message WHERE status = 0 AND next_retry_time <= ? ORDER BY create_time ASC LIMIT 100";

        List<MessageEntity> messages = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                messages.add(mapResultToEntity(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending messages", e);
        }
        return messages;
    }

    public void updateStatus(String messageId, int status, String errorMessage) {
        String sql = "UPDATE reliable_message SET status = ?, update_time = CURRENT_TIMESTAMP, error_message = ? WHERE message_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status);
            pstmt.setString(2, errorMessage);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update message status", e);
        }
    }

    public void updateRetry(String messageId, int retryCount, LocalDateTime nextRetryTime) {
        String sql = "UPDATE reliable_message SET retry_count = ?, next_retry_time = ?, update_time = CURRENT_TIMESTAMP WHERE message_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, retryCount);
            pstmt.setTimestamp(2, Timestamp.valueOf(nextRetryTime));
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update retry info", e);
        }
    }

    public MessageEntity findByMessageId(String messageId) {
        String sql = "SELECT * FROM reliable_message WHERE message_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, messageId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapResultToEntity(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find message by ID", e);
        }
    }

    private MessageEntity mapResultToEntity(ResultSet rs) throws SQLException {
        MessageEntity entity = new MessageEntity();
        entity.setId(rs.getLong("id"));
        entity.setMessageId(rs.getString("message_id"));
        entity.setMessageType(rs.getString("message_type"));
        entity.setDestination(rs.getString("destination"));
        entity.setContent(rs.getString("content"));
        entity.setStatus(rs.getInt("status"));
        entity.setRetryCount(rs.getInt("retry_count"));
        entity.setMaxRetry(rs.getInt("max_retry"));
        entity.setNextRetryTime(rs.getTimestamp("next_retry_time").toLocalDateTime());
        entity.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        entity.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        entity.setErrorMessage(rs.getString("error_message"));
        return entity;
    }
}