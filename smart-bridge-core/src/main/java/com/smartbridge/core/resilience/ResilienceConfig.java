package com.smartbridge.core.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for resilience patterns including circuit breakers,
 * retry policies, and network monitoring.
 */
@Configuration
@ConfigurationProperties(prefix = "smartbridge.resilience")
public class ResilienceConfig {

    private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();
    private RetrySettings retry = new RetrySettings();
    private NetworkMonitorSettings networkMonitor = new NetworkMonitorSettings();

    @Bean
    public CircuitBreaker fhirCircuitBreaker() {
        return new CircuitBreaker(
            "FHIR",
            circuitBreaker.getFailureThreshold(),
            Duration.ofSeconds(circuitBreaker.getCooldownSeconds()),
            circuitBreaker.getSuccessThreshold()
        );
    }

    @Bean
    public CircuitBreaker ucsCircuitBreaker() {
        return new CircuitBreaker(
            "UCS",
            circuitBreaker.getFailureThreshold(),
            Duration.ofSeconds(circuitBreaker.getCooldownSeconds()),
            circuitBreaker.getSuccessThreshold()
        );
    }

    @Bean
    public RetryPolicy fhirRetryPolicy() {
        return new RetryPolicy.Builder("FHIR")
            .maxAttempts(retry.getMaxAttempts())
            .initialDelay(Duration.ofMillis(retry.getInitialDelayMillis()))
            .maxDelay(Duration.ofMillis(retry.getMaxDelayMillis()))
            .backoffMultiplier(retry.getBackoffMultiplier())
            .build();
    }

    @Bean
    public RetryPolicy ucsRetryPolicy() {
        return new RetryPolicy.Builder("UCS")
            .maxAttempts(retry.getMaxAttempts())
            .initialDelay(Duration.ofMillis(retry.getInitialDelayMillis()))
            .maxDelay(Duration.ofMillis(retry.getMaxDelayMillis()))
            .backoffMultiplier(retry.getBackoffMultiplier())
            .build();
    }

    @Bean
    public NetworkMonitor networkMonitor() {
        return new NetworkMonitor(
            Duration.ofSeconds(networkMonitor.getCheckIntervalSeconds()),
            networkMonitor.getTimeoutMillis()
        );
    }

    public CircuitBreakerSettings getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerSettings circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public RetrySettings getRetry() {
        return retry;
    }

    public void setRetry(RetrySettings retry) {
        this.retry = retry;
    }

    public NetworkMonitorSettings getNetworkMonitor() {
        return networkMonitor;
    }

    public void setNetworkMonitor(NetworkMonitorSettings networkMonitor) {
        this.networkMonitor = networkMonitor;
    }

    public static class CircuitBreakerSettings {
        private int failureThreshold = 5;
        private int cooldownSeconds = 30;
        private int successThreshold = 3;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        public int getSuccessThreshold() {
            return successThreshold;
        }

        public void setSuccessThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
        }
    }

    public static class RetrySettings {
        private int maxAttempts = 3;
        private long initialDelayMillis = 1000;
        private long maxDelayMillis = 32000;
        private double backoffMultiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMillis() {
            return initialDelayMillis;
        }

        public void setInitialDelayMillis(long initialDelayMillis) {
            this.initialDelayMillis = initialDelayMillis;
        }

        public long getMaxDelayMillis() {
            return maxDelayMillis;
        }

        public void setMaxDelayMillis(long maxDelayMillis) {
            this.maxDelayMillis = maxDelayMillis;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    public static class NetworkMonitorSettings {
        private int checkIntervalSeconds = 30;
        private int timeoutMillis = 5000;

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }

        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
    }
}
