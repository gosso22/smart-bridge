package com.smartbridge.core.interfaces;

/**
 * Exception thrown when data transformation between UCS and FHIR formats fails.
 * Provides detailed error information for debugging and error handling.
 */
public class TransformationException extends Exception {

    private final String sourceSystem;
    private final String targetSystem;
    private final String errorCode;

    public TransformationException(String message) {
        super(message);
        this.sourceSystem = null;
        this.targetSystem = null;
        this.errorCode = null;
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
        this.sourceSystem = null;
        this.targetSystem = null;
        this.errorCode = null;
    }

    public TransformationException(String message, String sourceSystem, String targetSystem, String errorCode) {
        super(message);
        this.sourceSystem = sourceSystem;
        this.targetSystem = targetSystem;
        this.errorCode = errorCode;
    }

    public TransformationException(String message, Throwable cause, String sourceSystem, String targetSystem, String errorCode) {
        super(message, cause);
        this.sourceSystem = sourceSystem;
        this.targetSystem = targetSystem;
        this.errorCode = errorCode;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransformationException: ").append(getMessage());
        
        if (sourceSystem != null && targetSystem != null) {
            sb.append(" [").append(sourceSystem).append(" -> ").append(targetSystem).append("]");
        }
        
        if (errorCode != null) {
            sb.append(" (Error Code: ").append(errorCode).append(")");
        }
        
        return sb.toString();
    }
}