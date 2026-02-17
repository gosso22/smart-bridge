package com.smartbridge.core.resilience;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.client.FHIRClientService;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Resilient wrapper for FHIR client with circuit breaker and retry logic.
 * Provides fault-tolerant FHIR operations with automatic retry and failure handling.
 */
@Service
public class ResilientFHIRClient {

    private static final Logger logger = LoggerFactory.getLogger(ResilientFHIRClient.class);

    private final FHIRClientService fhirClient;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    public ResilientFHIRClient(FHIRClientService fhirClient,
                              @Qualifier("fhirCircuitBreaker") CircuitBreaker fhirCircuitBreaker,
                              @Qualifier("fhirRetryPolicy") RetryPolicy fhirRetryPolicy) {
        this.fhirClient = fhirClient;
        this.circuitBreaker = fhirCircuitBreaker;
        this.retryPolicy = fhirRetryPolicy;
        logger.info("Resilient FHIR client initialized with circuit breaker and retry policy");
    }

    /**
     * Create a Patient resource with resilience patterns.
     */
    public MethodOutcome createPatient(Patient patient) throws Exception {
        return executeWithResilience(() -> fhirClient.createPatient(patient), "createPatient");
    }

    /**
     * Get a Patient resource with resilience patterns.
     */
    public Patient getPatient(String patientId) throws Exception {
        return executeWithResilience(() -> fhirClient.getPatient(patientId), "getPatient");
    }

    /**
     * Update a Patient resource with resilience patterns.
     */
    public MethodOutcome updatePatient(Patient patient) throws Exception {
        return executeWithResilience(() -> fhirClient.updatePatient(patient), "updatePatient");
    }

    /**
     * Search for Patients with resilience patterns.
     */
    public List<Patient> searchPatientsUpdatedAfter(Date lastUpdated) throws Exception {
        return executeWithResilience(() -> fhirClient.searchPatientsUpdatedAfter(lastUpdated), "searchPatients");
    }

    /**
     * Create an Observation resource with resilience patterns.
     */
    public MethodOutcome createObservation(Observation observation) throws Exception {
        return executeWithResilience(() -> fhirClient.createObservation(observation), "createObservation");
    }

    /**
     * Get an Observation resource with resilience patterns.
     */
    public Observation getObservation(String observationId) throws Exception {
        return executeWithResilience(() -> fhirClient.getObservation(observationId), "getObservation");
    }

    /**
     * Update an Observation resource with resilience patterns.
     */
    public MethodOutcome updateObservation(Observation observation) throws Exception {
        return executeWithResilience(() -> fhirClient.updateObservation(observation), "updateObservation");
    }

    /**
     * Search for Observations with resilience patterns.
     */
    public List<Observation> searchObservationsByPatient(String patientId, Date lastUpdated) throws Exception {
        return executeWithResilience(() -> fhirClient.searchObservationsByPatient(patientId, lastUpdated), "searchObservations");
    }

    /**
     * Create a Task resource with resilience patterns.
     */
    public MethodOutcome createTask(Task task) throws Exception {
        return executeWithResilience(() -> fhirClient.createTask(task), "createTask");
    }

    /**
     * Get a Task resource with resilience patterns.
     */
    public Task getTask(String taskId) throws Exception {
        return executeWithResilience(() -> fhirClient.getTask(taskId), "getTask");
    }

    /**
     * Update a Task resource with resilience patterns.
     */
    public MethodOutcome updateTask(Task task) throws Exception {
        return executeWithResilience(() -> fhirClient.updateTask(task), "updateTask");
    }

    /**
     * Create a Subscription resource with resilience patterns.
     */
    public MethodOutcome createSubscription(Subscription subscription) throws Exception {
        return executeWithResilience(() -> fhirClient.createSubscription(subscription), "createSubscription");
    }

    /**
     * Get a Subscription resource with resilience patterns.
     */
    public Subscription getSubscription(String subscriptionId) throws Exception {
        return executeWithResilience(() -> fhirClient.getSubscription(subscriptionId), "getSubscription");
    }

    /**
     * Update a Subscription resource with resilience patterns.
     */
    public MethodOutcome updateSubscription(Subscription subscription) throws Exception {
        return executeWithResilience(() -> fhirClient.updateSubscription(subscription), "updateSubscription");
    }

    /**
     * Delete a Subscription resource with resilience patterns.
     */
    public void deleteSubscription(String subscriptionId) throws Exception {
        executeWithResilience(() -> {
            fhirClient.deleteSubscription(subscriptionId);
            return null;
        }, "deleteSubscription");
    }

    /**
     * Create a MedicationRequest resource with resilience patterns.
     */
    public MethodOutcome createMedicationRequest(MedicationRequest medicationRequest) throws Exception {
        return executeWithResilience(() -> fhirClient.createMedicationRequest(medicationRequest), "createMedicationRequest");
    }

    /**
     * Get a MedicationRequest resource with resilience patterns.
     */
    public MedicationRequest getMedicationRequest(String medicationRequestId) throws Exception {
        return executeWithResilience(() -> fhirClient.getMedicationRequest(medicationRequestId), "getMedicationRequest");
    }

    /**
     * Update a MedicationRequest resource with resilience patterns.
     */
    public MethodOutcome updateMedicationRequest(MedicationRequest medicationRequest) throws Exception {
        return executeWithResilience(() -> fhirClient.updateMedicationRequest(medicationRequest), "updateMedicationRequest");
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
                    logger.error("Circuit breaker open for FHIR operation: {}", operationName);
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RetryException e) {
            logger.error("All retry attempts failed for FHIR operation: {}", operationName);
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
