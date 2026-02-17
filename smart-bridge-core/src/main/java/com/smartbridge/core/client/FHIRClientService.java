package com.smartbridge.core.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.param.DateRangeParam;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * FHIR client service for interacting with HAPI FHIR server.
 * Provides CRUD operations for Patient, Observation, Task, and MedicationRequest resources.
 * Supports authentication and connection management.
 */
public class FHIRClientService {

    private static final Logger logger = LoggerFactory.getLogger(FHIRClientService.class);
    
    private final FhirContext fhirContext;
    private IGenericClient client;
    private String serverBaseUrl;
    private AuthenticationType authenticationType;
    
    public enum AuthenticationType {
        NONE,
        BASIC,
        BEARER_TOKEN
    }

    public FHIRClientService() {
        this.fhirContext = FhirContext.forR4();
        // Disable server validation for faster startup
        this.fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        this.authenticationType = AuthenticationType.NONE;
    }

    /**
     * Configure the FHIR client with server URL
     */
    public void configure(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
        this.client = fhirContext.newRestfulGenericClient(serverBaseUrl);
        logger.info("FHIR client configured for server: {}", serverBaseUrl);
    }

    /**
     * Configure basic authentication
     */
    public void configureBasicAuth(String username, String password) {
        if (client == null) {
            throw new IllegalStateException("Client must be configured before setting authentication");
        }
        
        BasicAuthInterceptor authInterceptor = new BasicAuthInterceptor(username, password);
        client.registerInterceptor(authInterceptor);
        this.authenticationType = AuthenticationType.BASIC;
        logger.info("Basic authentication configured for FHIR client");
    }

    /**
     * Configure bearer token authentication
     */
    public void configureBearerToken(String token) {
        if (client == null) {
            throw new IllegalStateException("Client must be configured before setting authentication");
        }
        
        BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(token);
        client.registerInterceptor(authInterceptor);
        this.authenticationType = AuthenticationType.BEARER_TOKEN;
        logger.info("Bearer token authentication configured for FHIR client");
    }

    // ========== Patient Operations ==========

    /**
     * Create a new Patient resource
     */
    public MethodOutcome createPatient(Patient patient) {
        validateClient();
        logger.debug("Creating Patient resource");
        
        try {
            MethodOutcome outcome = client.create()
                .resource(patient)
                .execute();
            
            logger.info("Patient created with ID: {}", outcome.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error creating Patient resource", e);
            throw new FHIRClientException("Failed to create Patient", e);
        }
    }

    /**
     * Get a Patient resource by ID
     */
    public Patient getPatient(String patientId) {
        validateClient();
        logger.debug("Retrieving Patient with ID: {}", patientId);
        
        try {
            Patient patient = client.read()
                .resource(Patient.class)
                .withId(patientId)
                .execute();
            
            logger.info("Patient retrieved: {}", patientId);
            return patient;
        } catch (Exception e) {
            logger.error("Error retrieving Patient with ID: {}", patientId, e);
            throw new FHIRClientException("Failed to retrieve Patient: " + patientId, e);
        }
    }

    /**
     * Update an existing Patient resource
     */
    public MethodOutcome updatePatient(Patient patient) {
        validateClient();
        
        if (patient.getId() == null || patient.getId().isEmpty()) {
            throw new IllegalArgumentException("Patient must have an ID for update operation");
        }
        
        logger.debug("Updating Patient with ID: {}", patient.getId());
        
        try {
            MethodOutcome outcome = client.update()
                .resource(patient)
                .execute();
            
            logger.info("Patient updated: {}", patient.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error updating Patient with ID: {}", patient.getId(), e);
            throw new FHIRClientException("Failed to update Patient: " + patient.getId(), e);
        }
    }

    /**
     * Search for Patients updated after a specific date
     */
    public List<Patient> searchPatientsUpdatedAfter(Date lastUpdated) {
        validateClient();
        logger.debug("Searching for Patients updated after: {}", lastUpdated);
        
        try {
            Bundle bundle = client.search()
                .forResource(Patient.class)
                .lastUpdated(new DateRangeParam().setLowerBoundInclusive(lastUpdated))
                .returnBundle(Bundle.class)
                .execute();
            
            List<Patient> patients = extractResources(bundle, Patient.class);
            logger.info("Found {} Patients updated after {}", patients.size(), lastUpdated);
            return patients;
        } catch (Exception e) {
            logger.error("Error searching for Patients", e);
            throw new FHIRClientException("Failed to search for Patients", e);
        }
    }

    // ========== Observation Operations ==========

    /**
     * Create a new Observation resource
     */
    public MethodOutcome createObservation(Observation observation) {
        validateClient();
        logger.debug("Creating Observation resource");
        
        try {
            MethodOutcome outcome = client.create()
                .resource(observation)
                .execute();
            
            logger.info("Observation created with ID: {}", outcome.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error creating Observation resource", e);
            throw new FHIRClientException("Failed to create Observation", e);
        }
    }

    /**
     * Get an Observation resource by ID
     */
    public Observation getObservation(String observationId) {
        validateClient();
        logger.debug("Retrieving Observation with ID: {}", observationId);
        
        try {
            Observation observation = client.read()
                .resource(Observation.class)
                .withId(observationId)
                .execute();
            
            logger.info("Observation retrieved: {}", observationId);
            return observation;
        } catch (Exception e) {
            logger.error("Error retrieving Observation with ID: {}", observationId, e);
            throw new FHIRClientException("Failed to retrieve Observation: " + observationId, e);
        }
    }

    /**
     * Update an existing Observation resource
     */
    public MethodOutcome updateObservation(Observation observation) {
        validateClient();
        
        if (observation.getId() == null || observation.getId().isEmpty()) {
            throw new IllegalArgumentException("Observation must have an ID for update operation");
        }
        
        logger.debug("Updating Observation with ID: {}", observation.getId());
        
        try {
            MethodOutcome outcome = client.update()
                .resource(observation)
                .execute();
            
            logger.info("Observation updated: {}", observation.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error updating Observation with ID: {}", observation.getId(), e);
            throw new FHIRClientException("Failed to update Observation: " + observation.getId(), e);
        }
    }

    /**
     * Search for Observations by patient and updated date
     */
    public List<Observation> searchObservationsByPatient(String patientId, Date lastUpdated) {
        validateClient();
        logger.debug("Searching for Observations for patient: {} updated after: {}", patientId, lastUpdated);
        
        try {
            Bundle bundle = client.search()
                .forResource(Observation.class)
                .where(Observation.PATIENT.hasId(patientId))
                .lastUpdated(new DateRangeParam().setLowerBoundInclusive(lastUpdated))
                .returnBundle(Bundle.class)
                .execute();
            
            List<Observation> observations = extractResources(bundle, Observation.class);
            logger.info("Found {} Observations for patient {}", observations.size(), patientId);
            return observations;
        } catch (Exception e) {
            logger.error("Error searching for Observations", e);
            throw new FHIRClientException("Failed to search for Observations", e);
        }
    }

    // ========== Task Operations ==========

    /**
     * Create a new Task resource
     */
    public MethodOutcome createTask(Task task) {
        validateClient();
        logger.debug("Creating Task resource");
        
        try {
            MethodOutcome outcome = client.create()
                .resource(task)
                .execute();
            
            logger.info("Task created with ID: {}", outcome.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error creating Task resource", e);
            throw new FHIRClientException("Failed to create Task", e);
        }
    }

    /**
     * Get a Task resource by ID
     */
    public Task getTask(String taskId) {
        validateClient();
        logger.debug("Retrieving Task with ID: {}", taskId);
        
        try {
            Task task = client.read()
                .resource(Task.class)
                .withId(taskId)
                .execute();
            
            logger.info("Task retrieved: {}", taskId);
            return task;
        } catch (Exception e) {
            logger.error("Error retrieving Task with ID: {}", taskId, e);
            throw new FHIRClientException("Failed to retrieve Task: " + taskId, e);
        }
    }

    /**
     * Update an existing Task resource
     */
    public MethodOutcome updateTask(Task task) {
        validateClient();
        
        if (task.getId() == null || task.getId().isEmpty()) {
            throw new IllegalArgumentException("Task must have an ID for update operation");
        }
        
        logger.debug("Updating Task with ID: {}", task.getId());
        
        try {
            MethodOutcome outcome = client.update()
                .resource(task)
                .execute();
            
            logger.info("Task updated: {}", task.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error updating Task with ID: {}", task.getId(), e);
            throw new FHIRClientException("Failed to update Task: " + task.getId(), e);
        }
    }

    // ========== Subscription Operations ==========

    /**
     * Create a new Subscription resource
     */
    public MethodOutcome createSubscription(Subscription subscription) {
        validateClient();
        logger.debug("Creating Subscription resource");
        
        try {
            MethodOutcome outcome = client.create()
                .resource(subscription)
                .execute();
            
            logger.info("Subscription created with ID: {}", outcome.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error creating Subscription resource", e);
            throw new FHIRClientException("Failed to create Subscription", e);
        }
    }

    /**
     * Get a Subscription resource by ID
     */
    public Subscription getSubscription(String subscriptionId) {
        validateClient();
        logger.debug("Retrieving Subscription with ID: {}", subscriptionId);
        
        try {
            Subscription subscription = client.read()
                .resource(Subscription.class)
                .withId(subscriptionId)
                .execute();
            
            logger.info("Subscription retrieved: {}", subscriptionId);
            return subscription;
        } catch (Exception e) {
            logger.error("Error retrieving Subscription with ID: {}", subscriptionId, e);
            throw new FHIRClientException("Failed to retrieve Subscription: " + subscriptionId, e);
        }
    }

    /**
     * Update an existing Subscription resource
     */
    public MethodOutcome updateSubscription(Subscription subscription) {
        validateClient();
        
        if (subscription.getId() == null || subscription.getId().isEmpty()) {
            throw new IllegalArgumentException("Subscription must have an ID for update operation");
        }
        
        logger.debug("Updating Subscription with ID: {}", subscription.getId());
        
        try {
            MethodOutcome outcome = client.update()
                .resource(subscription)
                .execute();
            
            logger.info("Subscription updated: {}", subscription.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error updating Subscription with ID: {}", subscription.getId(), e);
            throw new FHIRClientException("Failed to update Subscription: " + subscription.getId(), e);
        }
    }

    /**
     * Delete a Subscription resource
     */
    public void deleteSubscription(String subscriptionId) {
        validateClient();
        logger.debug("Deleting Subscription with ID: {}", subscriptionId);
        
        try {
            client.delete()
                .resourceById("Subscription", subscriptionId)
                .execute();
            
            logger.info("Subscription deleted: {}", subscriptionId);
        } catch (Exception e) {
            logger.error("Error deleting Subscription with ID: {}", subscriptionId, e);
            throw new FHIRClientException("Failed to delete Subscription: " + subscriptionId, e);
        }
    }

    // ========== MedicationRequest Operations ==========

    /**
     * Create a new MedicationRequest resource
     */
    public MethodOutcome createMedicationRequest(MedicationRequest medicationRequest) {
        validateClient();
        logger.debug("Creating MedicationRequest resource");
        
        try {
            MethodOutcome outcome = client.create()
                .resource(medicationRequest)
                .execute();
            
            logger.info("MedicationRequest created with ID: {}", outcome.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error creating MedicationRequest resource", e);
            throw new FHIRClientException("Failed to create MedicationRequest", e);
        }
    }

    /**
     * Get a MedicationRequest resource by ID
     */
    public MedicationRequest getMedicationRequest(String medicationRequestId) {
        validateClient();
        logger.debug("Retrieving MedicationRequest with ID: {}", medicationRequestId);
        
        try {
            MedicationRequest medicationRequest = client.read()
                .resource(MedicationRequest.class)
                .withId(medicationRequestId)
                .execute();
            
            logger.info("MedicationRequest retrieved: {}", medicationRequestId);
            return medicationRequest;
        } catch (Exception e) {
            logger.error("Error retrieving MedicationRequest with ID: {}", medicationRequestId, e);
            throw new FHIRClientException("Failed to retrieve MedicationRequest: " + medicationRequestId, e);
        }
    }

    /**
     * Update an existing MedicationRequest resource
     */
    public MethodOutcome updateMedicationRequest(MedicationRequest medicationRequest) {
        validateClient();
        
        if (medicationRequest.getId() == null || medicationRequest.getId().isEmpty()) {
            throw new IllegalArgumentException("MedicationRequest must have an ID for update operation");
        }
        
        logger.debug("Updating MedicationRequest with ID: {}", medicationRequest.getId());
        
        try {
            MethodOutcome outcome = client.update()
                .resource(medicationRequest)
                .execute();
            
            logger.info("MedicationRequest updated: {}", medicationRequest.getId());
            return outcome;
        } catch (Exception e) {
            logger.error("Error updating MedicationRequest with ID: {}", medicationRequest.getId(), e);
            throw new FHIRClientException("Failed to update MedicationRequest: " + medicationRequest.getId(), e);
        }
    }

    // ========== Utility Methods ==========

    /**
     * Extract resources from a Bundle
     */
    private <T extends Resource> List<T> extractResources(Bundle bundle, Class<T> resourceClass) {
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(resourceClass::isInstance)
            .map(resourceClass::cast)
            .toList();
    }

    /**
     * Validate that the client is configured
     */
    private void validateClient() {
        if (client == null) {
            throw new IllegalStateException("FHIR client not configured. Call configure() first.");
        }
    }

    // ========== Getters ==========

    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public boolean isConfigured() {
        return client != null;
    }
}
