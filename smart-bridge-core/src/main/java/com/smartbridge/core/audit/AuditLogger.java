package com.smartbridge.core.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Audit logging service for Smart Bridge operations.
 * Provides comprehensive audit trails for compliance and debugging purposes.
 * Logs all data transformations, system interactions, and security events.
 */
@Component
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("com.smartbridge.audit");
    private static final Logger securityLog = LoggerFactory.getLogger("com.smartbridge.security");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired(required = false)
    private Counter auditLogCounter;

    @Autowired(required = false)
    private Counter securityEventCounter;

    /**
     * Log data transformation operations.
     */
    public void logTransformation(String sourceSystem, String targetSystem, String operation, 
                                String sourceId, String targetId, boolean success, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "TRANSFORMATION | %s | %s->%s | %s | SourceId=%s | TargetId=%s | Success=%s | Details=%s",
            timestamp, sourceSystem, targetSystem, operation, sourceId, targetId, success, details
        );
        
        auditLog.info(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }

    /**
     * Log mediator operations.
     */
    public void logMediatorOperation(String mediatorName, String operation, String requestId, 
                                   boolean success, long durationMs, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "MEDIATOR | %s | %s | %s | RequestId=%s | Success=%s | Duration=%dms | Details=%s",
            timestamp, mediatorName, operation, requestId, success, durationMs, details
        );
        
        auditLog.info(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }

    /**
     * Log FHIR resource operations.
     */
    public void logFHIROperation(String operation, String resourceType, String resourceId, 
                               String fhirServer, boolean success, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "FHIR | %s | %s | %s | ResourceId=%s | Server=%s | Success=%s | Details=%s",
            timestamp, operation, resourceType, resourceId, fhirServer, success, details
        );
        
        auditLog.info(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }

    /**
     * Log authentication and authorization events.
     */
    public void logSecurityEvent(String eventType, String userId, String sourceIp, 
                                boolean success, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "SECURITY | %s | %s | UserId=%s | SourceIP=%s | Success=%s | Details=%s",
            timestamp, eventType, userId, sourceIp, success, details
        );
        
        securityLog.info(logEntry);
        
        if (securityEventCounter != null) {
            securityEventCounter.increment();
        }
    }

    /**
     * Log data access events for patient data.
     */
    public void logDataAccess(String userId, String patientId, String dataType, 
                            String operation, String sourceSystem, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "DATA_ACCESS | %s | UserId=%s | PatientId=%s | DataType=%s | Operation=%s | Source=%s | Details=%s",
            timestamp, userId, patientId, dataType, operation, sourceSystem, details
        );
        
        auditLog.info(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }

    /**
     * Log system errors and exceptions.
     */
    public void logError(String component, String operation, String errorCode, 
                        String errorMessage, Map<String, String> context) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder contextStr = new StringBuilder();
        
        if (context != null && !context.isEmpty()) {
            context.forEach((key, value) -> contextStr.append(key).append("=").append(value).append(" "));
        }
        
        String logEntry = String.format(
            "ERROR | %s | %s | %s | ErrorCode=%s | Message=%s | Context=%s",
            timestamp, component, operation, errorCode, errorMessage, contextStr.toString().trim()
        );
        
        auditLog.error(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }

    /**
     * Log performance metrics and alerts.
     */
    public void logPerformanceAlert(String component, String metric, double value, 
                                  double threshold, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format(
            "PERFORMANCE_ALERT | %s | %s | Metric=%s | Value=%.2f | Threshold=%.2f | Details=%s",
            timestamp, component, metric, value, threshold, details
        );
        
        auditLog.warn(logEntry);
        
        if (auditLogCounter != null) {
            auditLogCounter.increment();
        }
    }
}