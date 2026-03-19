package com.smartbridge.core.flow;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.transformation.ConcurrentTransformationService;
import com.smartbridge.core.transformation.UCSToFHIRTransformer;
import com.smartbridge.core.validation.UCSClientValidator;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionFlowServiceTest {

    @Mock private UCSClientValidator ucsValidator;
    @Mock private UCSToFHIRTransformer transformer;
    @Mock private ResilientFHIRClient resilientFHIRClient;
    @Mock private FHIRClientService fhirClient;
    @Mock private MessageProducerService messageProducer;
    @Mock private AuditLogger auditLogger;
    @Mock private ConcurrentTransformationService concurrentTransformationService;
    @Mock private Executor transformationExecutor;

    private IngestionFlowService ingestionFlowService;

    @BeforeEach
    void setUp() {
        ingestionFlowService = new IngestionFlowService(
            ucsValidator, transformer, resilientFHIRClient, fhirClient,
            messageProducer, auditLogger, concurrentTransformationService,
            transformationExecutor);
    }

    @Test
    void testProcessIngestion_Success() throws Exception {
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id");

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", "123"));

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenReturn((FHIRResourceWrapper) wrapper);
        when(resilientFHIRClient.createPatient(any(Patient.class)))
            .thenReturn(outcome);
        when(fhirClient.getServerBaseUrl())
            .thenReturn("http://localhost:8080/fhir");

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestion(ucsClient);

        assertTrue(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
        assertEquals("123", result.getFhirResourceId());
        assertNotNull(result.getTransactionId());
        assertTrue(result.getDurationMs() >= 0);

        verify(ucsValidator).validate(ucsClient);
        verify(transformer).transformUCSToFHIR(ucsClient);
        verify(resilientFHIRClient).createPatient(patient);
    }

    @Test
    void testProcessIngestion_ValidationFailure() throws Exception {
        UCSClient ucsClient = createTestUCSClient();

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Invalid client data"));

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestion(ucsClient);

        assertFalse(result.isSuccess());
        assertFalse(result.isValidationPassed());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("validation failed"));

        verify(transformer, never()).transformUCSToFHIR(any());
        verify(resilientFHIRClient, never()).createPatient(any());
    }

    @Test
    void testProcessIngestion_TransformationFailure() throws Exception {
        UCSClient ucsClient = createTestUCSClient();

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenThrow(new TransformationException("Transformation error"));

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestion(ucsClient);

        assertFalse(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertFalse(result.isTransformationCompleted());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Transformation failed"));

        verify(resilientFHIRClient, never()).createPatient(any());
        verify(messageProducer).sendToRetryQueue(any());
    }

    @Test
    void testProcessIngestion_FHIRStorageFailure() throws Exception {
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id");

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenReturn((FHIRResourceWrapper) wrapper);
        when(resilientFHIRClient.createPatient(any(Patient.class)))
            .thenThrow(new RuntimeException("FHIR server error"));
        when(fhirClient.getServerBaseUrl())
            .thenReturn("http://localhost:8080/fhir");

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestion(ucsClient);

        assertFalse(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertFalse(result.isFhirStorageCompleted());
        assertTrue(result.getErrorMessage().contains("FHIR storage failed"));

        verify(messageProducer).sendToRetryQueue(any());
    }

    @Test
    void testProcessIngestion_PerformanceMonitoring() throws Exception {
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id");

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", "123"));

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenReturn((FHIRResourceWrapper) wrapper);
        when(resilientFHIRClient.createPatient(any(Patient.class)))
            .thenReturn(outcome);
        when(fhirClient.getServerBaseUrl())
            .thenReturn("http://localhost:8080/fhir");

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestion(ucsClient);

        assertTrue(result.isSuccess());
        assertTrue(result.getDurationMs() >= 0);
        assertTrue(result.getDurationMs() < 5000);
    }

    @Test
    void testProcessIngestionWithTransaction_Success() throws Exception {
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id");

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient", "123"));

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenReturn((FHIRResourceWrapper) wrapper);
        when(resilientFHIRClient.createPatient(any(Patient.class)))
            .thenReturn(outcome);
        when(fhirClient.getServerBaseUrl())
            .thenReturn("http://localhost:8080/fhir");

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestionWithTransaction(ucsClient);

        assertTrue(result.isSuccess());
        assertEquals("123", result.getFhirResourceId());
    }

    @Test
    void testProcessIngestionWithTransaction_Rollback() throws Exception {
        UCSClient ucsClient = createTestUCSClient();

        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Invalid data"));

        IngestionFlowService.IngestionFlowResult result =
            ingestionFlowService.processIngestionWithTransaction(ucsClient);

        assertFalse(result.isSuccess());
        verify(auditLogger).logError(
            eq("IngestionFlowService"), eq("rollbackTransaction"),
            eq("TRANSACTION_ROLLBACK"), anyString(), any());
    }

    // Helper methods — flat OpenSRP structure

    private UCSClient createTestUCSClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("test-base-entity-123");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "test-opensrp-123");
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "test-national-456");
        client.setAttributes(attributes);

        client.setFirstName("John");
        client.setLastName("Doe");
        client.setGender("Male");
        client.setBirthdate("1990-01-01");

        return client;
    }

    private Patient createTestPatient() {
        Patient patient = new Patient();
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("test-opensrp-123");
        patient.addName()
            .setFamily("Doe")
            .addGiven("John");
        patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
        return patient;
    }
}
