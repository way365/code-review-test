package com.notification.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 通知配置管理类
 * 负责从配置文件中读取和管理通知相关配置
 */
public class NotificationConfig {
    
    private static final String CONFIG_FILE = "config.properties";
    private static NotificationConfig instance;
    private final Properties properties;
    
    private NotificationConfig() {
        properties = new Properties();
        loadConfiguration();
    }
    
    /**
     * 获取配置实例（单例模式）
     * @return 配置实例
     */
    public static synchronized NotificationConfig getInstance() {
        if (instance == null) {
            instance = new NotificationConfig();
        }
        return instance;
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfiguration() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("无法找到配置文件: " + CONFIG_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败: " + CONFIG_FILE, e);
        }
    }
    
    /**
     * 获取配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * 获取整数配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取长整数配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取布尔配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
    
    // 钉钉配置
    public String getDingTalkWebhook() {
        return getProperty("dingtalk.webhook", "");
    }
    
    public String getDingTalkSecret() {
        return getProperty("dingtalk.secret", "");
    }
    
    // 飞书配置
    public String getFeishuWebhook() {
        return getProperty("feishu.webhook", "");
    }
    
    // 微信配置
    public String getWeChatAppId() {
        return getProperty("wechat.appid", "");
    }
    
    public String getWeChatAppSecret() {
        return getProperty("wechat.appsecret", "");
    }
    
    public String getWeChatTemplateId() {
        return getProperty("wechat.templateid", "");
    }
    
    public String getWeChatOpenId() {
        return getProperty("wechat.openid", "");
    }
    
    // 通知通用配置
    public int getNotificationTimeout() {
        return getIntProperty("notification.timeout", 5000);
    }
    
    public int getNotificationRetryCount() {
        return getIntProperty("notification.retry.count", 3);
    }
    
    public long getNotificationRetryDelay() {
        return getLongProperty("notification.retry.delay", 1000);
    }
    
    // 连接池配置
    public int getConnectionPoolMaxTotal() {
        return getIntProperty("notification.pool.max.total", 20);
    }
    
    public int getConnectionPoolMaxPerRoute() {
        return getIntProperty("notification.pool.max.per.route", 5);
    }
    
    public int getConnectionPoolConnectionTimeout() {
        return getIntProperty("notification.pool.connection.timeout", 3000);
    }
    
    public int getConnectionPoolSocketTimeout() {
        return getIntProperty("notification.pool.socket.timeout", 5000);
    }
    
    // 限流配置
    public int getRateLimitPermits() {
        return getIntProperty("notification.rate.limit.permits", 10);
    }
    
    public long getRateLimitPeriod() {
        return getLongProperty("notification.rate.limit.period", 60000); // 1分钟
    }
    
    // 异步配置
    public int getAsyncThreadPoolSize() {
        return getIntProperty("notification.async.thread.pool.size", 5);
    }
    
    public int getAsyncQueueSize() {
        return getIntProperty("notification.async.queue.size", 100);
    }
}