package com.smartbridge.core.flow;

/**
 * Exception thrown during ingestion flow operations.
 * Includes error code for categorizing failures.
 */
public class IngestionFlowException extends RuntimeException {

    private final String errorCode;

    public IngestionFlowException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public IngestionFlowException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
