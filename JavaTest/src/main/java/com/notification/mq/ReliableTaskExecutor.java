package com.notification.mq;

import com.notification.mq.ReliableMessageManager;
import java.util.concurrent.Callable;

public class ReliableTaskExecutor {
    private final ReliableMessageManager messageManager;

    public ReliableTaskExecutor(ReliableMessageManager messageManager) {
        this.messageManager = messageManager;
    }

    public <T> T executeWithReliableNotification(String taskName, 
                                               String messageType, 
                                               String destination, 
                                               Callable<T> task) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = task.call();
            long duration = System.currentTimeMillis() - startTime;
            
            // 任务成功，发送成功通知
            messageManager.sendTaskCompletion(messageType, destination, taskName, duration);
            
            return result;
        } catch (Exception e) {
            // 任务失败，发送失败通知
            messageManager.sendError(messageType, destination, taskName, e.getMessage());
            throw e;
        }
    }

    public void executeWithReliableNotification(String taskName,
                                               String messageType,
                                               String destination,
                                               Runnable task) throws Exception {
        executeWithReliableNotification(taskName, messageType, destination, () -> {
            task.run();
            return null;
        });
    }
}