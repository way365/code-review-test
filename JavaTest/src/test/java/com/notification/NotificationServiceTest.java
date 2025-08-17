package com.notification;

import com.notification.service.NotificationService;
import com.notification.service.impl.DingTalkNotificationService;
import com.notification.service.impl.FeishuNotificationService;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * 通知服务测试类
 */
public class NotificationServiceTest {
    
    @Test
    public void testDingTalkServiceCreation() {
        NotificationService dingTalk = new DingTalkNotificationService(
            "https://oapi.dingtalk.com/robot/send", 
            "test-secret"
        );
        assertNotNull(dingTalk);
    }
    
    @Test
    public void testFeishuServiceCreation() {
        NotificationService feishu = new FeishuNotificationService(
            "https://open.feishu.cn/open-apis/bot/v2/hook/test"
        );
        assertNotNull(feishu);
    }
}