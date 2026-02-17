package com.smartbridge.core.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Service for recording metrics and performance data for Smart Bridge operations.
 * Provides methods to track transformation throughput, latency, and operation counts.
 */
@Service
public class MetricsService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record a transformation operation with timing.
     *
     * @param sourceSystem Source system (e.g., "ucs", "fhir")
     * @param targetSystem Target system (e.g., "fhir", "ucs")
     * @param operation The operation to execute
     * @param <T> Return type
     * @return Result of the operation
     * @throws Exception if operation fails
     */
    public <T> T recordTransformation(String sourceSystem, String targetSystem, Callable<T> operation) throws Exception {
        Timer timer = Timer.builder("smart_bridge_transformation_duration")
                .description("Time taken for transformation operations")
                .tag("source", sourceSystem)
                .tag("target", targetSystem)
                .register(meterRegistry);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = operation.call();
            sample.stop(timer);
            incrementCounter("smart_bridge_transformation_success_total", sourceSystem, targetSystem, "success");
            logger.debug("Transformation from {} to {} completed successfully", sourceSystem, targetSystem);
            return result;
        } catch (Exception e) {
            sample.stop(timer);
            incrementCounter("smart_bridge_transformation_error_total", sourceSystem, targetSystem, "error");
            logger.error("Transformation from {} to {} failed: {}", sourceSystem, targetSystem, e.getMessage());
            throw e;
        }
    }

    /**
     * Record a mediator operation with timing.
     *
     * @param mediatorType Type of mediator (e.g., "ucs", "fhir")
     * @param operationType Type of operation (e.g., "read", "write", "update")
     * @param operation The operation to execute
     * @param <T> Return type
     * @return Result of the operation
     * @throws Exception if operation fails
     */
    public <T> T recordMediatorOperation(String mediatorType, String operationType, Callable<T> operation) throws Exception {
        Timer timer = Timer.builder("smart_bridge_mediator_duration")
                .description("Time taken for mediator operations")
                .tag("mediator", mediatorType)
                .tag("operation", operationType)
                .register(meterRegistry);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = operation.call();
            sample.stop(timer);
            incrementCounter("smart_bridge_mediator_operations_total", mediatorType, operationType, "success");
            return result;
        } catch (Exception e) {
            sample.stop(timer);
            incrementCounter("smart_bridge_mediator_operations_total", mediatorType, operationType, "error");
            throw e;
        }
    }

    /**
     * Record FHIR resource operation.
     *
     * @param resourceType FHIR resource type (e.g., "Patient", "Observation")
     * @param operation HTTP operation (e.g., "GET", "POST", "PUT")
     * @param success Whether the operation was successful
     */
    public void recordFHIROperation(String resourceType, String operation, boolean success) {
        Counter.builder("smart_bridge_fhir_operations_total")
                .description("Total number of FHIR resource operations")
                .tag("resource", resourceType)
                .tag("operation", operation)
                .tag("status", success ? "success" : "error")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record UCS API operation.
     *
     * @param endpoint UCS endpoint (e.g., "/clients", "/observations")
     * @param method HTTP method (e.g., "GET", "POST")
     * @param success Whether the operation was successful
     */
    public void recordUCSOperation(String endpoint, String method, boolean success) {
        Counter.builder("smart_bridge_ucs_operations_total")
                .description("Total number of UCS API operations")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", success ? "success" : "error")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record audit log entry.
     *
     * @param eventType Type of audit event
     */
    public void recordAuditLog(String eventType) {
        Counter.builder("smart_bridge_audit_logs_total")
                .description("Total number of audit log entries")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record security event.
     *
     * @param eventType Type of security event
     * @param severity Severity level (e.g., "low", "medium", "high")
     */
    public void recordSecurityEvent(String eventType, String severity) {
        Counter.builder("smart_bridge_security_events_total")
                .description("Total number of security events")
                .tag("event_type", eventType)
                .tag("severity", severity)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record message queue operation.
     *
     * @param queueName Queue name (e.g., "transformation", "retry", "dead-letter")
     * @param operation Operation type (e.g., "enqueue", "dequeue", "retry")
     */
    public void recordQueueOperation(String queueName, String operation) {
        Counter.builder("smart_bridge_queue_operations_total")
                .description("Total number of message queue operations")
                .tag("queue", queueName)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record transformation throughput (items per second).
     *
     * @param count Number of items transformed
     * @param duration Duration of the batch operation
     */
    public void recordThroughput(long count, Duration duration) {
        double throughput = count / (duration.toMillis() / 1000.0);
        meterRegistry.gauge("smart_bridge_transformation_throughput", throughput);
        logger.info("Transformation throughput: {} items/second", throughput);
    }

    /**
     * Record circuit breaker state change.
     *
     * @param serviceName Service name
     * @param state Circuit breaker state (e.g., "open", "closed", "half-open")
     */
    public void recordCircuitBreakerState(String serviceName, String state) {
        Counter.builder("smart_bridge_circuit_breaker_state_changes_total")
                .description("Total number of circuit breaker state changes")
                .tag("service", serviceName)
                .tag("state", state)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment a counter with tags.
     */
    private void incrementCounter(String name, String tag1Value, String tag2Value, String status) {
        Counter.builder(name)
                .tag("source", tag1Value)
                .tag("target", tag2Value)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}
