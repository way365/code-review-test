package com.notification.mq;

import com.notification.mq.repository.MessageRepository;
import com.notification.mq.service.ReliableMessageService;
import com.notification.service.NotificationService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ReliableMessageManager {
    private static ReliableMessageManager instance;
    private final Connection connection;
    private final MessageRepository messageRepository;
    private final ReliableMessageService messageService;

    private ReliableMessageManager() {
        try {
            // 使用SQLite作为本地消息存储
            String dbUrl = "jdbc:sqlite:reliable_message.db";
            this.connection = DriverManager.getConnection(dbUrl);
            this.messageRepository = new MessageRepository(connection);
            this.messageService = new ReliableMessageService(messageRepository);
            
            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize message manager", e);
        }
    }

    public static synchronized ReliableMessageManager getInstance() {
        if (instance == null) {
            instance = new ReliableMessageManager();
        }
        return instance;
    }

    public void registerNotificationService(String messageType, NotificationService service) {
        messageService.registerHandler(messageType, service);
    }

    public void sendReliableMessage(String messageType, String destination, String content) {
        messageService.sendMessage(messageType, destination, content);
    }

    public void sendTaskCompletion(String messageType, String destination, String taskName, long duration) {
        String content = String.format("任务完成通知：%s 执行成功，耗时 %d 毫秒", taskName, duration);
        sendReliableMessage(messageType, destination, content);
    }

    public void sendError(String messageType, String destination, String taskName, String error) {
        String content = String.format("任务失败通知：%s 执行失败，错误：%s", taskName, error);
        sendReliableMessage(messageType, destination, content);
    }

    public void start() {
        messageService.start();
    }

    public void stop() {
        messageService.stop();
    }

    public boolean isRunning() {
        return messageService.isRunning();
    }

    private void shutdown() {
        try {
            stop();
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    // 便捷方法
    public void sendDingTalkMessage(String webhook, String content) {
        sendReliableMessage("dingtalk", webhook, content);
    }

    public void sendFeishuMessage(String webhook, String content) {
        sendReliableMessage("feishu", webhook, content);
    }

    public void sendWeChatMessage(String openId, String content) {
        sendReliableMessage("wechat", openId, content);
    }
}