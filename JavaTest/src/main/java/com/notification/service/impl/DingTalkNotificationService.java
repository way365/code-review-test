package com.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.service.NotificationService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉通知服务实现
 */
public class DingTalkNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(DingTalkNotificationService.class);
    
    private final String webhookUrl;
    private final String secret;
    private final ObjectMapper objectMapper;
    
    public DingTalkNotificationService(String webhookUrl, String secret) {
        this.webhookUrl = webhookUrl;
        this.secret = secret;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public boolean sendNotification(String message, String title) {
        Map<String, Object> content = new HashMap<>();
        content.put("msgtype", "markdown");
        
        Map<String, String> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", message);
        content.put("markdown", markdown);
        
        return sendRequest(content);
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration) {
        String message = String.format(
            "## 🎯 任务完成通知\n" +
            "**任务名称**: %s\n" +
            "**执行状态**: %s\n" +
            "**执行时长**: %d 毫秒\n" +
            "**完成时间**: %s",
            taskName, status, duration, new java.util.Date()
        );
        return sendNotification(message, "任务完成通知");
    }
    
    @Override
    public boolean sendErrorNotification(String taskName, String errorMessage) {
        String message = String.format(
            "## ❌ 任务异常通知\n" +
            "**任务名称**: %s\n" +
            "**错误信息**: %s\n" +
            "**发生时间**: %s",
            taskName, errorMessage, new java.util.Date()
        );
        return sendNotification(message, "任务异常通知");
    }
    
    private boolean sendRequest(Map<String, Object> content) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);
            httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            
            String jsonContent = objectMapper.writeValueAsString(content);
            StringEntity entity = new StringEntity(jsonContent, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    logger.info("钉钉消息发送成功");
                    return true;
                } else {
                    logger.error("钉钉消息发送失败，状态码: {}", statusCode);
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("发送钉钉消息异常", e);
            return false;
        }
    }
}