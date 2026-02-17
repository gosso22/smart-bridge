package com.smartbridge.core.client;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FHIRClientService.
 * Tests configuration, authentication, and CRUD operations for FHIR resources.
 */
class FHIRClientServiceTest {

    private FHIRClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new FHIRClientService();
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(clientService);
        assertFalse(clientService.isConfigured());
        assertEquals(FHIRClientService.AuthenticationType.NONE, clientService.getAuthenticationType());
    }

    @Test
    void testConfigureServerUrl() {
        String serverUrl = "http://localhost:8080/fhir";
        clientService.configure(serverUrl);
        
        assertTrue(clientService.isConfigured());
        assertEquals(serverUrl, clientService.getServerBaseUrl());
    }

    @Test
    void testConfigureBasicAuthWithoutClient() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.configureBasicAuth("username", "password");
        });
        
        assertTrue(exception.getMessage().contains("Client must be configured"));
    }

    @Test
    void testConfigureBearerTokenWithoutClient() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.configureBearerToken("token123");
        });
        
        assertTrue(exception.getMessage().contains("Client must be configured"));
    }

    @Test
    void testConfigureBasicAuth() {
        clientService.configure("http://localhost:8080/fhir");
        clientService.configureBasicAuth("username", "password");
        
        assertEquals(FHIRClientService.AuthenticationType.BASIC, clientService.getAuthenticationType());
    }

    @Test
    void testConfigureBearerToken() {
        clientService.configure("http://localhost:8080/fhir");
        clientService.configureBearerToken("token123");
        
        assertEquals(FHIRClientService.AuthenticationType.BEARER_TOKEN, clientService.getAuthenticationType());
    }

    @Test
    void testCreatePatientWithoutConfiguration() {
        Patient patient = new Patient();
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.createPatient(patient);
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testGetPatientWithoutConfiguration() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.getPatient("123");
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testUpdatePatientWithoutId() {
        clientService.configure("http://localhost:8080/fhir");
        Patient patient = new Patient();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            clientService.updatePatient(patient);
        });
        
        assertTrue(exception.getMessage().contains("Patient must have an ID"));
    }

    @Test
    void testUpdateObservationWithoutId() {
        clientService.configure("http://localhost:8080/fhir");
        Observation observation = new Observation();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            clientService.updateObservation(observation);
        });
        
        assertTrue(exception.getMessage().contains("Observation must have an ID"));
    }

    @Test
    void testUpdateTaskWithoutId() {
        clientService.configure("http://localhost:8080/fhir");
        Task task = new Task();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            clientService.updateTask(task);
        });
        
        assertTrue(exception.getMessage().contains("Task must have an ID"));
    }

    @Test
    void testUpdateMedicationRequestWithoutId() {
        clientService.configure("http://localhost:8080/fhir");
        MedicationRequest medicationRequest = new MedicationRequest();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            clientService.updateMedicationRequest(medicationRequest);
        });
        
        assertTrue(exception.getMessage().contains("MedicationRequest must have an ID"));
    }

    @Test
    void testCreateObservationWithoutConfiguration() {
        Observation observation = new Observation();
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.createObservation(observation);
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testCreateTaskWithoutConfiguration() {
        Task task = new Task();
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.createTask(task);
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testCreateMedicationRequestWithoutConfiguration() {
        MedicationRequest medicationRequest = new MedicationRequest();
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.createMedicationRequest(medicationRequest);
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testSearchPatientsWithoutConfiguration() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.searchPatientsUpdatedAfter(new Date());
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }

    @Test
    void testSearchObservationsWithoutConfiguration() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            clientService.searchObservationsByPatient("patient123", new Date());
        });
        
        assertTrue(exception.getMessage().contains("FHIR client not configured"));
    }
}
