package com.notification.idempotent;

import com.notification.mq.entity.MessageEntity;
import com.notification.service.NotificationService;
import java.util.function.Function;

/**
 * 幂等消息处理器
 * 包装NotificationService，提供幂等性保证
 */
public class IdempotentMessageProcessor {
    private final MessageIdempotentService idempotentService;
    private final NotificationService delegate;
    private final Function<String, String> messageIdExtractor;

    public IdempotentMessageProcessor(NotificationService delegate) {
        this(new MessageIdempotentService(), delegate, content -> content);
    }

    public IdempotentMessageProcessor(MessageIdempotentService idempotentService, 
                                    NotificationService delegate,
                                    Function<String, String> messageIdExtractor) {
        this.idempotentService = idempotentService;
        this.delegate = delegate;
        this.messageIdExtractor = messageIdExtractor;
    }

    /**
     * 发送消息（幂等处理）
     * @param content 消息内容
     * @throws Exception 如果发送失败
     */
    public void sendNotification(String content) throws Exception {
        String messageId = messageIdExtractor.apply(content);
        
        // 检查是否已处理
        if (idempotentService.isProcessed(messageId)) {
            System.out.println("消息已处理，跳过重复发送: " + messageId);
            return;
        }

        // 标记为处理中（防止并发重复处理）
        if (!idempotentService.markProcessing(messageId)) {
            System.out.println("消息正在处理中，跳过: " + messageId);
            return;
        }

        try {
            // 执行实际发送
            delegate.sendNotification(content);
            
            // 标记为已处理
            idempotentService.markProcessed(messageId, "SUCCESS");
            System.out.println("消息发送成功: " + messageId);
            
        } catch (Exception e) {
            // 发送失败，移除处理中标记，允许重试
            idempotentService.remove(messageId);
            throw new Exception("消息发送失败: " + messageId, e);
        }
    }

    /**
     * 发送任务完成通知（幂等处理）
     */
    public void sendTaskCompletionNotification(String taskName, long duration) throws Exception {
        String content = String.format("任务完成：%s，耗时：%d ms", taskName, duration);
        sendNotification(content);
    }

    /**
     * 发送错误通知（幂等处理）
     */
    public void sendErrorNotification(String taskName, String error) throws Exception {
        String content = String.format("任务失败：%s，错误：%s", taskName, error);
        sendNotification(content);
    }

    /**
     * 获取底层NotificationService
     */
    public NotificationService getDelegate() {
        return delegate;
    }

    /**
     * 获取幂等服务
     */
    public MessageIdempotentService getIdempotentService() {
        return idempotentService;
    }
}