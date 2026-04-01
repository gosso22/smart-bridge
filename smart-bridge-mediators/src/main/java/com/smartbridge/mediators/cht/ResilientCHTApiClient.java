package com.smartbridge.mediators.cht;

import com.smartbridge.core.model.cht.CHTChangesResponse;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.CircuitBreakerOpenException;
import com.smartbridge.core.resilience.RetryException;
import com.smartbridge.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

/**
 * Resilient wrapper for CHT API client with circuit breaker and retry logic.
 * Provides fault-tolerant CHT operations with automatic retry and failure handling.
 */
public class ResilientCHTApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ResilientCHTApiClient.class);

    private final CHTApiClient chtClient;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    public ResilientCHTApiClient(CHTApiClient chtClient,
                                @Qualifier("chtCircuitBreaker") CircuitBreaker chtCircuitBreaker,
                                @Qualifier("chtRetryPolicy") RetryPolicy chtRetryPolicy) {
        this.chtClient = chtClient;
        this.circuitBreaker = chtCircuitBreaker;
        this.retryPolicy = chtRetryPolicy;
        logger.info("Resilient CHT client initialized with circuit breaker and retry policy");
    }

    public List<CHTContact> listPersons(int limit, int skip) throws Exception {
        return executeWithResilience(() -> chtClient.listPersons(limit, skip), "listPersons");
    }

    public CHTContact getContact(String id) throws Exception {
        return executeWithResilience(() -> chtClient.getContact(id), "getContact");
    }

    public CHTContact createPerson(CHTContact contact) throws Exception {
        return executeWithResilience(() -> chtClient.createPerson(contact), "createPerson");
    }

    public CHTContact updatePerson(String id, CHTContact contact) throws Exception {
        return executeWithResilience(() -> chtClient.updatePerson(id, contact), "updatePerson");
    }

    public String listPlaces(String contactType) throws Exception {
        return executeWithResilience(() -> chtClient.listPlaces(contactType), "listPlaces");
    }

    public String exportReports(String formType) throws Exception {
        return executeWithResilience(() -> chtClient.exportReports(formType), "exportReports");
    }

    public CHTChangesResponse getChanges(String sinceSeq, int limit) throws Exception {
        return executeWithResilience(() -> chtClient.getChanges(sinceSeq, limit), "getChanges");
    }

    public boolean testConnection() {
        try {
            return executeWithResilience(() -> chtClient.testConnection(), "testConnection");
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
                    logger.error("Circuit breaker open for CHT operation: {}", operationName);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RetryException e) {
            logger.error("All retry attempts failed for CHT operation: {}", operationName);
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    public CircuitBreaker.CircuitBreakerMetrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }

    public void resetCircuitBreaker() {
        circuitBreaker.reset();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
