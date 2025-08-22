package com.notification;

import com.notification.config.NotificationConfig;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * NotificationConfig单元测试
 */
public class NotificationConfigTest {
    
    @Test
    public void testGetInstance() {
        // 测试单例模式
        NotificationConfig config1 = NotificationConfig.getInstance();
        NotificationConfig config2 = NotificationConfig.getInstance();
        
        assertNotNull(config1);
        assertNotNull(config2);
        assertSame(config1, config2);
    }
    
    @Test
    public void testGetProperty() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试获取属性（使用默认值）
        String value = config.getProperty("nonexistent.key", "default");
        assertEquals("default", value);
    }
    
    @Test
    public void testGetIntProperty() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试获取整数属性
        int timeout = config.getNotificationTimeout();
        assertTrue(timeout > 0);
        
        // 测试默认值
        int defaultValue = config.getIntProperty("nonexistent.key", 100);
        assertEquals(100, defaultValue);
    }
    
    @Test
    public void testGetLongProperty() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试获取长整数属性
        long delay = config.getNotificationRetryDelay();
        assertTrue(delay >= 0);
        
        // 测试默认值
        long defaultValue = config.getLongProperty("nonexistent.key", 1000L);
        assertEquals(1000L, defaultValue);
    }
    
    @Test
    public void testGetBooleanProperty() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试默认值
        boolean defaultValue = config.getBooleanProperty("nonexistent.key", true);
        assertTrue(defaultValue);
        
        defaultValue = config.getBooleanProperty("nonexistent.key", false);
        assertFalse(defaultValue);
    }
    
    @Test
    public void testDingTalkConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试钉钉配置（可能为空字符串）
        String webhook = config.getDingTalkWebhook();
        assertNotNull(webhook);
        
        String secret = config.getDingTalkSecret();
        assertNotNull(secret);
    }
    
    @Test
    public void testFeishuConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试飞书配置
        String webhook = config.getFeishuWebhook();
        assertNotNull(webhook);
    }
    
    @Test
    public void testWeChatConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试微信配置
        String appId = config.getWeChatAppId();
        assertNotNull(appId);
        
        String appSecret = config.getWeChatAppSecret();
        assertNotNull(appSecret);
        
        String templateId = config.getWeChatTemplateId();
        assertNotNull(templateId);
        
        String openId = config.getWeChatOpenId();
        assertNotNull(openId);
    }
    
    @Test
    public void testNotificationConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试通知配置
        int timeout = config.getNotificationTimeout();
        assertTrue(timeout > 0);
        
        int retryCount = config.getNotificationRetryCount();
        assertTrue(retryCount >= 0);
        
        long retryDelay = config.getNotificationRetryDelay();
        assertTrue(retryDelay >= 0);
    }
    
    @Test
    public void testConnectionPoolConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试连接池配置
        int maxTotal = config.getConnectionPoolMaxTotal();
        assertTrue(maxTotal > 0);
        
        int maxPerRoute = config.getConnectionPoolMaxPerRoute();
        assertTrue(maxPerRoute > 0);
        
        int connectionTimeout = config.getConnectionPoolConnectionTimeout();
        assertTrue(connectionTimeout > 0);
        
        int socketTimeout = config.getConnectionPoolSocketTimeout();
        assertTrue(socketTimeout > 0);
    }
    
    @Test
    public void testRateLimitConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试限流配置
        int permits = config.getRateLimitPermits();
        assertTrue(permits > 0);
        
        long period = config.getRateLimitPeriod();
        assertTrue(period > 0);
    }
    
    @Test
    public void testAsyncConfig() {
        NotificationConfig config = NotificationConfig.getInstance();
        
        // 测试异步配置
        int threadPoolSize = config.getAsyncThreadPoolSize();
        assertTrue(threadPoolSize > 0);
        
        int queueSize = config.getAsyncQueueSize();
        assertTrue(queueSize > 0);
    }
}