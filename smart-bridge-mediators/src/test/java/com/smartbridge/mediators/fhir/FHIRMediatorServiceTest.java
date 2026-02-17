package com.smartbridge.mediators.fhir;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.MediatorService;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FHIRMediatorServiceTest {

    @Mock
    private FHIRClientService fhirClientService;
    
    @Mock
    private FHIRChangeDetectionService changeDetectionService;
    
    @Mock
    private com.smartbridge.core.audit.AuditLogger auditLogger;

    private FHIRMediatorService mediatorService;

    @BeforeEach
    void setUp() {
        mediatorService = new FHIRMediatorService(
            "http://localhost:8080/fhir",
            "NONE",
            "",
            "",
            "",
            fhirClientService,
            changeDetectionService
        );
        
        // Inject the mocked audit logger using reflection
        try {
            java.lang.reflect.Field field = com.smartbridge.mediators.base.BaseMediatorService.class
                .getDeclaredField("auditLogger");
            field.setAccessible(true);
            field.set(mediatorService, auditLogger);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject audit logger", e);
        }
        
        // Use lenient stubbing to avoid UnnecessaryStubbing exceptions
        lenient().when(fhirClientService.isConfigured()).thenReturn(true);
    }

    @Test
    void testGetConfiguration() {
        MediatorService.MediatorConfig config = mediatorService.getConfiguration();
        
        assertNotNull(config);
        assertEquals("FHIR-Mediator", config.getName());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("Mediator for HAPI FHIR server integration", config.getDescription());
    }

    @Test
    void testValidateRequest_NullRequest() {
        Map<String, String> headers = new HashMap<>();
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(null, headers);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    void testValidateRequest_InvalidResourceType() {
        Patient patient = createTestPatient();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Resource-Type", "InvalidResource");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(patient, headers);
        });
        
        assertTrue(exception.getMessage().contains("Invalid resource type"));
    }

    @Test
    void testValidateRequest_InvalidOperation() {
        Patient patient = createTestPatient();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "INVALID");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(patient, headers);
        });
        
        assertTrue(exception.getMessage().contains("Invalid operation"));
    }

    @Test
    void testProcessRequest_CreatePatient() throws MediatorException {
        // Use actual Patient object - in production, FHIR resources come pre-parsed
        Patient patient = createTestPatient();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        headers.put("X-Resource-Type", "Patient");
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", "123"));
        
        when(fhirClientService.createPatient(any(Patient.class))).thenReturn(outcome);
        
        Object result = mediatorService.processRequest(patient, headers);
        
        assertNotNull(result);
        verify(fhirClientService).createPatient(any(Patient.class));
    }

    @Test
    void testProcessRequest_UpdatePatient() throws MediatorException {
        // Use actual Patient object - in production, FHIR resources come pre-parsed
        Patient patient = createTestPatient();
        patient.setId("123");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "UPDATE");
        headers.put("X-Resource-Type", "Patient");
        headers.put("X-Resource-Id", "123");
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", "123"));
        
        when(fhirClientService.updatePatient(any(Patient.class))).thenReturn(outcome);
        
        Object result = mediatorService.processRequest(patient, headers);
        
        assertNotNull(result);
        verify(fhirClientService).updatePatient(any(Patient.class));
    }

    @Test
    void testProcessRequest_UpdatePatientWithoutId() {
        Patient patient = createTestPatient();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "UPDATE");
        headers.put("X-Resource-Type", "Patient");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.processRequest(patient, headers);
        });
        
        assertTrue(exception.getMessage().contains("Resource ID required"));
    }

    @Test
    void testProcessRequest_GetPatient() throws MediatorException {
        Patient patient = createTestPatient();
        patient.setId("123");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "GET");
        headers.put("X-Resource-Type", "Patient");
        headers.put("X-Resource-Id", "123");
        
        when(fhirClientService.getPatient("123")).thenReturn(patient);
        
        // Use empty map for GET operation (request body not used)
        Object result = mediatorService.processRequest(new HashMap<>(), headers);
        
        assertNotNull(result);
        verify(fhirClientService).getPatient("123");
    }

    @Test
    void testProcessRequest_CreateObservation() throws MediatorException {
        Observation observation = createTestObservation();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        headers.put("X-Resource-Type", "Observation");
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Observation", "456"));
        
        when(fhirClientService.createObservation(any(Observation.class))).thenReturn(outcome);
        
        Object result = mediatorService.processRequest(observation, headers);
        
        assertNotNull(result);
        verify(fhirClientService).createObservation(any(Observation.class));
    }

    @Test
    void testProcessRequest_CreateTask() throws MediatorException {
        Task task = createTestTask();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        headers.put("X-Resource-Type", "Task");
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Task", "789"));
        
        when(fhirClientService.createTask(any(Task.class))).thenReturn(outcome);
        
        Object result = mediatorService.processRequest(task, headers);
        
        assertNotNull(result);
        verify(fhirClientService).createTask(any(Task.class));
    }

    @Test
    void testProcessRequest_CreateMedicationRequest() throws MediatorException {
        MedicationRequest medicationRequest = createTestMedicationRequest();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        headers.put("X-Resource-Type", "MedicationRequest");
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("MedicationRequest", "101"));
        
        when(fhirClientService.createMedicationRequest(any(MedicationRequest.class))).thenReturn(outcome);
        
        Object result = mediatorService.processRequest(medicationRequest, headers);
        
        assertNotNull(result);
        verify(fhirClientService).createMedicationRequest(any(MedicationRequest.class));
    }

    @Test
    void testProcessRequest_CreateSubscription() throws MediatorException {
        Map<String, String> subscriptionParams = new HashMap<>();
        subscriptionParams.put("resourceType", "Patient");
        subscriptionParams.put("criteria", "Patient?");
        subscriptionParams.put("webhookUrl", "http://localhost:8080/webhook");
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "CREATE");
        headers.put("X-Resource-Type", "Subscription");
        
        Subscription subscription = new Subscription();
        subscription.setId("sub-123");
        
        when(changeDetectionService.createSubscription(anyString(), anyString(), anyString()))
            .thenReturn(subscription);
        
        Object result = mediatorService.processRequest(subscriptionParams, headers);
        
        assertNotNull(result);
        verify(changeDetectionService).createSubscription("Patient", "Patient?", "http://localhost:8080/webhook");
    }

    @Test
    void testProcessRequest_ActivateSubscription() throws MediatorException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "ACTIVATE");
        headers.put("X-Resource-Type", "Subscription");
        headers.put("X-Resource-Id", "sub-123");
        
        doNothing().when(changeDetectionService).activateSubscription("sub-123");
        
        Object result = mediatorService.processRequest(new HashMap<>(), headers);
        
        assertNotNull(result);
        verify(changeDetectionService).activateSubscription("sub-123");
    }

    @Test
    void testProcessRequest_DeactivateSubscription() throws MediatorException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "DEACTIVATE");
        headers.put("X-Resource-Type", "Subscription");
        headers.put("X-Resource-Id", "sub-123");
        
        doNothing().when(changeDetectionService).deactivateSubscription("sub-123");
        
        Object result = mediatorService.processRequest(new HashMap<>(), headers);
        
        assertNotNull(result);
        verify(changeDetectionService).deactivateSubscription("sub-123");
    }

    @Test
    void testProcessRequest_DeleteSubscription() throws MediatorException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Operation", "DELETE");
        headers.put("X-Resource-Type", "Subscription");
        headers.put("X-Resource-Id", "sub-123");
        
        doNothing().when(changeDetectionService).deleteSubscription("sub-123");
        
        Object result = mediatorService.processRequest(new HashMap<>(), headers);
        
        assertNotNull(result);
        verify(changeDetectionService).deleteSubscription("sub-123");
    }

    @Test
    void testHealthCheck_Configured() {
        when(fhirClientService.isConfigured()).thenReturn(true);
        
        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();
        
        assertNotNull(result);
        assertTrue(result.isHealthy());
        assertEquals("FHIR server is reachable", result.getMessage());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    void testHealthCheck_NotConfigured() {
        when(fhirClientService.isConfigured()).thenReturn(false);
        
        MediatorService.HealthCheckResult result = mediatorService.performHealthCheck();
        
        assertNotNull(result);
        assertFalse(result.isHealthy());
        assertEquals("FHIR client not configured", result.getMessage());
    }

    @Test
    void testAuthenticate_BasicAuth() throws MediatorException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("authType", "BASIC");
        credentials.put("username", "testuser");
        credentials.put("password", "testpass");
        
        doNothing().when(fhirClientService).configureBasicAuth(anyString(), anyString());
        
        String result = mediatorService.authenticate(credentials);
        
        assertEquals("BASIC_AUTH_CONFIGURED", result);
        verify(fhirClientService).configureBasicAuth("testuser", "testpass");
    }

    @Test
    void testAuthenticate_BearerToken() throws MediatorException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("authType", "BEARER");
        credentials.put("token", "test-token-123");
        
        doNothing().when(fhirClientService).configureBearerToken(anyString());
        
        String result = mediatorService.authenticate(credentials);
        
        assertEquals("BEARER_AUTH_CONFIGURED", result);
        verify(fhirClientService).configureBearerToken("test-token-123");
    }

    @Test
    void testAuthenticate_NoAuth() throws MediatorException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("authType", "NONE");
        
        String result = mediatorService.authenticate(credentials);
        
        assertEquals("NO_AUTH", result);
        // Note: fhirClientService.configure() is called in constructor/initialize(), 
        // so we can't use verifyNoInteractions here
    }

    @Test
    void testAuthenticate_BasicAuthMissingCredentials() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("authType", "BASIC");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.authenticate(credentials);
        });
        
        assertTrue(exception.getMessage().contains("Username and password required"));
    }

    @Test
    void testAuthenticate_BearerTokenMissing() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("authType", "BEARER");
        
        MediatorException exception = assertThrows(MediatorException.class, () -> {
            mediatorService.authenticate(credentials);
        });
        
        assertTrue(exception.getMessage().contains("Token required"));
    }

    @Test
    void testGetEndpoints() {
        Map<String, String> endpoints = mediatorService.getEndpoints();
        
        assertNotNull(endpoints);
        assertTrue(endpoints.containsKey("health"));
        assertTrue(endpoints.containsKey("patient"));
        assertTrue(endpoints.containsKey("observation"));
        assertTrue(endpoints.containsKey("task"));
        assertTrue(endpoints.containsKey("medicationRequest"));
        assertTrue(endpoints.containsKey("subscription"));
        assertTrue(endpoints.containsKey("authenticate"));
        assertEquals("/fhir/health", endpoints.get("health"));
        assertEquals("/fhir/Patient", endpoints.get("patient"));
    }

    @Test
    void testGetDefaultChannelConfig() {
        Map<String, Object> config = mediatorService.getDefaultChannelConfig();
        
        assertNotNull(config);
        assertEquals("FHIR Channel", config.get("name"));
        assertEquals("^/fhir/.*$", config.get("urlPattern"));
        assertEquals("http", config.get("type"));
    }

    @Test
    void testGetFhirClientService() {
        FHIRClientService service = mediatorService.getFhirClientService();
        assertNotNull(service);
        assertEquals(fhirClientService, service);
    }

    @Test
    void testGetChangeDetectionService() {
        FHIRChangeDetectionService service = mediatorService.getChangeDetectionService();
        assertNotNull(service);
        assertEquals(changeDetectionService, service);
    }

    // Helper methods to create test resources

    private Patient createTestPatient() {
        Patient patient = new Patient();
        patient.addName()
            .setFamily("Doe")
            .addGiven("John");
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("opensrp-123");
        return patient;
    }

    private Observation createTestObservation() {
        Observation observation = new Observation();
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.getCode()
            .addCoding()
            .setSystem("http://loinc.org")
            .setCode("8867-4")
            .setDisplay("Heart rate");
        observation.setValue(new Quantity()
            .setValue(72)
            .setUnit("beats/minute")
            .setSystem("http://unitsofmeasure.org")
            .setCode("/min"));
        return observation;
    }

    private Task createTestTask() {
        Task task = new Task();
        task.setStatus(Task.TaskStatus.REQUESTED);
        task.setIntent(Task.TaskIntent.ORDER);
        task.setDescription("Test task");
        return task;
    }

    private MedicationRequest createTestMedicationRequest() {
        MedicationRequest medicationRequest = new MedicationRequest();
        medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        
        CodeableConcept medication = new CodeableConcept();
        medication.addCoding()
            .setSystem("http://www.nlm.nih.gov/research/umls/rxnorm")
            .setCode("313782")
            .setDisplay("Acetaminophen 325 MG Oral Tablet");
        medicationRequest.setMedication(medication);
        
        return medicationRequest;
    }
}
