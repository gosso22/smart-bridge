package com.smartbridge.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retry logic with configurable limits and exponential backoff.
 * Supports custom retry conditions and backoff strategies.
 */
public class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);

    private final String name;
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;
    private final Predicate<Exception> retryCondition;

    /**
     * Create a retry policy with default settings.
     * Default: 3 attempts, 1s initial delay, 32s max delay, 2x backoff
     */
    public RetryPolicy(String name) {
        this(name, 3, Duration.ofSeconds(1), Duration.ofSeconds(32), 2.0, e -> true);
    }

    /**
     * Create a retry policy with custom settings.
     *
     * @param name Policy name for logging
     * @param maxAttempts Maximum number of retry attempts
     * @param initialDelay Initial delay before first retry
     * @param maxDelay Maximum delay between retries
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param retryCondition Predicate to determine if exception should be retried
     */
    public RetryPolicy(String name, int maxAttempts, Duration initialDelay, Duration maxDelay,
                      double backoffMultiplier, Predicate<Exception> retryCondition) {
        this.name = name;
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.backoffMultiplier = backoffMultiplier;
        this.retryCondition = retryCondition;

        logger.info("Retry policy created for {}: maxAttempts={}, initialDelay={}ms, maxDelay={}ms, backoff={}x",
            name, maxAttempts, initialDelay.toMillis(), maxDelay.toMillis(), backoffMultiplier);
    }

    /**
     * Execute an operation with retry logic.
     *
     * @param supplier The operation to execute
     * @return Result of the operation
     * @throws Exception if all retry attempts fail
     */
    public <T> T execute(Supplier<T> supplier) throws Exception {
        Exception lastException = null;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;

            try {
                logger.debug("Executing {} (attempt {}/{})", name, attempt, maxAttempts);
                T result = supplier.get();
                
                if (attempt > 1) {
                    logger.info("Operation {} succeeded on attempt {}", name, attempt);
                }
                
                return result;
            } catch (Exception e) {
                lastException = e;

                if (!retryCondition.test(e)) {
                    logger.warn("Exception not retryable for {}: {}", name, e.getMessage());
                    throw e;
                }

                if (attempt >= maxAttempts) {
                    logger.error("All retry attempts exhausted for {} after {} attempts", name, attempt);
                    break;
                }

                Duration delay = calculateDelay(attempt);
                logger.warn("Operation {} failed (attempt {}/{}), retrying in {}ms: {}",
                    name, attempt, maxAttempts, delay.toMillis(), e.getMessage());

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry interrupted for {}", name);
                    throw new RetryException("Retry interrupted", ie);
                }
            }
        }

        throw new RetryException("All retry attempts failed for: " + name, lastException);
    }

    /**
     * Calculate delay for exponential backoff.
     */
    private Duration calculateDelay(int attempt) {
        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        delayMillis = Math.min(delayMillis, maxDelay.toMillis());
        return Duration.ofMillis(delayMillis);
    }

    public String getName() {
        return name;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * Builder for creating retry policies with fluent API.
     */
    public static class Builder {
        private String name;
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(32);
        private double backoffMultiplier = 2.0;
        private Predicate<Exception> retryCondition = e -> true;

        public Builder(String name) {
            this.name = name;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder retryCondition(Predicate<Exception> retryCondition) {
            this.retryCondition = retryCondition;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(name, maxAttempts, initialDelay, maxDelay, backoffMultiplier, retryCondition);
        }
    }
}
