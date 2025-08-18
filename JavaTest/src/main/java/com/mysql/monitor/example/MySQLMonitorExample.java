package com.mysql.monitor.example;

import com.mysql.monitor.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MySQL监控组件使用示例
 * 演示如何使用主从延迟监控、多源监控和告警功能
 */
public class MySQLMonitorExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== MySQL主从延迟监控示例 ===\n");

        // 示例1: 基本监控
        example1_basicMonitor();
        
        // 示例2: 多源监控
        example2_multiSourceMonitor();
        
        // 示例3: 告警管理
        example3_alertManagement();
        
        // 示例4: 连接池使用
        example4_connectionPool();
    }

    /**
     * 示例1: 基本监控
     */
    private static void example1_basicMonitor() throws Exception {
        System.out.println("--- 示例1: 基本监控 ---");
        
        // 创建监控器（使用示例配置）
        MySQLReplicationMonitor monitor = new MySQLReplicationMonitor(
            "jdbc:mysql://localhost:3306/mysql",  // 主库
            "jdbc:mysql://localhost:3307/mysql",  // 从库
            "root",
            "password",
            3000  // 3秒检查间隔
        );

        // 添加延迟监听器
        monitor.addListener(new MySQLReplicationMonitor.ReplicationDelayListener() {
            @Override
            public void onDelayChange(long delaySeconds) {
                System.out.println("延迟变化: " + delaySeconds + " 秒");
            }

            @Override
            public void onError(Exception e) {
                System.err.println("监控错误: " + e.getMessage());
            }
        });

        // 启动监控
        monitor.start();
        
        // 运行5秒后停止
        Thread.sleep(5000);
        
        // 获取详细状态
        MySQLReplicationMonitor.ReplicationStatus status = monitor.getDetailedStatus();
        System.out.println("复制状态: " + status);
        
        monitor.stop();
        System.out.println("基本监控示例完成\n");
    }

    /**
     * 示例2: 多源监控
     */
    private static void example2_multiSourceMonitor() throws Exception {
        System.out.println("--- 示例2: 多源监控 ---");
        
        MultiSourceMonitor multiMonitor = new MultiSourceMonitor();
        
        // 添加多个监控源
        multiMonitor.addMonitor("master1-slave1", new MySQLReplicationMonitor(
            "jdbc:mysql://localhost:3306/mysql",
            "jdbc:mysql://localhost:3307/mysql",
            "root", "password", 5000
        ));
        
        multiMonitor.addMonitor("master2-slave2", new MySQLReplicationMonitor(
            "jdbc:mysql://localhost:3308/mysql",
            "jdbc:mysql://localhost:3309/mysql",
            "root", "password", 5000
        ));
        
        // 添加多源监听器
        multiMonitor.addListener(new MultiSourceMonitor.MultiSourceListener() {
            @Override
            public void onDelayChange(String sourceName, long delaySeconds) {
                System.out.println("[" + sourceName + "] 延迟: " + delaySeconds + " 秒");
            }

            @Override
            public void onError(String sourceName, Exception error) {
                System.err.println("[" + sourceName + "] 错误: " + error.getMessage());
            }

            @Override
            public void onSummary(MultiSourceMonitor.SummaryReport report) {
                System.out.println("汇总报告: " + report);
            }
        });
        
        // 启动所有监控
        multiMonitor.startAll();
        
        // 运行10秒后获取状态
        Thread.sleep(10000);
        
        Map<String, MultiSourceMonitor.MonitorStatus> statuses = multiMonitor.getAllStatuses();
        statuses.forEach((name, status) -> {
            System.out.println(name + ": " + status);
        });
        
        multiMonitor.stopAll();
        System.out.println("多源监控示例完成\n");
    }

    /**
     * 示例3: 告警管理
     */
    private static void example3_alertManagement() throws Exception {
        System.out.println("--- 示例3: 告警管理 ---");
        
        // 创建监控器
        MySQLReplicationMonitor monitor = new MySQLReplicationMonitor(
            "jdbc:mysql://localhost:3306/mysql",
            "jdbc:mysql://localhost:3307/mysql",
            "root", "password", 2000
        );
        
        // 创建告警管理器
        ReplicationAlertManager alertManager = new ReplicationAlertManager();
        
        // 添加告警规则
        alertManager.addRule("high_delay", new ReplicationAlertManager.AlertRule(
            monitor,
            ReplicationAlertManager.AlertType.DELAY_THRESHOLD,
            60,  // 60秒阈值
            300, // 5分钟冷却
            "主从延迟超过60秒"
        ));
        
        alertManager.addRule("replication_stopped", new ReplicationAlertManager.AlertRule(
            monitor,
            ReplicationAlertManager.AlertType.REPLICATION_STOPPED,
            0,   // 无阈值
            60,  // 1分钟冷却
            "复制进程停止"
        ));
        
        // 添加告警监听器
        alertManager.addListener(new ReplicationAlertManager.AlertListener() {
            @Override
            public void onAlert(ReplicationAlertManager.AlertEvent event) {
                System.out.println("⚠️ 告警: " + event.getRuleName() + 
                                 " 延迟: " + event.getCurrentDelay() + "秒");
            }

            @Override
            public void onRecovery(ReplicationAlertManager.RecoveryEvent event) {
                System.out.println("✅ 恢复: " + event.getRuleName() + 
                                 " 延迟: " + event.getCurrentDelay() + "秒");
            }
        });
        
        // 启动监控和告警
        monitor.start();
        alertManager.start();
        
        // 运行15秒
        Thread.sleep(15000);
        
        // 查看告警历史
        Map<String, ReplicationAlertManager.AlertHistory> history = alertManager.getAlertHistory();
        history.forEach((rule, hist) -> {
            System.out.println("规则 " + rule + ": 告警中=" + hist.isAlerting());
        });
        
        monitor.stop();
        alertManager.stop();
        System.out.println("告警管理示例完成\n");
    }

    /**
     * 示例4: 连接池使用
     */
    private static void example4_connectionPool() throws Exception {
        System.out.println("--- 示例4: 连接池使用 ---");
        
        // 创建连接池
        MySQLConnectionPool pool = new MySQLConnectionPool(
            "jdbc:mysql://localhost:3306/mysql",
            "root",
            "password",
            10,  // 最大10个连接
            "monitor_pool"
        );
        
        // 使用连接池执行查询
        try (Connection conn = pool.getConnection()) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS")) {
                
                if (rs.next()) {
                    System.out.println("从库状态查询成功");
                    System.out.println("Slave_IO_Running: " + rs.getString("Slave_IO_Running"));
                    System.out.println("Slave_SQL_Running: " + rs.getString("Slave_SQL_Running"));
                    System.out.println("Seconds_Behind_Master: " + rs.getLong("Seconds_Behind_Master"));
                }
            }
        }
        
        // 获取连接池状态
        MySQLConnectionPool.PoolStatus status = pool.getStatus();
        System.out.println("连接池状态: " + status);
        
        // 模拟并发使用
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try (Connection conn = pool.getConnection()) {
                    Thread.sleep(100); // 模拟查询时间
                    System.out.println("线程 " + threadId + " 使用连接完成");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        pool.close();
        System.out.println("连接池示例完成\n");
    }

    /**
     * 实际使用示例
     */
    public static void realWorldExample() {
        System.out.println("=== 实际使用配置示例 ===");
        
        // 配置示例
        String[][] configurations = {
            {"主库1", "jdbc:mysql://db1-master:3306/appdb", "jdbc:mysql://db1-slave:3306/appdb"},
            {"主库2", "jdbc:mysql://db2-master:3306/appdb", "jdbc:mysql://db2-slave:3306/appdb"},
            {"主库3", "jdbc:mysql://db3-master:3306/appdb", "jdbc:mysql://db3-slave:3306/appdb"}
        };
        
        MultiSourceMonitor monitor = new MultiSourceMonitor();
        ReplicationAlertManager alertManager = new ReplicationAlertManager();
        
        // 添加监控源
        for (String[] config : configurations) {
            String name = config[0];
            String masterUrl = config[1];
            String slaveUrl = config[2];
            
            MySQLReplicationMonitor replicationMonitor = new MySQLReplicationMonitor(
                masterUrl, slaveUrl, "monitor_user", "monitor_password", 5000
            );
            
            monitor.addMonitor(name, replicationMonitor);
            
            // 添加告警规则
            alertManager.addRule(name + "_high_delay", new ReplicationAlertManager.AlertRule(
                replicationMonitor,
                ReplicationAlertManager.AlertType.DELAY_THRESHOLD,
                30,  // 30秒阈值
                300, // 5分钟冷却
                name + "主从延迟超过30秒"
            ));
        }
        
        // 添加邮件通知监听器
        alertManager.addListener(new ReplicationAlertManager.AlertListener() {
            @Override
            public void onAlert(ReplicationAlertManager.AlertEvent event) {
                // 这里可以集成邮件发送
                System.out.println("发送邮件告警: " + event.getRuleName());
            }

            @Override
            public void onRecovery(ReplicationAlertManager.RecoveryEvent event) {
                // 这里可以集成邮件通知
                System.out.println("发送恢复邮件: " + event.getRuleName());
            }
        });
        
        // 启动所有服务
        monitor.startAll();
        alertManager.start();
        
        System.out.println("生产环境监控已启动");
    }
}