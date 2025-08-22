package com.notification.manager;

import com.notification.config.NotificationConfig;
import com.notification.service.EnhancedNotificationService;
import com.notification.service.NotificationService;
import com.notification.service.EnhancedNotificationService.NotificationMessage;
import com.notification.service.EnhancedNotificationService.SendResult;
import com.notification.service.EnhancedNotificationService.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步通知管理器
 * 提供非阻塞的通知发送能力，支持优先级队列和批量处理
 */
public class AsyncNotificationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationManager.class);
    
    private final Map<String, NotificationService> services;
    private final PriorityBlockingQueue<NotificationTask> taskQueue;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong taskIdGenerator;
    private final NotificationConfig config;
    private volatile boolean running = true;
    
    /**
     * 通知任务
     */
    private static class NotificationTask implements Comparable<NotificationTask> {
        private final String taskId;
        private final String serviceName;
        private final NotificationMessage message;
        private final CompletableFuture<SendResult> future;
        private final long createTime;
        private final long scheduleTime;
        private final int retryCount;
        
        public NotificationTask(String taskId, String serviceName, NotificationMessage message, 
                              CompletableFuture<SendResult> future, long scheduleTime) {
            this(taskId, serviceName, message, future, scheduleTime, 0);
        }
        
        public NotificationTask(String taskId, String serviceName, NotificationMessage message, 
                              CompletableFuture<SendResult> future, long scheduleTime, int retryCount) {
            this.taskId = taskId;
            this.serviceName = serviceName;
            this.message = message;
            this.future = future;
            this.createTime = System.currentTimeMillis();
            this.scheduleTime = scheduleTime;
            this.retryCount = retryCount;
        }
        
        @Override
        public int compareTo(NotificationTask other) {
            // 先按优先级排序，再按创建时间排序
            int priorityCompare = Integer.compare(other.message.getPriority().getLevel(), 
                                                this.message.getPriority().getLevel());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.createTime, other.createTime);
        }
        
        // Getters
        public String getTaskId() { return taskId; }
        public String getServiceName() { return serviceName; }
        public NotificationMessage getMessage() { return message; }
        public CompletableFuture<SendResult> getFuture() { return future; }
        public long getCreateTime() { return createTime; }
        public long getScheduleTime() { return scheduleTime; }
        public int getRetryCount() { return retryCount; }
    }
    
    public AsyncNotificationManager() {
        this.services = new ConcurrentHashMap<>();
        this.taskQueue = new PriorityBlockingQueue<>();
        this.taskIdGenerator = new AtomicLong(0);
        this.config = NotificationConfig.getInstance();
        
        // 创建线程池
        this.executor = createThreadPool();
        
        // 创建调度器
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "async-notification-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动任务处理器
        startTaskProcessors();
        
        logger.info("异步通知管理器已启动 - 线程池大小: {}, 队列容量: {}", 
                   config.getAsyncThreadPoolSize(), config.getAsyncQueueSize());
    }
    
    /**
     * 添加通知服务
     * @param name 服务名称
     * @param service 通知服务
     */
    public void addService(String name, NotificationService service) {
        services.put(name, service);
        logger.info("已添加异步通知服务: {}", name);
    }
    
    /**
     * 异步发送通知到指定服务
     * @param serviceName 服务名称
     * @param message 通知消息
     * @return 发送结果Future
     */
    public CompletableFuture<SendResult> sendNotificationAsync(String serviceName, NotificationMessage message) {
        return sendNotificationAsync(serviceName, message, 0);
    }
    
    /**
     * 异步发送延迟通知
     * @param serviceName 服务名称
     * @param message 通知消息
     * @param delayMillis 延迟时间（毫秒）
     * @return 发送结果Future
     */
    public CompletableFuture<SendResult> sendNotificationAsync(String serviceName, NotificationMessage message, long delayMillis) {
        if (!running) {
            CompletableFuture<SendResult> future = new CompletableFuture<>();
            future.complete(new SendResult(false, message.getMessageId(), "通知管理器已关闭", 0));\n            return future;\n        }\n        \n        NotificationService service = services.get(serviceName);\n        if (service == null) {\n            CompletableFuture<SendResult> future = new CompletableFuture<>();\n            future.complete(new SendResult(false, message.getMessageId(), \"服务不存在: \" + serviceName, 0));\n            return future;\n        }\n        \n        CompletableFuture<SendResult> future = new CompletableFuture<>();\n        String taskId = generateTaskId();\n        long scheduleTime = System.currentTimeMillis() + delayMillis;\n        \n        NotificationTask task = new NotificationTask(taskId, serviceName, message, future, scheduleTime);\n        \n        if (delayMillis > 0) {\n            // 延迟任务\n            scheduler.schedule(() -> taskQueue.offer(task), delayMillis, TimeUnit.MILLISECONDS);\n        } else {\n            // 立即任务\n            taskQueue.offer(task);\n        }\n        \n        logger.debug(\"已提交异步通知任务 - ID: {}, 服务: {}, 延迟: {}ms\", taskId, serviceName, delayMillis);\n        return future;\n    }\n    \n    /**\n     * 异步发送通知到所有服务\n     * @param message 通知消息\n     * @return 发送结果Future列表\n     */\n    public Map<String, CompletableFuture<SendResult>> sendNotificationToAllAsync(NotificationMessage message) {\n        Map<String, CompletableFuture<SendResult>> results = new HashMap<>();\n        \n        for (String serviceName : services.keySet()) {\n            results.put(serviceName, sendNotificationAsync(serviceName, message));\n        }\n        \n        return results;\n    }\n    \n    /**\n     * 批量异步发送通知\n     * @param serviceName 服务名称\n     * @param messages 通知消息列表\n     * @return 发送结果Future列表\n     */\n    public List<CompletableFuture<SendResult>> sendBatchNotificationsAsync(String serviceName, List<NotificationMessage> messages) {\n        return messages.stream()\n                .map(msg -> sendNotificationAsync(serviceName, msg))\n                .collect(java.util.stream.Collectors.toList());\n    }\n    \n    /**\n     * 获取队列状态\n     * @return 队列状态信息\n     */\n    public Map<String, Object> getQueueStatus() {\n        Map<String, Object> status = new HashMap<>();\n        status.put(\"queueSize\", taskQueue.size());\n        status.put(\"activeThreads\", executor.getActiveCount());\n        status.put(\"completedTasks\", executor.getCompletedTaskCount());\n        status.put(\"totalTasks\", executor.getTaskCount());\n        status.put(\"running\", running);\n        return status;\n    }\n    \n    /**\n     * 优雅关闭\n     */\n    public void shutdown() {\n        logger.info(\"正在关闭异步通知管理器...\");\n        running = false;\n        \n        executor.shutdown();\n        scheduler.shutdown();\n        \n        try {\n            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {\n                logger.warn(\"强制关闭线程池\");\n                executor.shutdownNow();\n            }\n            \n            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {\n                scheduler.shutdownNow();\n            }\n        } catch (InterruptedException e) {\n            executor.shutdownNow();\n            scheduler.shutdownNow();\n            Thread.currentThread().interrupt();\n        }\n        \n        // 完成剩余的Future\n        while (!taskQueue.isEmpty()) {\n            NotificationTask task = taskQueue.poll();\n            if (task != null && !task.getFuture().isDone()) {\n                task.getFuture().complete(new SendResult(false, task.getMessage().getMessageId(), \"服务已关闭\", 0));\n            }\n        }\n        \n        logger.info(\"异步通知管理器已关闭\");\n    }\n    \n    /**\n     * 创建线程池\n     * @return ThreadPoolExecutor\n     */\n    private ThreadPoolExecutor createThreadPool() {\n        int corePoolSize = config.getAsyncThreadPoolSize();\n        int maximumPoolSize = corePoolSize * 2;\n        long keepAliveTime = 60L;\n        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(config.getAsyncQueueSize());\n        \n        ThreadFactory threadFactory = r -> {\n            Thread t = new Thread(r, \"async-notification-\" + taskIdGenerator.getAndIncrement());\n            t.setDaemon(true);\n            return t;\n        };\n        \n        RejectedExecutionHandler rejectedHandler = (r, executor) -> {\n            logger.warn(\"异步通知任务被拒绝，队列已满\");\n            // 可以在这里实现降级逻辑，比如同步发送\n        };\n        \n        return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, \n                                     TimeUnit.SECONDS, workQueue, threadFactory, rejectedHandler);\n    }\n    \n    /**\n     * 启动任务处理器\n     */\n    private void startTaskProcessors() {\n        for (int i = 0; i < config.getAsyncThreadPoolSize(); i++) {\n            executor.submit(this::processNotificationTasks);\n        }\n    }\n    \n    /**\n     * 处理通知任务\n     */\n    private void processNotificationTasks() {\n        while (running) {\n            try {\n                NotificationTask task = taskQueue.poll(1, TimeUnit.SECONDS);\n                if (task != null) {\n                    processTask(task);\n                }\n            } catch (InterruptedException e) {\n                Thread.currentThread().interrupt();\n                break;\n            } catch (Exception e) {\n                logger.error(\"处理通知任务异常\", e);\n            }\n        }\n    }\n    \n    /**\n     * 处理单个任务\n     * @param task 通知任务\n     */\n    private void processTask(NotificationTask task) {\n        if (task.getFuture().isDone()) {\n            return;\n        }\n        \n        try {\n            NotificationService service = services.get(task.getServiceName());\n            if (service == null) {\n                task.getFuture().complete(new SendResult(false, task.getMessage().getMessageId(), \n                                                       \"服务不存在: \" + task.getServiceName(), 0));\n                return;\n            }\n            \n            long startTime = System.currentTimeMillis();\n            boolean success;\n            \n            // 根据服务类型选择调用方法\n            NotificationMessage msg = task.getMessage();\n            if (service instanceof EnhancedNotificationService) {\n                EnhancedNotificationService enhancedService = (EnhancedNotificationService) service;\n                success = enhancedService.sendNotificationWithPriority(msg.getContent(), msg.getTitle(), msg.getPriority());\n            } else {\n                success = service.sendNotification(msg.getContent(), msg.getTitle());\n            }\n            \n            long duration = System.currentTimeMillis() - startTime;\n            SendResult result = new SendResult(success, msg.getMessageId(), \n                                             success ? null : \"发送失败\", duration);\n            task.getFuture().complete(result);\n            \n            logger.debug(\"异步通知任务完成 - ID: {}, 服务: {}, 结果: {}, 耗时: {}ms\", \n                        task.getTaskId(), task.getServiceName(), success, duration);\n            \n        } catch (Exception e) {\n            logger.error(\"执行异步通知任务异常 - ID: {}, 服务: {}\", task.getTaskId(), task.getServiceName(), e);\n            task.getFuture().complete(new SendResult(false, task.getMessage().getMessageId(), \n                                                   \"执行异常: \" + e.getMessage(), 0));\n        }\n    }\n    \n    /**\n     * 生成任务ID\n     * @return 任务ID\n     */\n    private String generateTaskId() {\n        return \"async_\" + System.currentTimeMillis() + \"_\" + taskIdGenerator.getAndIncrement();\n    }\n    \n    /**\n     * 注册关闭钩子\n     */\n    static {\n        Runtime.getRuntime().addShutdownHook(new Thread(() -> {\n            // 在应用关闭时，需要由具体实例来调用shutdown()\n        }));\n    }\n}