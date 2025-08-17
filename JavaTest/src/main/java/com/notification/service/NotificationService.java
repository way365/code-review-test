package com.notification.service;

/**
 * 通知服务接口
 */
public interface NotificationService {
    
    /**
     * 发送通知消息
     * @param message 消息内容
     * @param title 消息标题
     * @return 是否发送成功
     */
    boolean sendNotification(String message, String title);
    
    /**
     * 发送任务完成通知
     * @param taskName 任务名称
     * @param status 任务状态
     * @param duration 执行时长
     * @return 是否发送成功
     */
    boolean sendTaskCompletionNotification(String taskName, String status, long duration);
    
    /**
     * 发送异常通知
     * @param taskName 任务名称
     * @param errorMessage 错误信息
     * @return 是否发送成功
     */
    boolean sendErrorNotification(String taskName, String errorMessage);
}