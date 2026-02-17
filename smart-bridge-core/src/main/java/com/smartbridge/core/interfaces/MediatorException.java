package com.smartbridge.core.interfaces;

/**
 * Exception thrown when mediator operations fail.
 * Provides detailed error information for OpenHIM integration and debugging.
 */
public class MediatorException extends Exception {

    private final String mediatorName;
    private final String operation;
    private final int httpStatusCode;

    public MediatorException(String message) {
        super(message);
        this.mediatorName = null;
        this.operation = null;
        this.httpStatusCode = 500;
    }

    public MediatorException(String message, Throwable cause) {
        super(message, cause);
        this.mediatorName = null;
        this.operation = null;
        this.httpStatusCode = 500;
    }

    public MediatorException(String message, String mediatorName, String operation, int httpStatusCode) {
        super(message);
        this.mediatorName = mediatorName;
        this.operation = operation;
        this.httpStatusCode = httpStatusCode;
    }

    public MediatorException(String message, Throwable cause, String mediatorName, String operation, int httpStatusCode) {
        super(message, cause);
        this.mediatorName = mediatorName;
        this.operation = operation;
        this.httpStatusCode = httpStatusCode;
    }

    public String getMediatorName() {
        return mediatorName;
    }

    public String getOperation() {
        return operation;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MediatorException: ").append(getMessage());
        
        if (mediatorName != null) {
            sb.append(" [Mediator: ").append(mediatorName);
            if (operation != null) {
                sb.append(", Operation: ").append(operation);
            }
            sb.append("]");
        }
        
        sb.append(" (HTTP Status: ").append(httpStatusCode).append(")");
        
        return sb.toString();
    }
}