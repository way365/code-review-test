package com.notification;

import com.notification.executor.TaskExecutor;
import com.notification.manager.NotificationManager;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 主类 - 演示通知功能
 */
public class Main {
    
    public static void main(String[] args) {
        // 初始化通知管理器
        NotificationManager notificationManager = new NotificationManager();
        
        // 创建任务执行器
        TaskExecutor taskExecutor = new TaskExecutor(notificationManager);
        
        System.out.println("开始执行示例任务...");
        
        // 示例1：执行成功的任务
        taskExecutor.executeWithNotification(() -> {
            // 模拟任务执行
            simulateTask();
            return "任务完成";
        }, "数据同步任务");
        
        // 示例2：执行失败的任务
        try {
            taskExecutor.executeWithNotification(() -> {
                // 模拟失败的任务
                simulateFailingTask();
                return null;
            }, "文件处理任务");
        } catch (Exception e) {
            System.err.println("捕获到异常: " + e.getMessage());
        }
        
        // 示例3：微信公众号单独使用
        System.out.println("\n=== 微信公众号通知示例 ===");
        if (notificationManager.getService("wechat") != null) {
            notificationManager.getService("wechat")
                .sendTaskCompletionNotification("微信公众号测试", "成功", 1500);
        } else {
            System.out.println("微信公众号未配置，跳过示例");
        }
        
        // 示例4：自定义通知
        notificationManager.sendToAll("这是一个自定义测试消息", "测试通知");
        
        System.out.println("所有示例任务执行完成！");
    }
    
    private static void simulateTask() {
        try {
            // 模拟耗时操作
            Random random = new Random();
            int delay = random.nextInt(3000) + 1000; // 1-4秒
            TimeUnit.MILLISECONDS.sleep(delay);
            
            System.out.println("任务执行成功，耗时: " + delay + "毫秒");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("任务被中断", e);
        }
    }
    
    private static void simulateFailingTask() {
        try {
            // 模拟耗时操作
            TimeUnit.MILLISECONDS.sleep(1500);
            
            // 模拟随机失败
            Random random = new Random();
            if (random.nextBoolean()) {
                throw new RuntimeException("文件读取失败：权限不足");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("任务被中断", e);
        }
    }
}