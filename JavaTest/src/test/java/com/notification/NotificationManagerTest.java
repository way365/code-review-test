package com.notification;

import com.notification.config.NotificationConfig;
import com.notification.manager.NotificationManager;
import com.notification.service.NotificationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * NotificationManager单元测试
 */
public class NotificationManagerTest {
    
    private NotificationManager notificationManager;
    
    @Mock
    private NotificationService mockService;
    
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        notificationManager = new NotificationManager();
    }
    
    @After
    public void tearDown() {
        if (notificationManager != null) {
            notificationManager.shutdown();
        }
    }
    
    @Test
    public void testAddService() {
        // 添加服务
        notificationManager.addService("test", mockService);
        
        // 验证服务已添加
        assertNotNull(notificationManager.getService("test"));
        assertEquals(mockService.getClass(), 
                   notificationManager.getService("test").getClass().getSuperclass().getSuperclass().getSuperclass());
    }
    
    @Test
    public void testSendToAll() {
        // 设置mock行为
        when(mockService.sendNotification(anyString(), anyString())).thenReturn(true);
        
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 发送通知
        Map<String, Boolean> results = notificationManager.sendToAll("测试消息", "测试标题");
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.containsKey("test"));
        assertTrue(results.get("test"));
        
        // 验证mock调用
        verify(mockService, times(1)).sendNotification("测试消息", "测试标题");
    }
    
    @Test
    public void testSendTaskCompletionToAll() {
        // 设置mock行为
        when(mockService.sendTaskCompletionNotification(anyString(), anyString(), anyLong())).thenReturn(true);
        
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 发送任务完成通知
        Map<String, Boolean> results = notificationManager.sendTaskCompletionToAll("测试任务", "成功", 1000L);
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.containsKey("test"));
        assertTrue(results.get("test"));
        
        // 验证mock调用
        verify(mockService, times(1)).sendTaskCompletionNotification("测试任务", "成功", 1000L);
    }
    
    @Test
    public void testSendErrorToAll() {
        // 设置mock行为
        when(mockService.sendErrorNotification(anyString(), anyString())).thenReturn(true);
        
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 发送错误通知
        Map<String, Boolean> results = notificationManager.sendErrorToAll("测试任务", "测试错误");
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.containsKey("test"));
        assertTrue(results.get("test"));
        
        // 验证mock调用
        verify(mockService, times(1)).sendErrorNotification("测试任务", "测试错误");
    }
    
    @Test
    public void testSendToAllWithException() {
        // 设置mock抛出异常
        when(mockService.sendNotification(anyString(), anyString())).thenThrow(new RuntimeException("测试异常"));
        
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 发送通知
        Map<String, Boolean> results = notificationManager.sendToAll("测试消息", "测试标题");
        
        // 验证结果（异常应该被处理，返回false）
        assertNotNull(results);
        assertTrue(results.containsKey("test"));
        assertFalse(results.get("test"));
    }
    
    @Test
    public void testGetServicesStatus() {
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 获取服务状态
        Map<String, String> status = notificationManager.getServicesStatus();
        
        // 验证状态
        assertNotNull(status);
        assertTrue(status.containsKey("test"));
    }
    
    @Test
    public void testTestAllConnections() {
        // 设置mock行为
        when(mockService.sendNotification(anyString(), anyString())).thenReturn(true);
        
        // 添加服务
        notificationManager.addService("test", mockService, false, false, false);
        
        // 测试连接
        Map<String, Boolean> results = notificationManager.testAllConnections();
        
        // 验证结果
        assertNotNull(results);
        assertTrue(results.containsKey("test"));
        assertTrue(results.get("test"));
    }
    
    @Test
    public void testAsyncManager() {
        // 验证异步管理器不为空
        assertNotNull(notificationManager.getAsyncManager());
    }
}