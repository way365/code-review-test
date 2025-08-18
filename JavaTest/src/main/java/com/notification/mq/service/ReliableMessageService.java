package com.notification.mq.service;

import com.notification.mq.entity.MessageEntity;
import com.notification.mq.repository.MessageRepository;
import com.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReliableMessageService {
    private final MessageRepository messageRepository;
    private final Map<String, NotificationService> messageHandlers;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public ReliableMessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
        this.messageHandlers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void registerHandler(String messageType, NotificationService handler) {
        messageHandlers.put(messageType, handler);
    }

    public void start() {
        if (running) return;
        running = true;
        
        // 每30秒执行一次重试任务
        scheduler.scheduleWithFixedDelay(
            this::processPendingMessages, 
            0, 30, TimeUnit.SECONDS
        );
        
        System.out.println("Reliable message service started");
    }

    public void stop() {
        if (!running) return;
        running = false;
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Reliable message service stopped");
    }

    public void sendMessage(String messageType, String destination, String content) {
        String messageId = generateMessageId();
        MessageEntity message = new MessageEntity(messageId, messageType, destination, content);
        
        messageRepository.save(message);
        
        // 立即尝试发送一次
        processMessage(message);
    }

    private void processPendingMessages() {
        if (!running) return;
        
        try {
            List<MessageEntity> pendingMessages = messageRepository.findPendingMessages();
            
            for (MessageEntity message : pendingMessages) {
                if (!running) break;
                processMessage(message);
            }
        } catch (Exception e) {
            System.err.println("Error processing pending messages: " + e.getMessage());
        }
    }

    private void processMessage(MessageEntity message) {
        NotificationService handler = messageHandlers.get(message.getMessageType());
        if (handler == null) {
            messageRepository.updateStatus(
                message.getMessageId(), 
                2, 
                "No handler found for message type: " + message.getMessageType()
            );
            return;
        }

        try {
            handler.sendNotification(message.getContent());
            
            // 发送成功
            messageRepository.updateStatus(message.getMessageId(), 1, null);
            System.out.println("Message sent successfully: " + message.getMessageId());
            
        } catch (Exception e) {
            // 发送失败，更新重试信息
            int newRetryCount = message.getRetryCount() + 1;
            
            if (newRetryCount >= message.getMaxRetry()) {
                // 达到最大重试次数，标记为死亡消息
                messageRepository.updateStatus(
                    message.getMessageId(), 
                    3, 
                    "Max retry exceeded: " + e.getMessage()
                );
                System.err.println("Message failed permanently: " + message.getMessageId());
            } else {
                // 计算下次重试时间（指数退避）
                long delaySeconds = calculateDelay(newRetryCount);
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
                
                messageRepository.updateRetry(message.getMessageId(), newRetryCount, nextRetry);
                System.err.println("Message failed, will retry later: " + message.getMessageId() + 
                                 ", retry: " + newRetryCount + ", next: " + nextRetry);
            }
        }
    }

    private long calculateDelay(int retryCount) {
        // 指数退避：30s, 60s, 120s, 240s...
        return 30L * (long) Math.pow(2, retryCount - 1);
    }

    private String generateMessageId() {
        return "MSG_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public boolean isRunning() {
        return running;
    }

    public void setMaxRetry(String messageType, int maxRetry) {
        // 可以设置特定消息类型的最大重试次数
    }
}