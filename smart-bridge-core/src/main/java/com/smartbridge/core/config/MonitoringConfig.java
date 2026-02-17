package com.smartbridge.core.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration for monitoring and metrics collection in Smart Bridge.
 * Provides custom metrics for transformation operations, mediator performance,
 * and system health monitoring with Prometheus integration.
 */
@Configuration
@ConditionalOnProperty(name = "smart-bridge.monitoring.metrics-enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringConfig {

    private final AtomicLong activeTransformations = new AtomicLong(0);
    private final AtomicLong queueDepth = new AtomicLong(0);

    /**
     * Customize the meter registry with Smart Bridge specific tags.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            "application", "smart-bridge",
            "version", "1.0.0-SNAPSHOT"
        );
    }

    /**
     * Register JVM memory metrics.
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Register JVM garbage collection metrics.
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Register JVM thread metrics.
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Register processor metrics.
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Gauge for active transformation operations.
     */
    @Bean
    public Gauge activeTransformationsGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("smart_bridge_active_transformations", activeTransformations, AtomicLong::get)
                .description("Number of currently active transformation operations")
                .register(meterRegistry);
    }

    /**
     * Gauge for message queue depth.
     */
    @Bean
    public Gauge queueDepthGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("smart_bridge_queue_depth", queueDepth, AtomicLong::get)
                .description("Current depth of the message queue")
                .register(meterRegistry);
    }

    /**
     * Timer for measuring transformation operation duration.
     */
    @Bean
    public Timer transformationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("smart_bridge_transformation_duration")
                .description("Time taken for UCS to FHIR transformation operations")
                .tag("operation", "transformation")
                .register(meterRegistry);
    }

    /**
     * Counter for successful transformations.
     */
    @Bean
    public Counter transformationSuccessCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_transformation_success_total")
                .description("Total number of successful transformation operations")
                .tag("operation", "transformation")
                .tag("status", "success")
                .register(meterRegistry);
    }

    /**
     * Counter for failed transformations.
     */
    @Bean
    public Counter transformationErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_transformation_error_total")
                .description("Total number of failed transformation operations")
                .tag("operation", "transformation")
                .tag("status", "error")
                .register(meterRegistry);
    }

    /**
     * Timer for measuring mediator operation duration.
     */
    @Bean
    public Timer mediatorTimer(MeterRegistry meterRegistry) {
        return Timer.builder("smart_bridge_mediator_duration")
                .description("Time taken for mediator operations")
                .tag("operation", "mediation")
                .register(meterRegistry);
    }

    /**
     * Counter for FHIR resource operations.
     */
    @Bean
    public Counter fhirOperationCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_fhir_operations_total")
                .description("Total number of FHIR resource operations")
                .tag("operation", "fhir")
                .register(meterRegistry);
    }

    /**
     * Counter for UCS API operations.
     */
    @Bean
    public Counter ucsOperationCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_ucs_operations_total")
                .description("Total number of UCS API operations")
                .tag("operation", "ucs")
                .register(meterRegistry);
    }

    /**
     * Counter for audit log entries.
     */
    @Bean
    public Counter auditLogCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_audit_logs_total")
                .description("Total number of audit log entries")
                .tag("operation", "audit")
                .register(meterRegistry);
    }

    /**
     * Counter for security events.
     */
    @Bean
    public Counter securityEventCounter(MeterRegistry meterRegistry) {
        return Counter.builder("smart_bridge_security_events_total")
                .description("Total number of security events")
                .tag("operation", "security")
                .register(meterRegistry);
    }

    /**
     * Get the active transformations counter for updating.
     */
    public AtomicLong getActiveTransformations() {
        return activeTransformations;
    }

    /**
     * Get the queue depth counter for updating.
     */
    public AtomicLong getQueueDepth() {
        return queueDepth;
    }
}