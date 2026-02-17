package com.smartbridge.mediators.flow;

/**
 * Exception thrown during reverse sync flow operations.
 * Includes error code for categorizing failures.
 */
public class ReverseSyncFlowException extends RuntimeException {

    private final String errorCode;

    public ReverseSyncFlowException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReverseSyncFlowException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
