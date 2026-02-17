package com.smartbridge.core.queue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a message in the queue system.
 * Contains the payload, metadata, and retry information.
 */
public class QueueMessage {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("payload")
    private String payload;
    
    @JsonProperty("messageType")
    private String messageType;
    
    @JsonProperty("retryCount")
    private int retryCount;
    
    @JsonProperty("maxRetries")
    private int maxRetries;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("lastAttemptAt")
    private Instant lastAttemptAt;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    public QueueMessage() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.retryCount = 0;
        this.maxRetries = 5;
    }
    
    public QueueMessage(String payload, String messageType) {
        this();
        this.payload = payload;
        this.messageType = messageType;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getPayload() {
        return payload;
    }
    
    public void setPayload(String payload) {
        this.payload = payload;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }
    
    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastAttemptAt = Instant.now();
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries;
    }
    
    public long getRetryDelayMillis() {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s
        return (long) Math.pow(2, retryCount) * 1000;
    }
}
