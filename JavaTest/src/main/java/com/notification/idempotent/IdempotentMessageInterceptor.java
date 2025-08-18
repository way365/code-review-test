package com.notification.idempotent;

import com.notification.mq.entity.MessageEntity;
import java.util.function.Function;

/**
 * 消息幂等拦截器
 * 用于在消息处理链路中插入幂等性检查
 */
public class IdempotentMessageInterceptor {
    private final MessageIdempotentService idempotentService;
    private final Function<MessageEntity, String> messageIdExtractor;

    public IdempotentMessageInterceptor() {
        this(new MessageIdempotentService(), 
             message -> message.getMessageId() + "_" + message.getDestination());
    }

    public IdempotentMessageInterceptor(MessageIdempotentService idempotentService,
                                    Function<MessageEntity, String> messageIdExtractor) {
        this.idempotentService = idempotentService;
        this.messageIdExtractor = messageIdExtractor;
    }

    /**
     * 拦截消息处理
     * @param message 消息实体
     * @param processor 消息处理器
     * @return 处理结果，如果消息已处理则返回缓存结果
     * @throws Exception 如果处理失败
     */
    public Object intercept(MessageEntity message, MessageProcessor processor) throws Exception {
        String messageKey = messageIdExtractor.apply(message);
        
        // 检查是否已处理
        if (idempotentService.isProcessed(messageKey)) {
            System.out.println("消息已处理，跳过: " + messageKey);
            return idempotentService.getProcessingResult(messageKey);
        }

        // 标记为处理中
        if (!idempotentService.markProcessing(messageKey)) {
            System.out.println("消息正在处理中，跳过: " + messageKey);
            return null;
        }

        try {
            // 执行实际处理
            Object result = processor.process(message);
            
            // 标记为已处理
            idempotentService.markProcessed(messageKey, result);
            
            return result;
            
        } catch (Exception e) {
            // 处理失败，移除处理中标记，允许重试
            idempotentService.remove(messageKey);
            throw e;
        }
    }

    /**
     * 消息处理器接口
     */
    @FunctionalInterface
    public interface MessageProcessor {
        Object process(MessageEntity message) throws Exception;
    }

    /**
     * 获取幂等服务
     */
    public MessageIdempotentService getIdempotentService() {
        return idempotentService;
    }

    /**
     * 清理指定消息的记录
     */
    public void clearMessageRecord(MessageEntity message) {
        String messageKey = messageIdExtractor.apply(message);
        idempotentService.remove(messageKey);
    }

    /**
     * 清理所有消息记录
     */
    public void clearAll() {
        idempotentService.clearAll();
    }
}