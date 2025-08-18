package com.notification.idempotent.example;

import com.notification.idempotent.IdempotentMessageManager;
import com.notification.service.impl.DingTalkNotificationService;
import com.notification.service.impl.FeishuNotificationService;
import com.notification.service.impl.WeChatNotificationService;

public class IdempotentMessageExample {
    public static void main(String[] args) throws Exception {
        // 获取幂等消息管理器
        IdempotentMessageManager manager = IdempotentMessageManager.getInstance();
        
        // 注册幂等处理器
        registerIdempotentProcessors(manager);
        
        // 启动服务
        manager.start();
        
        try {
            System.out.println("=== 消息幂等组件演示 ===\n");
            
            // 示例1：演示幂等性
            demoIdempotency(manager);
            
            // 示例2：演示并发幂等
            demoConcurrentIdempotency(manager);
            
            // 示例3：演示自定义消息ID
            demoCustomMessageId(manager);
            
            // 示例4：演示统计和清理
            demoStatistics(manager);
            
            System.out.println("\n=== 演示完成 ===");
            
        } finally {
            manager.stop();
        }
    }
    
    private static void registerIdempotentProcessors(IdempotentMessageManager manager) {
        // 注册钉钉幂等处理器
        String dingTalkWebhook = System.getProperty("dingtalk.webhook", "https://oapi.dingtalk.com/robot/send");
        String dingTalkSecret = System.getProperty("dingtalk.secret", "demo-secret");
        manager.registerIdempotentProcessor("dingtalk", 
            new DingTalkNotificationService(dingTalkWebhook, dingTalkSecret));
        
        // 注册飞书幂等处理器
        String feishuWebhook = System.getProperty("feishu.webhook", "https://open.feishu.cn/open-apis/bot/v2/hook");
        manager.registerIdempotentProcessor("feishu", 
            new FeishuNotificationService(feishuWebhook));
        
        // 注册微信幂等处理器（带自定义消息ID提取器）
        String wechatAppId = System.getProperty("wechat.appid", "demo-appid");
        String wechatAppSecret = System.getProperty("wechat.appsecret", "demo-secret");
        String wechatTemplateId = System.getProperty("wechat.templateid", "demo-template");
        String wechatOpenId = System.getProperty("wechat.openid", "demo-openid");
        
        manager.registerIdempotentProcessor("wechat", 
            new WeChatNotificationService(wechatAppId, wechatAppSecret, 
                                        wechatTemplateId, wechatOpenId),
            content -> extractMessageIdFromContent(content));
        
        System.out.println("已注册幂等处理器：");
        System.out.println("- 钉钉幂等处理器 ✅");
        System.out.println("- 飞书幂等处理器 ✅");
        System.out.println("- 微信幂等处理器（自定义消息ID） ✅\n");
    }
    
    private static void demoIdempotency(IdempotentMessageManager manager) throws InterruptedException {
        System.out.println("=== 示例1：消息幂等性演示 ===");
        
        String messageId = "MSG_12345";
        String content = "测试幂等消息: " + messageId;
        
        // 第一次发送
        System.out.println("第一次发送消息...");
        manager.sendIdempotentMessage("dingtalk", "webhook-url", content);
        
        // 等待处理完成
        Thread.sleep(2000);
        
        // 第二次发送相同消息（应该被幂等处理）
        System.out.println("第二次发送相同消息（应该被幂等处理）...");
        manager.sendIdempotentMessage("dingtalk", "webhook-url", content);
        
        // 第三次发送相同消息
        System.out.println("第三次发送相同消息（应该被幂等处理）...");
        manager.sendIdempotentMessage("dingtalk", "webhook-url", content);
        
        System.out.println("幂等性演示完成\n");
    }
    
    private static void demoConcurrentIdempotency(IdempotentMessageManager manager) throws InterruptedException {
        System.out.println("=== 示例2：并发幂等性演示 ===");
        
        String messageId = "CONCURRENT_MSG_67890";
        String content = "并发测试消息: " + messageId;
        
        // 模拟并发发送
        System.out.println("模拟并发发送相同消息...");
        
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    System.out.println("线程 " + threadId + " 开始发送...");
                    manager.sendIdempotentMessage("feishu", "webhook-url", content);
                    System.out.println("线程 " + threadId + " 完成发送");
                } catch (Exception e) {
                    System.err.println("线程 " + threadId + " 发送失败: " + e.getMessage());
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("并发幂等性演示完成\n");
    }
    
    private static void demoCustomMessageId(IdempotentMessageManager manager) {
        System.out.println("=== 示例3：自定义消息ID演示 ===");
        
        // 发送带业务ID的消息
        String businessMessage = "ORDER_20240818_001|用户下单通知：用户ID=12345, 订单ID=67890";
        
        System.out.println("发送带业务ID的消息...");
        manager.sendIdempotentMessage("wechat", "user-openid", businessMessage);
        
        // 再次发送相同业务ID的消息
        System.out.println("再次发送相同业务ID的消息（应该被幂等处理）...");
        manager.sendIdempotentMessage("wechat", "user-openid", businessMessage);
        
        System.out.println("自定义消息ID演示完成\n");
    }
    
    private static void demoStatistics(IdempotentMessageManager manager) throws InterruptedException {
        System.out.println("=== 示例4：统计和清理演示 ===");
        
        // 等待处理完成
        Thread.sleep(3000);
        
        // 显示统计信息
        System.out.println(manager.getStatistics());
        
        // 清理钉钉消息记录
        System.out.println("清理钉钉消息记录...");
        manager.clearMessageRecords("dingtalk");
        
        // 再次显示统计信息
        System.out.println("清理后统计：");
        System.out.println(manager.getStatistics());
    }
    
    /**
     * 从消息内容中提取业务ID作为幂等键
     */
    private static String extractMessageIdFromContent(String content) {
        // 假设消息格式为：ORDER_XXXXX|消息内容
        if (content.contains("|")) {
            return content.split("\\|")[0];
        }
        return "DEFAULT_" + content.hashCode();
    }
}