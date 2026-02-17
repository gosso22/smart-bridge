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

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionFlowServiceTest {

    @Mock
    private UCSClientValidator ucsValidator;

    @Mock
    private UCSToFHIRTransformer transformer;

    @Mock
    private ResilientFHIRClient resilientFHIRClient;

    @Mock
    private FHIRClientService fhirClient;

    @Mock
    private MessageProducerService messageProducer;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ConcurrentTransformationService concurrentTransformationService;

    @Mock
    private Executor transformationExecutor;

    private IngestionFlowService ingestionFlowService;

    @BeforeEach
    void setUp() {
        ingestionFlowService = new IngestionFlowService(
            ucsValidator,
            transformer,
            resilientFHIRClient,
            fhirClient,
            messageProducer,
            auditLogger,
            concurrentTransformationService,
            transformationExecutor
        );
    }

    @Test
    void testProcessIngestion_Success() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id"
        );
        
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
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestion(ucsClient);
        
        // Assert
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
        verify(auditLogger).logTransformation(
            eq("UCS"), eq("FHIR"), eq("INGESTION"),
            anyString(), eq("123"), eq(true), anyString()
        );
        verify(auditLogger).logFHIROperation(
            eq("CREATE"), eq("Patient"), eq("123"),
            anyString(), eq(true), anyString()
        );
    }

    @Test
    void testProcessIngestion_ValidationFailure() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        
        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Invalid client data"));
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestion(ucsClient);
        
        // Assert
        assertFalse(result.isSuccess());
        assertFalse(result.isValidationPassed());
        assertFalse(result.isTransformationCompleted());
        assertFalse(result.isFhirStorageCompleted());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("validation failed"));
        
        verify(ucsValidator).validate(ucsClient);
        verify(transformer, never()).transformUCSToFHIR(any());
        verify(resilientFHIRClient, never()).createPatient(any());
        verify(auditLogger).logTransformation(
            eq("UCS"), eq("FHIR"), eq("INGESTION"),
            anyString(), isNull(), eq(false), anyString()
        );
    }

    @Test
    void testProcessIngestion_TransformationFailure() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        
        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenThrow(new TransformationException("Transformation error"));
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestion(ucsClient);
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertFalse(result.isTransformationCompleted());
        assertFalse(result.isFhirStorageCompleted());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Transformation failed"));
        
        verify(ucsValidator).validate(ucsClient);
        verify(transformer).transformUCSToFHIR(ucsClient);
        verify(resilientFHIRClient, never()).createPatient(any());
        verify(messageProducer).sendToRetryQueue(any());
    }

    @Test
    void testProcessIngestion_FHIRStorageFailure() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id"
        );
        
        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.valid());
        when(transformer.transformUCSToFHIR(any(UCSClient.class)))
            .thenReturn((FHIRResourceWrapper) wrapper);
        when(resilientFHIRClient.createPatient(any(Patient.class)))
            .thenThrow(new RuntimeException("FHIR server error"));
        when(fhirClient.getServerBaseUrl())
            .thenReturn("http://localhost:8080/fhir");
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestion(ucsClient);
        
        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertFalse(result.isFhirStorageCompleted());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("FHIR storage failed"));
        
        verify(resilientFHIRClient).createPatient(patient);
        verify(auditLogger).logFHIROperation(
            eq("CREATE"), eq("Patient"), isNull(),
            anyString(), eq(false), anyString()
        );
        verify(messageProducer).sendToRetryQueue(any());
    }

    @Test
    void testProcessIngestion_PerformanceMonitoring() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id"
        );
        
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
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestion(ucsClient);
        
        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getDurationMs() >= 0);
        assertTrue(result.getDurationMs() < 5000); // Should be under 5 seconds
    }

    @Test
    void testProcessIngestionWithTransaction_Success() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        Patient patient = createTestPatient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
            patient, "UCS", "test-id"
        );
        
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
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestionWithTransaction(ucsClient);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("123", result.getFhirResourceId());
    }

    @Test
    void testProcessIngestionWithTransaction_Rollback() throws Exception {
        // Arrange
        UCSClient ucsClient = createTestUCSClient();
        
        when(ucsValidator.validate(any(UCSClient.class)))
            .thenReturn(UCSClientValidator.ValidationResult.invalid("Invalid data"));
        
        // Act
        IngestionFlowService.IngestionFlowResult result = 
            ingestionFlowService.processIngestionWithTransaction(ucsClient);
        
        // Assert
        assertFalse(result.isSuccess());
        verify(auditLogger).logError(
            eq("IngestionFlowService"), eq("rollbackTransaction"),
            eq("TRANSACTION_ROLLBACK"), anyString(), any()
        );
    }

    // Helper methods

    private UCSClient createTestUCSClient() {
        UCSClient client = new UCSClient();
        
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId("test-opensrp-123");
        identifiers.setNationalId("test-national-456");
        client.setIdentifiers(identifiers);
        
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName("John");
        demographics.setLastName("Doe");
        demographics.setGender("M");
        demographics.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
        client.setDemographics(demographics);
        
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
