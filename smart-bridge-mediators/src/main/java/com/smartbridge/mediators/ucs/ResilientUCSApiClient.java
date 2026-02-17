package com.smartbridge.mediators.ucs;

import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.CircuitBreakerOpenException;
import com.smartbridge.core.resilience.RetryException;
import com.smartbridge.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Resilient wrapper for UCS API client with circuit breaker and retry logic.
 * Provides fault-tolerant UCS operations with automatic retry and failure handling.
 */
public class ResilientUCSApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ResilientUCSApiClient.class);

    private final UCSApiClient ucsClient;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    public ResilientUCSApiClient(UCSApiClient ucsClient,
                                @Qualifier("ucsCircuitBreaker") CircuitBreaker ucsCircuitBreaker,
                                @Qualifier("ucsRetryPolicy") RetryPolicy ucsRetryPolicy) {
        this.ucsClient = ucsClient;
        this.circuitBreaker = ucsCircuitBreaker;
        this.retryPolicy = ucsRetryPolicy;
        logger.info("Resilient UCS client initialized with circuit breaker and retry policy");
    }

    /**
     * Create a client in UCS with resilience patterns.
     */
    public UCSClient createClient(UCSClient client) throws Exception {
        return executeWithResilience(() -> ucsClient.createClient(client), "createClient");
    }

    /**
     * Update a client in UCS with resilience patterns.
     */
    public UCSClient updateClient(String clientId, UCSClient client) throws Exception {
        return executeWithResilience(() -> ucsClient.updateClient(clientId, client), "updateClient");
    }

    /**
     * Get a client from UCS with resilience patterns.
     */
    public UCSClient getClient(String clientId) throws Exception {
        return executeWithResilience(() -> ucsClient.getClient(clientId), "getClient");
    }

    /**
     * Authenticate with UCS with resilience patterns.
     */
    public String authenticate() throws Exception {
        return executeWithResilience(() -> ucsClient.authenticate(), "authenticate");
    }

    /**
     * Test UCS connection with resilience patterns.
     */
    public boolean testConnection() {
        try {
            return executeWithResilience(() -> ucsClient.testConnection(), "testConnection");
        } catch (Exception e) {
            logger.warn("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute an operation with circuit breaker and retry logic.
     */
    private <T> T executeWithResilience(SupplierWithException<T> operation, String operationName) throws Exception {
        try {
            return retryPolicy.execute(() -> {
                try {
                    return circuitBreaker.execute(() -> {
                        try {
                            return operation.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (CircuitBreakerOpenException e) {
                    logger.error("Circuit breaker open for UCS operation: {}", operationName);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RetryException e) {
            logger.error("All retry attempts failed for UCS operation: {}", operationName);
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Get circuit breaker metrics.
     */
    public CircuitBreaker.CircuitBreakerMetrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }

    /**
     * Reset the circuit breaker.
     */
    public void resetCircuitBreaker() {
        circuitBreaker.reset();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
