package com.smartbridge.core.monitoring;

import com.smartbridge.core.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alerting service for exceptional conditions and security monitoring.
 * Provides administrator notifications for critical system events.
 * 
 * Requirements: 5.5, 8.6
 */
@Service
public class AlertingService {
    private static final Logger logger = LoggerFactory.getLogger(AlertingService.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_ALERT");
    private static final Logger systemLogger = LoggerFactory.getLogger("SYSTEM_ALERT");

    private final MetricsService metricsService;
    private final AuditService auditService;
    
    // Track alert frequencies to prevent alert storms
    private final Map<String, AlertThrottle> alertThrottles = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> securityViolationCounts = new ConcurrentHashMap<>();

    public AlertingService(MetricsService metricsService, AuditService auditService) {
        this.metricsService = metricsService;
        this.auditService = auditService;
    }

    /**
     * Alert on transformation failures exceeding threshold.
     */
    public void alertTransformationFailure(String sourceSystem, String targetSystem, 
                                          String errorMessage, String sourceId) {
        String alertKey = String.format("transformation_failure_%s_%s", sourceSystem, targetSystem);
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "ALERT: Transformation failure from %s to %s. SourceId=%s, Error=%s",
                sourceSystem, targetSystem, sourceId, errorMessage
            );
            
            systemLogger.error(message);
            metricsService.recordSecurityEvent("transformation_failure", "medium");
            auditService.logTransformation("SYSTEM", sourceSystem, targetSystem, 
                "TRANSFORM_FAILED", sourceId, null, false, errorMessage);
            
            notifyAdministrators("Transformation Failure", message, AlertSeverity.MEDIUM);
        }
    }

    /**
     * Alert on FHIR server connectivity issues.
     */
    public void alertFHIRServerUnavailable(String fhirServer, String errorMessage) {
        String alertKey = "fhir_server_unavailable";
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "CRITICAL: FHIR server unavailable. Server=%s, Error=%s",
                fhirServer, errorMessage
            );
            
            systemLogger.error(message);
            metricsService.recordSecurityEvent("fhir_unavailable", "high");
            
            notifyAdministrators("FHIR Server Unavailable", message, AlertSeverity.HIGH);
        }
    }

    /**
     * Alert on UCS API connectivity issues.
     */
    public void alertUCSApiUnavailable(String endpoint, String errorMessage) {
        String alertKey = "ucs_api_unavailable";
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "CRITICAL: UCS API unavailable. Endpoint=%s, Error=%s",
                endpoint, errorMessage
            );
            
            systemLogger.error(message);
            metricsService.recordSecurityEvent("ucs_unavailable", "high");
            
            notifyAdministrators("UCS API Unavailable", message, AlertSeverity.HIGH);
        }
    }

    /**
     * Alert on circuit breaker opening.
     */
    public void alertCircuitBreakerOpen(String serviceName, int failureCount) {
        String alertKey = String.format("circuit_breaker_open_%s", serviceName);
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "WARNING: Circuit breaker opened for service %s after %d failures",
                serviceName, failureCount
            );
            
            systemLogger.warn(message);
            metricsService.recordCircuitBreakerState(serviceName, "open");
            
            notifyAdministrators("Circuit Breaker Opened", message, AlertSeverity.MEDIUM);
        }
    }

    /**
     * Alert on message queue overflow.
     */
    public void alertQueueOverflow(String queueName, int currentSize, int maxSize) {
        String alertKey = String.format("queue_overflow_%s", queueName);
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "WARNING: Queue %s approaching capacity. Current=%d, Max=%d",
                queueName, currentSize, maxSize
            );
            
            systemLogger.warn(message);
            metricsService.recordQueueOperation(queueName, "overflow_warning");
            
            notifyAdministrators("Queue Overflow Warning", message, AlertSeverity.MEDIUM);
        }
    }

    /**
     * Alert on dead letter queue messages.
     */
    public void alertDeadLetterQueue(String queueName, String messageId, String errorMessage) {
        String alertKey = "dead_letter_queue";
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "ERROR: Message moved to dead letter queue. Queue=%s, MessageId=%s, Error=%s",
                queueName, messageId, errorMessage
            );
            
            systemLogger.error(message);
            metricsService.recordQueueOperation("dead-letter", "message_added");
            
            notifyAdministrators("Dead Letter Queue Alert", message, AlertSeverity.HIGH);
        }
    }

    /**
     * Alert on performance degradation.
     */
    public void alertPerformanceDegradation(String operation, long actualDurationMs, 
                                           long thresholdMs) {
        String alertKey = String.format("performance_degradation_%s", operation);
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "WARNING: Performance degradation detected. Operation=%s, Duration=%dms, Threshold=%dms",
                operation, actualDurationMs, thresholdMs
            );
            
            systemLogger.warn(message);
            
            notifyAdministrators("Performance Degradation", message, AlertSeverity.MEDIUM);
        }
    }

    /**
     * Detect and alert on authentication failures.
     */
    public void detectAuthenticationFailure(String userId, String userName, String sourceIp, 
                                           String authMethod, String errorMessage) {
        String violationKey = String.format("auth_failure_%s_%s", userId, sourceIp);
        int failureCount = securityViolationCounts
            .computeIfAbsent(violationKey, k -> new AtomicInteger(0))
            .incrementAndGet();
        
        auditService.logAuthenticationAttempt(userId, userName, authMethod, sourceIp, 
            false, errorMessage);
        
        if (failureCount >= 3) {
            String message = String.format(
                "SECURITY ALERT: Multiple authentication failures. UserId=%s, UserName=%s, IP=%s, Count=%d, Method=%s",
                userId, userName, sourceIp, failureCount, authMethod
            );
            
            securityLogger.error(message);
            metricsService.recordSecurityEvent("authentication_failure", "high");
            
            notifyAdministrators("Authentication Failure Alert", message, AlertSeverity.HIGH);
            
            // Consider blocking after threshold
            if (failureCount >= 5) {
                blockSuspiciousActivity(userId, sourceIp, "Excessive authentication failures");
            }
        }
    }

    /**
     * Detect and alert on authorization violations.
     */
    public void detectAuthorizationViolation(String userId, String userName, String resource, 
                                            String action, String sourceIp, String details) {
        String violationKey = String.format("authz_violation_%s_%s", userId, resource);
        int violationCount = securityViolationCounts
            .computeIfAbsent(violationKey, k -> new AtomicInteger(0))
            .incrementAndGet();
        
        auditService.logAuthorizationDecision(userId, userName, resource, action, 
            false, sourceIp, details);
        
        String message = String.format(
            "SECURITY ALERT: Authorization violation. UserId=%s, UserName=%s, Resource=%s, Action=%s, IP=%s, Count=%d",
            userId, userName, resource, action, sourceIp, violationCount
        );
        
        securityLogger.warn(message);
        metricsService.recordSecurityEvent("authorization_violation", "medium");
        
        if (violationCount >= 3) {
            notifyAdministrators("Authorization Violation Alert", message, AlertSeverity.MEDIUM);
        }
    }

    /**
     * Detect and alert on suspicious data access patterns.
     */
    public void detectSuspiciousDataAccess(String userId, String userName, String patientId, 
                                          String dataType, String sourceIp, String details) {
        String violationKey = String.format("suspicious_access_%s", userId);
        int accessCount = securityViolationCounts
            .computeIfAbsent(violationKey, k -> new AtomicInteger(0))
            .incrementAndGet();
        
        auditService.logPatientDataAccess(userId, userName, patientId, dataType, 
            "READ", "SUSPICIOUS", sourceIp, details);
        
        if (accessCount >= 10) {
            String message = String.format(
                "SECURITY ALERT: Suspicious data access pattern. UserId=%s, UserName=%s, PatientId=%s, IP=%s, AccessCount=%d",
                userId, userName, patientId, sourceIp, accessCount
            );
            
            securityLogger.error(message);
            metricsService.recordSecurityEvent("suspicious_access", "high");
            
            notifyAdministrators("Suspicious Data Access Alert", message, AlertSeverity.HIGH);
        }
    }

    /**
     * Detect and alert on encryption failures.
     */
    public void detectEncryptionFailure(String operation, String dataType, String errorMessage) {
        String alertKey = "encryption_failure";
        
        if (shouldAlert(alertKey)) {
            String message = String.format(
                "CRITICAL SECURITY ALERT: Encryption failure. Operation=%s, DataType=%s, Error=%s",
                operation, dataType, errorMessage
            );
            
            securityLogger.error(message);
            metricsService.recordSecurityEvent("encryption_failure", "critical");
            
            notifyAdministrators("Encryption Failure Alert", message, AlertSeverity.CRITICAL);
        }
    }

    /**
     * Detect and alert on audit trail tampering.
     */
    public void detectAuditTampering(long startSeqNum, long endSeqNum, String details) {
        String message = String.format(
            "CRITICAL SECURITY ALERT: Audit trail tampering detected. SeqRange=%d-%d, Details=%s",
            startSeqNum, endSeqNum, details
        );
        
        securityLogger.error(message);
        metricsService.recordSecurityEvent("audit_tampering", "critical");
        
        notifyAdministrators("Audit Tampering Alert", message, AlertSeverity.CRITICAL);
    }

    /**
     * Detect and alert on TLS/SSL violations.
     */
    public void detectTLSViolation(String sourceIp, String endpoint, String details) {
        String message = String.format(
            "SECURITY ALERT: TLS/SSL violation detected. IP=%s, Endpoint=%s, Details=%s",
            sourceIp, endpoint, details
        );
        
        securityLogger.error(message);
        metricsService.recordSecurityEvent("tls_violation", "high");
        
        notifyAdministrators("TLS Violation Alert", message, AlertSeverity.HIGH);
    }

    /**
     * Block suspicious activity by logging and alerting.
     */
    private void blockSuspiciousActivity(String userId, String sourceIp, String reason) {
        String message = String.format(
            "CRITICAL SECURITY ACTION: Blocking suspicious activity. UserId=%s, IP=%s, Reason=%s",
            userId, sourceIp, reason
        );
        
        securityLogger.error(message);
        metricsService.recordSecurityEvent("activity_blocked", "critical");
        
        auditService.logSecurityEvent("ACTIVITY_BLOCKED", userId, null, sourceIp, 
            true, reason);
        
        notifyAdministrators("Suspicious Activity Blocked", message, AlertSeverity.CRITICAL);
    }

    /**
     * Check if alert should be sent based on throttling rules.
     */
    private boolean shouldAlert(String alertKey) {
        AlertThrottle throttle = alertThrottles.computeIfAbsent(alertKey, 
            k -> new AlertThrottle());
        return throttle.shouldAlert();
    }

    /**
     * Notify administrators through configured channels.
     */
    private void notifyAdministrators(String subject, String message, AlertSeverity severity) {
        // Log to appropriate logger based on severity
        switch (severity) {
            case CRITICAL:
                logger.error("[ADMIN NOTIFICATION] {}: {}", subject, message);
                break;
            case HIGH:
                logger.error("[ADMIN NOTIFICATION] {}: {}", subject, message);
                break;
            case MEDIUM:
                logger.warn("[ADMIN NOTIFICATION] {}: {}", subject, message);
                break;
            case LOW:
                logger.info("[ADMIN NOTIFICATION] {}: {}", subject, message);
                break;
        }
        
        // TODO: Integrate with actual notification system (email, SMS, Slack, PagerDuty, etc.)
        // For now, we log to a dedicated admin notification logger
        Logger adminLogger = LoggerFactory.getLogger("ADMIN_NOTIFICATION");
        adminLogger.error("Severity={} | Subject={} | Message={}", severity, subject, message);
    }

    /**
     * Reset security violation counts (for testing or after incident resolution).
     */
    public void resetSecurityViolationCounts() {
        securityViolationCounts.clear();
    }

    /**
     * Get current security violation count for a key.
     */
    public int getSecurityViolationCount(String violationKey) {
        AtomicInteger count = securityViolationCounts.get(violationKey);
        return count != null ? count.get() : 0;
    }

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Alert throttling to prevent alert storms.
     */
    private static class AlertThrottle {
        private static final long THROTTLE_WINDOW_MS = 60000; // 1 minute
        private static final int MAX_ALERTS_PER_WINDOW = 5;
        
        private Instant windowStart = Instant.now();
        private int alertCount = 0;

        public synchronized boolean shouldAlert() {
            Instant now = Instant.now();
            
            // Reset window if expired
            if (now.toEpochMilli() - windowStart.toEpochMilli() > THROTTLE_WINDOW_MS) {
                windowStart = now;
                alertCount = 0;
            }
            
            // Check if we're within limits
            if (alertCount < MAX_ALERTS_PER_WINDOW) {
                alertCount++;
                return true;
            }
            
            return false;
        }
    }
}
