package com.smartbridge.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Timer reverseSyncTimer(MeterRegistry registry) {
        return Timer.builder("smartbridge.reverse.sync.duration")
                .description("Duration of reverse sync operations")
                .register(registry);
    }

    @Bean
    public Counter reverseSyncSuccessCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.reverse.sync.success")
                .description("Successful reverse sync operations")
                .register(registry);
    }

    @Bean
    public Counter reverseSyncErrorCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.reverse.sync.error")
                .description("Failed reverse sync operations")
                .register(registry);
    }

    @Bean
    public Counter conflictDetectedCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.reverse.sync.conflict")
                .description("Conflicts detected during reverse sync")
                .register(registry);
    }

    // ==================== CHT Metrics ====================

    @Bean
    public Timer chtTransformationTimer(MeterRegistry registry) {
        return Timer.builder("smartbridge.cht.transformation.duration")
                .description("Duration of CHT transformation operations")
                .register(registry);
    }

    @Bean
    public Counter chtTransformationSuccessCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.cht.transformation.success")
                .description("Successful CHT transformation operations")
                .register(registry);
    }

    @Bean
    public Counter chtTransformationErrorCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.cht.transformation.error")
                .description("Failed CHT transformation operations")
                .register(registry);
    }

    @Bean
    public Counter chtSyncBatchCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.cht.sync.batch")
                .description("CHT sync batch operations")
                .register(registry);
    }

    @Bean
    public Timer chtReverseSyncTimer(MeterRegistry registry) {
        return Timer.builder("smartbridge.cht.reverse.sync.duration")
                .description("Duration of CHT reverse sync operations")
                .register(registry);
    }

    @Bean
    public Counter chtReverseSyncSuccessCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.cht.reverse.sync.success")
                .description("Successful CHT reverse sync operations")
                .register(registry);
    }

    @Bean
    public Counter chtReverseSyncErrorCounter(MeterRegistry registry) {
        return Counter.builder("smartbridge.cht.reverse.sync.error")
                .description("Failed CHT reverse sync operations")
                .register(registry);
    }
}
