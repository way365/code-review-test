package com.notification.http;

import com.notification.config.NotificationConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP连接池管理器
 * 提供优化的HTTP连接池配置和生命周期管理
 */
public class HttpConnectionManager implements Closeable {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpConnectionManager.class);
    
    private static volatile HttpConnectionManager instance;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final CloseableHttpClient httpClient;
    private final ScheduledExecutorService cleanupExecutor;
    private final NotificationConfig config;
    
    private HttpConnectionManager() {
        this.config = NotificationConfig.getInstance();
        this.connectionManager = createConnectionManager();
        this.httpClient = createHttpClient();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "http-connection-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // 启动连接清理任务
        startConnectionCleanup();
        
        logger.info("HTTP连接池管理器已初始化 - 最大连接数: {}, 单路由最大连接数: {}", 
                   config.getConnectionPoolMaxTotal(), config.getConnectionPoolMaxPerRoute());
    }
    
    /**
     * 获取单例实例
     * @return HttpConnectionManager实例
     */
    public static HttpConnectionManager getInstance() {
        if (instance == null) {
            synchronized (HttpConnectionManager.class) {
                if (instance == null) {
                    instance = new HttpConnectionManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取HTTP客户端
     * @return CloseableHttpClient实例
     */
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }
    
    /**
     * 获取连接池统计信息
     * @return 连接池状态字符串
     */
    public String getPoolStats() {
        return connectionManager.getTotalStats().toString();
    }
    
    /**
     * 创建连接管理器
     * @return PoolingHttpClientConnectionManager
     */
    private PoolingHttpClientConnectionManager createConnectionManager() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        
        // 设置连接池参数
        cm.setMaxTotal(config.getConnectionPoolMaxTotal());
        cm.setDefaultMaxPerRoute(config.getConnectionPoolMaxPerRoute());
        
        // 设置连接存活时间
        cm.setDefaultSocketConfig(
            org.apache.hc.core5.http.config.SocketConfig.custom()
                .setSoTimeout(config.getConnectionPoolSocketTimeout(), TimeUnit.MILLISECONDS)
                .build()
        );
        
        return cm;
    }
    
    /**
     * 创建HTTP客户端
     * @return CloseableHttpClient
     */
    private CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(config.getConnectionPoolConnectionTimeout(), TimeUnit.MILLISECONDS)
            .setResponseTimeout(config.getNotificationTimeout(), TimeUnit.MILLISECONDS)
            .build();
        
        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .build();
    }
    
    /**
     * 启动连接清理任务
     */
    private void startConnectionCleanup() {
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try {
                // 清理过期和空闲连接
                connectionManager.closeExpired();
                connectionManager.closeIdle(TimeValue.ofSeconds(30));
                
                if (logger.isDebugEnabled()) {
                    logger.debug("HTTP连接池清理完成，当前状态: {}", getPoolStats());
                }
            } catch (Exception e) {
                logger.warn("HTTP连接池清理异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    @Override
    public void close() throws IOException {
        logger.info("正在关闭HTTP连接池管理器...");
        
        try {
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("关闭HTTP客户端异常", e);
        }
        
        try {
            connectionManager.close();
        } catch (IOException e) {
            logger.warn("关闭连接管理器异常", e);
        }
        
        logger.info("HTTP连接池管理器已关闭");
    }
    
    /**
     * 注册JVM关闭钩子
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (instance != null) {
                    instance.close();
                }
            } catch (IOException e) {
                System.err.println("关闭HTTP连接池管理器失败: " + e.getMessage());
            }
        }));
    }
}