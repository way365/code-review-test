package com.notification.mq.entity;

import java.time.LocalDateTime;

public class MessageEntity {
    private Long id;
    private String messageId;
    private String messageType;
    private String destination;
    private String content;
    private Integer status; // 0-待发送, 1-已发送, 2-发送失败, 3-已死亡
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String errorMessage;

    public MessageEntity() {}

    public MessageEntity(String messageId, String messageType, String destination, String content) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.destination = destination;
        this.content = content;
        this.status = 0;
        this.retryCount = 0;
        this.maxRetry = 3;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.nextRetryTime = LocalDateTime.now().plusSeconds(30);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetry() { return maxRetry; }
    public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }
    
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "MessageEntity{" +
                "id=" + id +
                ", messageId='" + messageId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", destination='" + destination + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", nextRetryTime=" + nextRetryTime +
                '}';
    }
}