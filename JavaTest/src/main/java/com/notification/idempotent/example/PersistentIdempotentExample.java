package com.notification.idempotent.example;

import com.notification.idempotent.PersistentIdempotentService;
import com.notification.mq.ReliableMessageManager;
import com.notification.mq.entity.MessageEntity;
import com.notification.mq.service.NotificationService;

import java.util.concurrent.*;

/**
 * 持久化幂等消息示例
 * 演示基于SQLite的持久化幂等性保证
 */
public class PersistentIdempotentExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 持久化幂等消息示例 ===\n");

        // 初始化持久化幂等服务
        PersistentIdempotentService idempotentService = new PersistentIdempotentService("idempotent.db");
        
        // 注册通知服务
        ReliableMessageManager.getInstance().registerNotificationService("dingtalk", new MockNotificationService());

        // 示例1: 基本幂等性
        example1_basicIdempotency(idempotentService);
        
        // 示例2: 并发幂等性
        example2_concurrentIdempotency(idempotentService);
        
        // 示例3: 重启后保持幂等性
        example3_restartPersistence(idempotentService);
        
        // 示例4: 统计和清理
        example4_statisticsAndCleanup(idempotentService);

        // 关闭服务
        idempotentService.close();
    }

    /**
     * 示例1: 基本幂等性
     */
    private static void example1_basicIdempotency(PersistentIdempotentService idempotentService) {
        System.out.println("--- 示例1: 基本幂等性 ---");
        
        String messageKey = "order_001";
        
        // 第一次处理
        boolean canProcess1 = idempotentService.markProcessing(messageKey);
        if (canProcess1) {
            System.out.println("第一次处理消息: " + messageKey);
            idempotentService.markProcessed(messageKey, "订单创建成功");
        }
        
        // 第二次处理（幂等）
        boolean canProcess2 = idempotentService.markProcessing(messageKey);
        if (!canProcess2) {
            System.out.println("消息已处理，跳过: " + messageKey);
        }
        
        // 获取处理结果
        String result = idempotentService.getProcessingResult(messageKey);
        System.out.println("处理结果: " + result);
        System.out.println();
    }

    /**
     * 示例2: 并发幂等性
     */
    private static void example2_concurrentIdempotency(PersistentIdempotentService idempotentService) {
        System.out.println("--- 示例2: 并发幂等性 ---");
        
        String messageKey = "payment_002";
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    boolean canProcess = idempotentService.markProcessing(messageKey);
                    if (canProcess) {
                        System.out.println("线程 " + threadId + " 开始处理消息: " + messageKey);
                        Thread.sleep(1000); // 模拟处理时间
                        idempotentService.markProcessed(messageKey, "支付完成");
                        System.out.println("线程 " + threadId + " 完成处理");
                    } else {
                        System.out.println("线程 " + threadId + " 跳过重复消息");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("并发处理完成");
        System.out.println();
    }

    /**
     * 示例3: 重启后保持幂等性
     */
    private static void example3_restartPersistence(PersistentIdempotentService idempotentService) {
        System.out.println("--- 示例3: 重启后保持幂等性 ---");
        
        // 模拟重启前的消息处理
        String messageKey = "user_003";
        if (!idempotentService.isProcessed(messageKey)) {
            System.out.println("模拟重启前处理消息: " + messageKey);
            idempotentService.markProcessed(messageKey, "用户注册完成");
        }
        
        // 模拟重启后
        PersistentIdempotentService newService = null;
        try {
            newService = new PersistentIdempotentService("idempotent.db");
            
            // 重启后检查消息状态
            boolean isProcessed = newService.isProcessed(messageKey);
            String result = newService.getProcessingResult(messageKey);
            
            System.out.println("重启后消息状态: " + (isProcessed ? "已处理" : "未处理"));
            System.out.println("重启后处理结果: " + result);
            
            // 尝试重新处理
            boolean canProcess = newService.markProcessing(messageKey);
            System.out.println("重启后是否可以重新处理: " + canProcess);
            
            newService.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println();
    }

    /**
     * 示例4: 统计和清理
     */
    private static void example4_statisticsAndCleanup(PersistentIdempotentService idempotentService) {
        System.out.println("--- 示例4: 统计和清理 ---");
        
        // 添加一些测试数据
        for (int i = 1; i <= 5; i++) {
            String key = "test_" + i;
            if (i % 2 == 0) {
                idempotentService.markProcessed(key, "处理结果" + i);
            } else {
                idempotentService.markProcessing(key);
            }
        }
        
        // 获取统计信息
        String stats = idempotentService.getStatistics();
        System.out.println("统计信息: " + stats);
        
        // 清理单个消息
        idempotentService.remove("test_1");
        System.out.println("清理单个消息记录");
        
        // 再次获取统计信息
        stats = idempotentService.getStatistics();
        System.out.println("清理后统计: " + stats);
        
        // 清理过期消息（模拟）
        idempotentService.cleanupExpired();
        System.out.println("已执行过期消息清理");
        System.out.println();
    }

    /**
     * 模拟通知服务
     */
    static class MockNotificationService implements NotificationService {
        @Override
        public boolean send(MessageEntity message) {
            System.out.println("发送通知: " + message.getContent());
            return true;
        }

        @Override
        public String getServiceName() {
            return "MockNotificationService";
        }
    }
}