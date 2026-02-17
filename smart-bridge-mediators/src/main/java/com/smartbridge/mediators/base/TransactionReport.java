package com.smartbridge.mediators.base;

import java.time.Instant;
import java.util.Map;

/**
 * Transaction report for OpenHIM Core.
 * Contains transaction details for monitoring and audit purposes.
 */
public class TransactionReport {

    private final String transactionId;
    private final String mediatorName;
    private final String status;
    private final int httpStatusCode;
    private final Instant startTime;
    private final Instant endTime;
    private final Map<String, String> request;
    private final Map<String, String> response;
    private final String error;

    private TransactionReport(Builder builder) {
        this.transactionId = builder.transactionId;
        this.mediatorName = builder.mediatorName;
        this.status = builder.status;
        this.httpStatusCode = builder.httpStatusCode;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.request = builder.request;
        this.response = builder.response;
        this.error = builder.error;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMediatorName() {
        return mediatorName;
    }

    public String getStatus() {
        return status;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Map<String, String> getRequest() {
        return request;
    }

    public Map<String, String> getResponse() {
        return response;
    }

    public String getError() {
        return error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String transactionId;
        private String mediatorName;
        private String status;
        private int httpStatusCode = 200;
        private Instant startTime;
        private Instant endTime;
        private Map<String, String> request;
        private Map<String, String> response;
        private String error;

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder mediatorName(String mediatorName) {
            this.mediatorName = mediatorName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder httpStatusCode(int httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder request(Map<String, String> request) {
            this.request = request;
            return this;
        }

        public Builder response(Map<String, String> response) {
            this.response = response;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public TransactionReport build() {
            return new TransactionReport(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "TransactionReport{id='%s', mediator='%s', status='%s', httpStatus=%d, duration=%dms}",
            transactionId, mediatorName, status, httpStatusCode,
            (endTime != null && startTime != null) ? 
                endTime.toEpochMilli() - startTime.toEpochMilli() : 0
        );
    }
}
