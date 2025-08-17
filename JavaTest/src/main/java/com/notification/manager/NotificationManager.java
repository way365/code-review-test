package com.notification.manager;

import com.notification.service.NotificationService;
import com.notification.service.impl.DingTalkNotificationService;
import com.notification.service.impl.FeishuNotificationService;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知管理器
 */
public class NotificationManager {
    
    private final Map<String, NotificationService> services = new HashMap<>();
    
    public NotificationManager() {
        // 初始化默认服务
        initializeDefaultServices();
    }
    
    /**
     * 添加通知服务
     * @param name 服务名称
     * @param service 通知服务实例
     */
    public void addService(String name, NotificationService service) {
        services.put(name, service);
    }
    
    /**
     * 获取通知服务
     * @param name 服务名称
     * @return 通知服务实例
     */
    public NotificationService getService(String name) {
        return services.get(name);
    }
    
    /**
     * 发送通知到所有服务
     * @param message 消息内容
     * @param title 消息标题
     * @return 发送结果
     */
    public Map<String, Boolean> sendToAll(String message, String title) {
        Map<String, Boolean> results = new HashMap<>();
        for (Map.Entry<String, NotificationService> entry : services.entrySet()) {
            boolean success = entry.getValue().sendNotification(message, title);
            results.put(entry.getKey(), success);
        }
        return results;
    }
    
    /**
     * 发送任务完成通知到所有服务
     * @param taskName 任务名称
     * @param status 任务状态
     * @param duration 执行时长
     * @return 发送结果
     */
    public Map<String, Boolean> sendTaskCompletionToAll(String taskName, String status, long duration) {
        Map<String, Boolean> results = new HashMap<>();
        for (Map.Entry<String, NotificationService> entry : services.entrySet()) {
            boolean success = entry.getValue().sendTaskCompletionNotification(taskName, status, duration);
            results.put(entry.getKey(), success);
        }
        return results;
    }
    
    /**
     * 发送异常通知到所有服务
     * @param taskName 任务名称
     * @param errorMessage 错误信息
     * @return 发送结果
     */
    public Map<String, Boolean> sendErrorToAll(String taskName, String errorMessage) {
        Map<String, Boolean> results = new HashMap<>();
        for (Map.Entry<String, NotificationService> entry : services.entrySet()) {
            boolean success = entry.getValue().sendErrorNotification(taskName, errorMessage);
            results.put(entry.getKey(), success);
        }
        return results;
    }
    
    private void initializeDefaultServices() {
        // 从配置中读取URL
        String dingTalkWebhook = System.getProperty("dingtalk.webhook", "https://oapi.dingtalk.com/robot/send");
        String dingTalkSecret = System.getProperty("dingtalk.secret", "");
        
        String feishuWebhook = System.getProperty("feishu.webhook", "https://open.feishu.cn/open-apis/bot/v2/hook/");
        
        services.put("dingtalk", new DingTalkNotificationService(dingTalkWebhook, dingTalkSecret));
        services.put("feishu", new FeishuNotificationService(feishuWebhook));
    }
}