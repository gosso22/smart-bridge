# Alerting and Security Monitoring

## Overview

The AlertingService provides comprehensive alerting and security monitoring capabilities for the Smart Bridge system. It detects exceptional conditions, security violations, and system anomalies, then notifies administrators through configured channels.

## Features

### System Alerts

- **Transformation Failures**: Alerts when data transformation operations fail
- **Service Unavailability**: Monitors FHIR server and UCS API connectivity
- **Circuit Breaker Events**: Tracks circuit breaker state changes
- **Queue Overflow**: Warns when message queues approach capacity
- **Dead Letter Queue**: Alerts on messages that exceed retry limits
- **Performance Degradation**: Detects operations exceeding time thresholds

### Security Monitoring

- **Authentication Failures**: Detects and tracks failed login attempts
- **Authorization Violations**: Monitors unauthorized access attempts
- **Suspicious Data Access**: Identifies unusual data access patterns
- **Encryption Failures**: Alerts on encryption/decryption errors
- **Audit Tampering**: Detects integrity violations in audit trails
- **TLS Violations**: Monitors insecure connection attempts

### Alert Severity Levels

- **CRITICAL**: Immediate action required (security breaches, data integrity issues)
- **HIGH**: Urgent attention needed (service outages, multiple failures)
- **MEDIUM**: Important but not urgent (performance issues, warnings)
- **LOW**: Informational alerts

## Usage

### Injecting the Service

```java
@Service
public class MyService {
    private final AlertingService alertingService;
    
    public MyService(AlertingService alertingService) {
        this.alertingService = alertingService;
    }
}
```

### System Alerts

```java
// Alert on transformation failure
alertingService.alertTransformationFailure(
    "ucs", "fhir", "Invalid data format", "client-123"
);

// Alert on service unavailability
alertingService.alertFHIRServerUnavailable(
    "http://fhir.example.com", "Connection timeout"
);

// Alert on circuit breaker opening
alertingService.alertCircuitBreakerOpen("fhir-service", 5);

// Alert on queue overflow
alertingService.alertQueueOverflow("transformation-queue", 950, 1000);

// Alert on dead letter queue
alertingService.alertDeadLetterQueue(
    "retry-queue", "msg-456", "Max retries exceeded"
);

// Alert on performance degradation
alertingService.alertPerformanceDegradation(
    "transformation", 8000, 5000
);
```

### Security Monitoring

```java
// Detect authentication failures
alertingService.detectAuthenticationFailure(
    "user123", "John Doe", "192.168.1.1", 
    "basic", "Invalid password"
);

// Detect authorization violations
alertingService.detectAuthorizationViolation(
    "user123", "John Doe", "/api/admin", 
    "DELETE", "192.168.1.1", "Insufficient permissions"
);

// Detect suspicious data access
alertingService.detectSuspiciousDataAccess(
    "user123", "John Doe", "patient-456", 
    "medical_records", "192.168.1.1", "Rapid access pattern"
);

// Detect encryption failures
alertingService.detectEncryptionFailure(
    "encrypt", "patient_data", "Key rotation failed"
);

// Detect audit tampering
alertingService.detectAuditTampering(100, 150, "Hash chain broken");

// Detect TLS violations
alertingService.detectTLSViolation(
    "192.168.1.1", "/api/patients", "Insecure connection attempt"
);
```

## Alert Throttling

The service implements alert throttling to prevent alert storms:

- **Throttle Window**: 1 minute
- **Max Alerts per Window**: 5 alerts per unique alert key

This ensures administrators aren't overwhelmed with duplicate alerts while still being notified of ongoing issues.

## Security Violation Tracking

The service tracks security violations and escalates responses:

### Authentication Failures
- **3 failures**: Alert administrators
- **5 failures**: Block suspicious activity

### Authorization Violations
- **3 violations**: Alert administrators

### Suspicious Data Access
- **10 accesses**: Alert administrators

## Integration with Other Services

### MetricsService Integration
All alerts are recorded as metrics for monitoring dashboards:
- `smart_bridge_security_events_total`
- `smart_bridge_circuit_breaker_state_changes_total`
- `smart_bridge_queue_operations_total`

### AuditService Integration
Security events are logged to the audit trail:
- Authentication attempts
- Authorization decisions
- Security violations
- Activity blocking

## Logging Configuration

Alerts are logged to multiple destinations:

### Security Alerts
- Logger: `SECURITY_ALERT`
- File: `logs/smart-bridge-security.log`
- Retention: 365 days

### System Alerts
- Logger: `SYSTEM_ALERT`
- File: `logs/smart-bridge.log`
- Retention: 30 days

### Admin Notifications
- Logger: `ADMIN_NOTIFICATION`
- Files: Both application and security logs
- Retention: 365 days (security), 30 days (application)

## Administrator Notification Channels

The service provides a notification framework that can be extended to support:

- **Email**: Send alerts via SMTP
- **SMS**: Send critical alerts via SMS gateway
- **Slack**: Post alerts to Slack channels
- **PagerDuty**: Create incidents for critical alerts
- **Webhook**: POST alerts to custom endpoints

### Extending Notification Channels

To add a notification channel, modify the `notifyAdministrators` method:

```java
private void notifyAdministrators(String subject, String message, AlertSeverity severity) {
    // Existing logging...
    
    // Add custom notification
    if (severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH) {
        emailService.sendAlert(subject, message);
        smsService.sendAlert(message);
        slackService.postAlert(subject, message, severity);
    }
}
```

## Testing

The service includes comprehensive unit tests covering:
- All alert types
- Security violation detection and escalation
- Alert throttling behavior
- Violation count tracking
- Integration with metrics and audit services

Run tests:
```bash
mvn test -Dtest=AlertingServiceTest
```

## Requirements Satisfied

- **Requirement 5.5**: Error handling with administrator alerts
- **Requirement 8.6**: Security violation detection and response

## Best Practices

1. **Use Appropriate Severity**: Choose severity levels that match the impact
2. **Provide Context**: Include relevant details in alert messages
3. **Monitor Alert Volume**: Review alert frequency to tune thresholds
4. **Test Notification Channels**: Regularly verify notification delivery
5. **Document Response Procedures**: Create runbooks for each alert type
6. **Review Security Violations**: Investigate all security alerts promptly
7. **Tune Thresholds**: Adjust violation thresholds based on normal usage patterns

## Future Enhancements

- Machine learning-based anomaly detection
- Automated incident response workflows
- Alert correlation and deduplication
- Self-healing capabilities for common issues
- Integration with SIEM systems
- Real-time dashboard for alert visualization
