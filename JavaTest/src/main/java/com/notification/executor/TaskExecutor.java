package com.notification.executor;

import com.notification.manager.NotificationManager;

import java.util.concurrent.Callable;

/**
 * 任务执行器，集成通知功能
 */
public class TaskExecutor {
    
    private final NotificationManager notificationManager;
    
    public TaskExecutor(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }
    
    /**
     * 执行任务并发送通知
     * @param task 任务
     * @param taskName 任务名称
     * @param <T> 任务返回类型
     * @return 任务执行结果
     */
    public <T> T executeWithNotification(Callable<T> task, String taskName) {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = task.call();
            long duration = System.currentTimeMillis() - startTime;
            
            // 发送成功通知
            notificationManager.sendTaskCompletionToAll(taskName, "成功", duration);
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // 发送异常通知
            notificationManager.sendErrorToAll(taskName, e.getMessage());
            
            // 重新抛出异常
            throw new RuntimeException("任务执行失败: " + taskName, e);
        }
    }
    
    /**
     * 执行Runnable任务并发送通知
     * @param task 任务
     * @param taskName 任务名称
     */
    public void executeWithNotification(Runnable task, String taskName) {
        executeWithNotification(() -> {
            task.run();
            return null;
        }, taskName);
    }
}