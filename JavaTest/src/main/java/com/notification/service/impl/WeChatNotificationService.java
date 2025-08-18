package com.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.service.NotificationService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号通知服务实现
 */
public class WeChatNotificationService implements NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(WeChatNotificationService.class);
    
    private final String appId;
    private final String appSecret;
    private final String templateId;
    private final String openId;
    private final ObjectMapper objectMapper;
    
    public WeChatNotificationService(String appId, String appSecret, String templateId, String openId) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.templateId = templateId;
        this.openId = openId;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public boolean sendNotification(String message, String title) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return false;
            }
            
            return sendTemplateMessage(accessToken, message, title);
        } catch (Exception e) {
            logger.error("发送微信公众号消息异常", e);
            return false;
        }
    }
    
    @Override
    public boolean sendTaskCompletionNotification(String taskName, String status, long duration) {
        String message = String.format(
            "任务名称：%s\n" +
            "执行状态：%s\n" +
            "执行时长：%d 毫秒\n" +
            "完成时间：%s",
            taskName, status, duration, new java.util.Date()
        );
        return sendNotification(message, "任务完成通知");
    }
    
    @Override
    public boolean sendErrorNotification(String taskName, String errorMessage) {
        String message = String.format(
            "任务名称：%s\n" +
            "错误信息：%s\n" +
            "发生时间：%s",
            taskName, errorMessage, new java.util.Date()
        );
        return sendNotification(message, "任务异常通知");
    }
    
    /**
     * 获取微信公众号访问令牌
     */
    private String getAccessToken() {
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret
        );
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    InputStream inputStream = response.getEntity().getContent();
                    byte[] bytes = new byte[inputStream.available()];
                    inputStream.read(bytes);
                    String responseBody = new String(bytes, StandardCharsets.UTF_8);
                    Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                    
                    if (result.containsKey("access_token")) {
                        return (String) result.get("access_token");
                    } else {
                        logger.error("获取access_token失败: {}", result);
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("获取access_token异常", e);
        }
        return null;
    }
    
    /**
     * 发送模板消息
     */
    private boolean sendTemplateMessage(String accessToken, String message, String title) {
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s",
            accessToken
        );
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("touser", openId);
            requestBody.put("template_id", templateId);
            requestBody.put("url", "http://www.example.com");
            
            Map<String, Object> data = new HashMap<>();
            
            Map<String, String> titleData = new HashMap<>();
            titleData.put("value", title);
            titleData.put("color", "#173177");
            data.put("title", titleData);
            
            Map<String, String> messageData = new HashMap<>();
            messageData.put("value", message);
            messageData.put("color", "#173177");
            data.put("content", messageData);
            
            Map<String, String> timeData = new HashMap<>();
            timeData.put("value", new java.util.Date().toString());
            timeData.put("color", "#173177");
            data.put("time", timeData);
            
            requestBody.put("data", data);
            
            String jsonContent = objectMapper.writeValueAsString(requestBody);
            StringEntity entity = new StringEntity(jsonContent, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    InputStream inputStream = response.getEntity().getContent();
                    byte[] bytes = new byte[inputStream.available()];
                    inputStream.read(bytes);
                    String responseBody = new String(bytes, StandardCharsets.UTF_8);
                    Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                    
                    if ("0".equals(String.valueOf(result.get("errcode")))) {
                        logger.info("微信公众号消息发送成功");
                        return true;
                    } else {
                        logger.error("微信公众号消息发送失败: {}", result);
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("发送微信公众号消息异常", e);
        }
        return false;
    }
}