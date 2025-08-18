package com.notification.mq.example;

import com.notification.mq.ReliableMessageManager;
import com.notification.mq.ReliableTaskExecutor;
import com.notification.service.impl.DingTalkNotificationService;
import com.notification.service.impl.FeishuNotificationService;
import com.notification.service.impl.WeChatNotificationService;

public class ReliableMessageExample {
    public static void main(String[] args) throws Exception {
        // 获取消息管理器单例
        ReliableMessageManager messageManager = ReliableMessageManager.getInstance();
        
        // 注册通知服务
        registerNotificationServices(messageManager);
        
        // 启动消息服务
        messageManager.start();
        
        // 创建可靠任务执行器
        ReliableTaskExecutor executor = new ReliableTaskExecutor(messageManager);
        
        try {
            System.out.println("=== 可靠消息必达组件演示 ===\n");
            
            // 示例1：直接发送可靠消息
            demoDirectMessages(messageManager);
            
            // 示例2：任务执行与可靠通知
            demoTaskExecution(executor);
            
            // 示例3：模拟网络异常，演示重试机制
            demoRetryMechanism(messageManager);
            
            System.out.println("\n=== 演示完成，等待消息处理 ===");
            
            // 等待消息处理完成
            Thread.sleep(10000);
            
        } finally {
            messageManager.stop();
        }
    }
    
    private static void registerNotificationServices(ReliableMessageManager messageManager) {
        // 注册钉钉通知服务
        String dingTalkWebhook = System.getProperty("dingtalk.webhook");
        String dingTalkSecret = System.getProperty("dingtalk.secret");
        if (dingTalkWebhook != null && dingTalkSecret != null) {
            messageManager.registerNotificationService(
                "dingtalk", 
                new DingTalkNotificationService(dingTalkWebhook, dingTalkSecret)
            );
        }
        
        // 注册飞书通知服务
        String feishuWebhook = System.getProperty("feishu.webhook");
        if (feishuWebhook != null) {
            messageManager.registerNotificationService(
                "feishu", 
                new FeishuNotificationService(feishuWebhook)
            );
        }
        
        // 注册微信通知服务
        String wechatAppId = System.getProperty("wechat.appid");
        String wechatAppSecret = System.getProperty("wechat.appsecret");
        String wechatTemplateId = System.getProperty("wechat.templateid");
        String wechatOpenId = System.getProperty("wechat.openid");
        if (wechatAppId != null && wechatAppSecret != null && 
            wechatTemplateId != null && wechatOpenId != null) {
            messageManager.registerNotificationService(
                "wechat", 
                new WeChatNotificationService(wechatAppId, wechatAppSecret, 
                                            wechatTemplateId, wechatOpenId)
            );
        }
        
        System.out.println("已注册的通知服务：");
        System.out.println("- 钉钉通知：" + (dingTalkWebhook != null ? "✅" : "❌"));
        System.out.println("- 飞书通知：" + (feishuWebhook != null ? "✅" : "❌"));
        System.out.println("- 微信通知：" + (wechatAppId != null ? "✅" : "❌"));
        System.out.println();
    }
    
    private static void demoDirectMessages(ReliableMessageManager messageManager) {
        System.out.println("=== 示例1：直接发送可靠消息 ===");
        
        // 发送钉钉消息（如果有配置）
        messageManager.sendDingTalkMessage("dingtalk-webhook", "钉钉可靠消息测试");
        
        // 发送飞书消息（如果有配置）
        messageManager.sendFeishuMessage("feishu-webhook", "飞书可靠消息测试");
        
        // 发送微信消息（如果有配置）
        messageManager.sendWeChatMessage("user-openid", "微信可靠消息测试");
        
        System.out.println("已发送可靠消息，系统将持续重试直到成功\n");
    }
    
    private static void demoTaskExecution(ReliableTaskExecutor executor) throws Exception {
        System.out.println("=== 示例2：任务执行与可靠通知 ===");
        
        // 成功任务
        executor.executeWithReliableNotification(
            "数据同步任务",
            "dingtalk",
            "dingtalk-webhook",
            () -> {
                simulateTask(1000);
                return "数据同步完成";
            }
        );
        
        // 失败任务
        try {
            executor.executeWithReliableNotification(
                "API调用任务",
                "feishu",
                "feishu-webhook",
                () -> {
                    simulateFailingTask();
                    return null;
                }
            );
        } catch (Exception e) {
            System.out.println("任务执行失败，已发送失败通知");
        }
        
        System.out.println("任务执行完成，通知已可靠发送\n");
    }
    
    private static void demoRetryMechanism(ReliableMessageManager messageManager) {
        System.out.println("=== 示例3：重试机制演示 ===");
        
        // 发送到一个无效的webhook，演示重试机制
        messageManager.sendDingTalkMessage("https://invalid-webhook.com", "重试机制测试消息");
        
        System.out.println("已发送测试消息到无效地址，系统将：");
        System.out.println("1. 立即尝试第一次发送（失败）");
        System.out.println("2. 30秒后重试第二次");
        System.out.println("3. 60秒后重试第三次");
        System.out.println("4. 120秒后重试第四次");
        System.out.println("5. 最终标记为死亡消息\n");
    }
    
    private static void simulateTask(long delay) throws InterruptedException {
        Thread.sleep(delay);
    }
    
    private static void simulateFailingTask() {
        throw new RuntimeException("网络连接超时");
    }
}