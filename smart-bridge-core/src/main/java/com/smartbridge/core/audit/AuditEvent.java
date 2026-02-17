package com.smartbridge.core.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an audit event in the Smart Bridge system.
 * Provides structured representation of audit log entries.
 */
public class AuditEvent {
    
    private final long sequenceNumber;
    private final Instant timestamp;
    private final String eventType;
    private final String userId;
    private final String userName;
    private final String resourceId;
    private final String dataType;
    private final String operation;
    private final String sourceSystem;
    private final String sourceIp;
    private final boolean success;
    private final String details;
    private final String hash;
    private final String previousHash;

    private AuditEvent(Builder builder) {
        this.sequenceNumber = builder.sequenceNumber;
        this.timestamp = builder.timestamp;
        this.eventType = builder.eventType;
        this.userId = builder.userId;
        this.userName = builder.userName;
        this.resourceId = builder.resourceId;
        this.dataType = builder.dataType;
        this.operation = builder.operation;
        this.sourceSystem = builder.sourceSystem;
        this.sourceIp = builder.sourceIp;
        this.success = builder.success;
        this.details = builder.details;
        this.hash = builder.hash;
        this.previousHash = builder.previousHash;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDataType() {
        return dataType;
    }

    public String getOperation() {
        return operation;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getDetails() {
        return details;
    }

    public String getHash() {
        return hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return sequenceNumber == that.sequenceNumber &&
               success == that.success &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(eventType, that.eventType) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, timestamp, eventType, userId, hash);
    }

    @Override
    public String toString() {
        return String.format(
            "AuditEvent{seqNum=%d, timestamp=%s, eventType=%s, userId=%s, operation=%s, success=%s, hash=%s}",
            sequenceNumber, timestamp, eventType, userId, operation, success, hash
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long sequenceNumber;
        private Instant timestamp = Instant.now();
        private String eventType;
        private String userId;
        private String userName;
        private String resourceId;
        private String dataType;
        private String operation;
        private String sourceSystem;
        private String sourceIp;
        private boolean success = true;
        private String details;
        private String hash;
        private String previousHash;

        public Builder sequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder dataType(String dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder sourceIp(String sourceIp) {
            this.sourceIp = sourceIp;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder hash(String hash) {
            this.hash = hash;
            return this;
        }

        public Builder previousHash(String previousHash) {
            this.previousHash = previousHash;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
