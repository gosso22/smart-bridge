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
}
