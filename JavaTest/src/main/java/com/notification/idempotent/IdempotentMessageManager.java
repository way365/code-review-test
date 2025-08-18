package com.notification.idempotent;

import com.notification.mq.ReliableMessageManager;
import com.notification.service.NotificationService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等消息管理器
 * 集成可靠消息管理器和幂等处理
 */
public class IdempotentMessageManager {
    private static volatile IdempotentMessageManager instance;
    private final ReliableMessageManager reliableManager;
    private final Map<String, IdempotentMessageProcessor> processors;

    private IdempotentMessageManager() {
        this.reliableManager = ReliableMessageManager.getInstance();
        this.processors = new ConcurrentHashMap<>();
    }

    public static IdempotentMessageManager getInstance() {
        if (instance == null) {
            synchronized (IdempotentMessageManager.class) {
                if (instance == null) {
                    instance = new IdempotentMessageManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册幂等消息处理器
     * @param messageType 消息类型
     * @param service 通知服务
     */
    public void registerIdempotentProcessor(String messageType, NotificationService service) {
        IdempotentMessageProcessor processor = new IdempotentMessageProcessor(service);
        processors.put(messageType, processor);
        reliableManager.registerNotificationService(messageType, processor);
    }

    /**
     * 注册带自定义消息ID提取器的处理器
     * @param messageType 消息类型
     * @param service 通知服务
     * @param messageIdExtractor 消息ID提取器
     */
    public void registerIdempotentProcessor(String messageType, 
                                          NotificationService service,
                                          java.util.function.Function<String, String> messageIdExtractor) {
        IdempotentMessageProcessor processor = new IdempotentMessageProcessor(
            new MessageIdempotentService(), service, messageIdExtractor);
        processors.put(messageType, processor);
        reliableManager.registerNotificationService(messageType, processor);
    }

    /**
     * 发送幂等消息
     * @param messageType 消息类型
     * @param destination 目标地址
     * @param content 消息内容
     */
    public void sendIdempotentMessage(String messageType, String destination, String content) {
        reliableManager.sendReliableMessage(messageType, destination, content);
    }

    /**
     * 发送幂等任务完成通知
     */
    public void sendIdempotentTaskCompletion(String messageType, String destination, 
                                           String taskName, long duration) {
        reliableManager.sendTaskCompletion(messageType, destination, taskName, duration);
    }

    /**
     * 发送幂等错误通知
     */
    public void sendIdempotentError(String messageType, String destination, 
                                  String taskName, String error) {
        reliableManager.sendError(messageType, destination, taskName, error);
    }

    /**
     * 启动服务
     */
    public void start() {
        reliableManager.start();
    }

    /**
     * 停止服务
     */
    public void stop() {
        reliableManager.stop();
        
        // 清理所有处理器
        processors.values().forEach(processor -> 
            processor.getIdempotentService().shutdown()
        );
        processors.clear();
    }

    /**
     * 获取消息统计信息
     */
    public String getStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== 幂等消息统计 ===\n");
        
        processors.forEach((type, processor) -> {
            MessageIdempotentService service = processor.getIdempotentService();
            stats.append(String.format("%s: 已处理=%d, 处理中=%d\n", 
                type, 
                service.getProcessedCount(),
                service.getProcessingCount()));
        });
        
        return stats.toString();
    }

    /**
     * 清理指定类型的消息记录
     */
    public void clearMessageRecords(String messageType) {
        IdempotentMessageProcessor processor = processors.get(messageType);
        if (processor != null) {
            processor.getIdempotentService().clearAll();
        }
    }

    /**
     * 获取底层可靠消息管理器
     */
    public ReliableMessageManager getReliableManager() {
        return reliableManager;
    }
}