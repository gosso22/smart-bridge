package com.smartbridge.mediators.flow;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.transformation.FHIRToUCSTransformer;
import com.smartbridge.mediators.ucs.UCSApiClient;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReverseSyncFlowService.
 * Tests reverse sync flow from FHIR to UCS including conflict resolution and data consistency.
 */
@ExtendWith(MockitoExtension.class)
class ReverseSyncFlowServiceTest {

    @Mock
    private FHIRChangeDetectionService changeDetectionService;

    @Mock
    private FHIRToUCSTransformer transformer;

    @Mock
    private FHIRClientService fhirClient;

    @Mock
    private UCSApiClient ucsApiClient;

    @Mock
    private AuditLogger auditLogger;

    private ReverseSyncFlowService reverseSyncFlowService;
    private Executor reverseSyncExecutor;

    @BeforeEach
    void setUp() {
        reverseSyncExecutor = Executors.newFixedThreadPool(4);
        reverseSyncFlowService = new ReverseSyncFlowService(
            changeDetectionService,
            transformer,
            fhirClient,
            ucsApiClient,
            auditLogger,
            reverseSyncExecutor
        );
    }

    @Test
    void testProcessReverseSync_Success() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        UCSClient ucsClient = createTestUCSClient();
        
        when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
        when(ucsApiClient.getClient(anyString())).thenThrow(new MediatorException(
            "Not found", "UCS_MEDIATOR", "GET_CLIENT", 404
        ));
        when(ucsApiClient.createClient(any())).thenReturn(ucsClient);

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertTrue(result.isSuccess());
        assertFalse(result.isSkipped());
        assertEquals("Patient", result.getResourceType());
        assertEquals("patient-123", result.getFhirResourceId());
        assertEquals("opensrp-123", result.getUcsClientId());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isUcsStorageCompleted());
        assertEquals("CREATE", result.getUcsOperationType());

        verify(transformer).transformFHIRToUCS(any());
        verify(ucsApiClient).createClient(any());
        verify(auditLogger).logTransformation(
            eq("FHIR"), eq("UCS"), eq("REVERSE_SYNC"),
            eq("patient-123"), eq("opensrp-123"),
            eq(true), anyString()
        );
    }

    @Test
    void testProcessReverseSync_UpdateExistingClient() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        UCSClient ucsClient = createTestUCSClient();
        UCSClient existingClient = createTestUCSClient();
        
        when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
        when(ucsApiClient.getClient(anyString())).thenReturn(existingClient);
        when(ucsApiClient.updateClient(anyString(), any())).thenReturn(ucsClient);

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("UPDATE", result.getUcsOperationType());
        
        verify(ucsApiClient).getClient("opensrp-123");
        verify(ucsApiClient).updateClient(eq("opensrp-123"), any());
        verify(ucsApiClient, never()).createClient(any());
    }

    @Test
    void testProcessReverseSync_TransformationFailure() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        
        when(transformer.transformFHIRToUCS(any()))
            .thenThrow(new RuntimeException("Transformation error"));

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Transformation failed"));
        
        verify(transformer).transformFHIRToUCS(any());
        verify(ucsApiClient, never()).createClient(any());
        verify(auditLogger).logTransformation(
            eq("FHIR"), eq("UCS"), eq("REVERSE_SYNC"),
            eq("patient-123"), isNull(),
            eq(false), anyString()
        );
    }

    @Test
    void testProcessReverseSync_UCSStorageFailure() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        UCSClient ucsClient = createTestUCSClient();
        
        when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
        when(ucsApiClient.getClient(anyString())).thenThrow(new MediatorException(
            "Not found", "UCS_MEDIATOR", "GET_CLIENT", 404
        ));
        when(ucsApiClient.createClient(any())).thenThrow(new MediatorException(
            "Storage error", "UCS_MEDIATOR", "CREATE_CLIENT", 500
        ));

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("UCS storage failed"));
        
        verify(ucsApiClient).createClient(any());
    }

    @Test
    void testProcessReverseSync_UnsupportedResourceType() {
        // Arrange
        Observation observation = new Observation();
        observation.setId("obs-123");

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(observation);

        // Assert
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Only Patient resources supported"));
        
        try {
            verify(transformer, never()).transformFHIRToUCS(any());
            verify(ucsApiClient, never()).createClient(any());
        } catch (Exception e) {
            fail("Verification failed: " + e.getMessage());
        }
    }

    @Test
    void testProcessReverseSync_CircularUpdateDetection() {
        // Arrange
        Patient patient = createTestPatient();
        
        // Add UCS tag to indicate this came from ingestion flow
        Coding tag = new Coding();
        tag.setCode("UCS");
        patient.getMeta().addTag(tag);
        
        // First process to establish baseline
        UCSClient ucsClient = createTestUCSClient();
        try {
            when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
            when(ucsApiClient.getClient(anyString())).thenAnswer(invocation -> {
                throw new MediatorException("Not found", "UCS_MEDIATOR", "GET_CLIENT", 404);
            });
            when(ucsApiClient.createClient(any())).thenReturn(ucsClient);
        } catch (Exception e) {
            fail("Mock setup failed: " + e.getMessage());
        }
        
        reverseSyncFlowService.processReverseSync(patient);
        
        // Update patient timestamp to simulate change
        patient.getMeta().setLastUpdated(new Date(System.currentTimeMillis() + 10000));

        // Act - process again with circular update
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertTrue(result.isConflictDetected());
        assertEquals("SKIP", result.getConflictResolution());
    }

    @Test
    void testProcessReverseSync_DataConsistencyVerification() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        UCSClient ucsClient = createTestUCSClient();
        
        when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
        when(ucsApiClient.getClient(anyString())).thenThrow(new MediatorException(
            "Not found", "UCS_MEDIATOR", "GET_CLIENT", 404
        ));
        when(ucsApiClient.createClient(any())).thenReturn(ucsClient);

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.isConsistencyVerified());
        assertTrue(result.getConsistencyIssues().isEmpty());
    }

    @Test
    void testProcessReverseSync_DataConsistencyIssues() throws Exception {
        // Arrange
        Patient patient = createTestPatient();
        UCSClient ucsClient = createTestUCSClient();
        
        // Create mismatch - different first name
        ucsClient.getDemographics().setFirstName("DifferentName");
        
        when(transformer.transformFHIRToUCS(any())).thenReturn(ucsClient);
        when(ucsApiClient.getClient(anyString())).thenThrow(new MediatorException(
            "Not found", "UCS_MEDIATOR", "GET_CLIENT", 404
        ));
        when(ucsApiClient.createClient(any())).thenReturn(ucsClient);

        // Act
        ReverseSyncFlowService.ReverseSyncFlowResult result = 
            reverseSyncFlowService.processReverseSync(patient);

        // Assert
        assertTrue(result.isSuccess());
        assertFalse(result.getConsistencyIssues().isEmpty());
        assertTrue(result.getConsistencyIssues().get(0).contains("First name mismatch"));
        
        verify(auditLogger).logError(
            eq("ReverseSyncFlowService"), eq("verifyDataConsistency"),
            eq("CONSISTENCY_WARNING"), anyString(), anyMap()
        );
    }

    @Test
    void testInitializeReverseSyncFlow() {
        // Act
        reverseSyncFlowService.initializeReverseSyncFlow();

        // Assert
        verify(changeDetectionService).registerChangeListener(
            eq("Patient"), any()
        );
    }

    // Helper methods

    private Patient createTestPatient() {
        Patient patient = new Patient();
        patient.setId("patient-123");
        
        // Add meta with timestamp
        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        patient.setMeta(meta);
        
        // Add identifiers
        Identifier opensrpId = new Identifier();
        opensrpId.setSystem("http://moh.go.tz/identifier/opensrp-id");
        opensrpId.setValue("opensrp-123");
        patient.addIdentifier(opensrpId);
        
        Identifier nationalId = new Identifier();
        nationalId.setSystem("http://moh.go.tz/identifier/national-id");
        nationalId.setValue("national-456");
        patient.addIdentifier(nationalId);
        
        // Add name
        HumanName name = new HumanName();
        name.addGiven("John");
        name.setFamily("Doe");
        patient.addName(name);
        
        // Add gender
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        
        // Add birth date
        patient.setBirthDate(new Date());
        
        return patient;
    }

    private UCSClient createTestUCSClient() {
        UCSClient client = new UCSClient();
        
        // Identifiers
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId("opensrp-123");
        identifiers.setNationalId("national-456");
        client.setIdentifiers(identifiers);
        
        // Demographics
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName("John");
        demographics.setLastName("Doe");
        demographics.setGender("M");
        demographics.setBirthDate(LocalDate.now());
        client.setDemographics(demographics);
        
        // Clinical data
        client.setClinicalData(new UCSClient.UCSClinicalData());
        
        // Metadata
        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata();
        metadata.setSource("FHIR");
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setUpdatedAt(LocalDateTime.now());
        client.setMetadata(metadata);
        
        return client;
    }
}
