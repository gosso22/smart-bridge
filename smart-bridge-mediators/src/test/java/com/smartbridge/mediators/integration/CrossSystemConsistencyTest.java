package com.smartbridge.mediators.integration;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.queue.QueueMessage;
import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.resilience.RetryPolicy;
import com.smartbridge.core.transformation.ConcurrentTransformationService;
import com.smartbridge.core.transformation.FHIRToUCSTransformer;
import com.smartbridge.core.transformation.UCSToFHIRTransformer;
import com.smartbridge.core.validation.FHIRValidator;
import com.smartbridge.core.validation.UCSClientValidator;
import com.smartbridge.mediators.flow.ReverseSyncFlowService;
import com.smartbridge.mediators.ucs.UCSApiClient;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for cross-system consistency verification.
 * Tests bidirectional data synchronization and consistency between UCS and FHIR systems.
 * Uses manual test doubles for IngestionFlowService dependencies to avoid Mockito
 * inline agent interference with real transformer instances.
 * Mockito is used only for UCSApiClient (ReverseSyncFlowService) where ArgumentCaptor is needed.
 *
 * Requirements: 3.2, 3.5, All requirements integration
 */
@ExtendWith(MockitoExtension.class)
class CrossSystemConsistencyTest {

    @Mock
    private UCSApiClient ucsApiClient;

    private UCSClientValidator ucsValidator;
    private FHIRValidator fhirValidator;
    private UCSToFHIRTransformer ucsToFhirTransformer;
    private FHIRToUCSTransformer fhirToUcsTransformer;
    private IngestionFlowService ingestionFlowService;
    private ReverseSyncFlowService reverseSyncFlowService;

    @BeforeEach
    void setUp() {
        ucsValidator = new UCSClientValidator();
        fhirValidator = new FHIRValidator();
        fhirToUcsTransformer = new FHIRToUCSTransformer(ucsValidator);
        ucsToFhirTransformer = new UCSToFHIRTransformer(ucsValidator, fhirValidator, fhirToUcsTransformer);
        Executor syncExecutor = Runnable::run;

        // Manual test doubles for IngestionFlowService (no Mockito to avoid interference)
        CapturingFHIRClientService stubFhirClient = new CapturingFHIRClientService();
        CapturingResilientFHIRClient stubResilient = new CapturingResilientFHIRClient(stubFhirClient);
        StubMessageProducer stubProducer = new StubMessageProducer();
        AuditLogger auditLogger = new AuditLogger();
        ConcurrentTransformationService concurrentService = new ConcurrentTransformationService(
            ucsToFhirTransformer, fhirToUcsTransformer, syncExecutor);

        ingestionFlowService = new IngestionFlowService(
            ucsValidator, ucsToFhirTransformer, stubResilient, stubFhirClient,
            stubProducer, auditLogger, concurrentService, syncExecutor);

        // ReverseSyncFlowService uses real FHIRToUCSTransformer + mocked UCSApiClient
        FHIRClientService reverseFhirClient = new StubFHIRClientServiceSimple();
        reverseSyncFlowService = new ReverseSyncFlowService(
            null, // changeDetectionService not needed for processReverseSync
            fhirToUcsTransformer,
            reverseFhirClient,
            ucsApiClient,
            auditLogger,
            syncExecutor);
    }

    @Test
    void testBidirectionalSync_UCSToFHIRToUCS_IdentifierConsistency() throws Exception {
        UCSClient originalClient = createUCSClient("opensrp-consistency-001");

        // Step 1: Ingest UCS to FHIR
        IngestionFlowService.IngestionFlowResult ingestionResult =
            ingestionFlowService.processIngestion(originalClient);
        assertTrue(ingestionResult.isSuccess(), "Ingestion should succeed: " + ingestionResult.getErrorMessage());

        // Get the captured FHIR Patient
        Patient createdPatient = CapturingResilientFHIRClient.lastPatient;
        assertNotNull(createdPatient, "Should have captured a Patient");

        // Step 2: Reverse sync FHIR back to UCS
        when(ucsApiClient.getClient("opensrp-consistency-001"))
            .thenThrow(new MediatorException("Client not found"));
        when(ucsApiClient.createClient(any(UCSClient.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReverseSyncFlowService.ReverseSyncFlowResult reverseSyncResult =
            reverseSyncFlowService.processReverseSync(createdPatient);
        assertTrue(reverseSyncResult.isSuccess(), "Reverse sync should succeed: " + reverseSyncResult.getErrorMessage());

        // Capture the UCS client that was created
        ArgumentCaptor<UCSClient> ucsClientCaptor = ArgumentCaptor.forClass(UCSClient.class);
        verify(ucsApiClient).createClient(ucsClientCaptor.capture());
        UCSClient resultClient = ucsClientCaptor.getValue();

        // Assert - Verify identifier consistency
        assertEquals(originalClient.getOpensrpId(),
            resultClient.getOpensrpId(),
            "OpenSRP ID should be consistent");

        if (originalClient.getNationalId() != null) {
            assertEquals(originalClient.getNationalId(),
                resultClient.getNationalId(),
                "National ID should be consistent");
        }
    }

    @Test
    void testBidirectionalSync_UCSToFHIRToUCS_DemographicConsistency() throws Exception {
        UCSClient originalClient = createUCSClientWithDemographics("opensrp-demo-002");

        IngestionFlowService.IngestionFlowResult ingestionResult =
            ingestionFlowService.processIngestion(originalClient);
        assertTrue(ingestionResult.isSuccess(), "Ingestion should succeed: " + ingestionResult.getErrorMessage());

        Patient createdPatient = CapturingResilientFHIRClient.lastPatient;

        when(ucsApiClient.getClient("opensrp-demo-002"))
            .thenThrow(new MediatorException("Client not found"));
        when(ucsApiClient.createClient(any(UCSClient.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReverseSyncFlowService.ReverseSyncFlowResult reverseSyncResult =
            reverseSyncFlowService.processReverseSync(createdPatient);
        assertTrue(reverseSyncResult.isSuccess());

        ArgumentCaptor<UCSClient> ucsClientCaptor = ArgumentCaptor.forClass(UCSClient.class);
        verify(ucsApiClient).createClient(ucsClientCaptor.capture());
        UCSClient resultClient = ucsClientCaptor.getValue();

        assertEquals(originalClient.getFirstName(),
            resultClient.getFirstName(), "First name should be consistent");
        assertEquals(originalClient.getLastName(),
            resultClient.getLastName(), "Last name should be consistent");
        // Gender is normalized during round-trip: "F" -> FHIR female -> "Female"
        assertTrue(isSameGender(originalClient.getGender(), resultClient.getGender()),
            "Gender should be consistent: original=" + originalClient.getGender() +
            ", result=" + resultClient.getGender());
        assertEquals(originalClient.getBirthdate(),
            resultClient.getBirthdate(), "Birth date should be consistent");
    }

    @Test
    void testBidirectionalSync_UCSToFHIRToUCS_GenderNormalization() throws Exception {
        String[] genders = {"M", "F", "O"};

        for (String gender : genders) {
            // Reset captured patient
            CapturingResilientFHIRClient.lastPatient = null;

            UCSClient originalClient = createUCSClient("opensrp-gender-" + gender);
            originalClient.setGender(gender);

            IngestionFlowService.IngestionFlowResult ingestionResult =
                ingestionFlowService.processIngestion(originalClient);
            assertTrue(ingestionResult.isSuccess(),
                "Ingestion should succeed for gender " + gender + ": " + ingestionResult.getErrorMessage());

            Patient createdPatient = CapturingResilientFHIRClient.lastPatient;

            when(ucsApiClient.getClient("opensrp-gender-" + gender))
                .thenThrow(new MediatorException("Client not found"));
            when(ucsApiClient.createClient(any(UCSClient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            ReverseSyncFlowService.ReverseSyncFlowResult reverseSyncResult =
                reverseSyncFlowService.processReverseSync(createdPatient);
            assertTrue(reverseSyncResult.isSuccess());

            ArgumentCaptor<UCSClient> ucsClientCaptor = ArgumentCaptor.forClass(UCSClient.class);
            verify(ucsApiClient, atLeastOnce()).createClient(ucsClientCaptor.capture());
            UCSClient resultClient = ucsClientCaptor.getValue();

            assertTrue(isSameGender(gender, resultClient.getGender()),
                "Gender " + gender + " should be consistent after round-trip, got: " +
                resultClient.getGender());
        }
    }

    @Test
    void testCrossSystemConsistency_DataIntegrity() throws Exception {
        UCSClient originalClient = createCompleteUCSClient("opensrp-integrity-005");

        IngestionFlowService.IngestionFlowResult ingestionResult =
            ingestionFlowService.processIngestion(originalClient);
        assertTrue(ingestionResult.isSuccess(), "Ingestion should succeed: " + ingestionResult.getErrorMessage());

        Patient createdPatient = CapturingResilientFHIRClient.lastPatient;

        when(ucsApiClient.getClient("opensrp-integrity-005"))
            .thenThrow(new MediatorException("Client not found"));
        when(ucsApiClient.createClient(any(UCSClient.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReverseSyncFlowService.ReverseSyncFlowResult reverseSyncResult =
            reverseSyncFlowService.processReverseSync(createdPatient);
        assertTrue(reverseSyncResult.isSuccess());

        ArgumentCaptor<UCSClient> ucsClientCaptor = ArgumentCaptor.forClass(UCSClient.class);
        verify(ucsApiClient).createClient(ucsClientCaptor.capture());
        UCSClient resultClient = ucsClientCaptor.getValue();

        assertNotNull(resultClient.getIdentifiers(), "Identifiers should not be null");
        assertNotNull(resultClient.getOpensrpId(), "OpenSRP ID should not be null");
        assertNotNull(resultClient.getFirstName(), "First name should not be null");
        assertNotNull(resultClient.getLastName(), "Last name should not be null");
        assertNotNull(resultClient.getGender(), "Gender should not be null");
        assertNotNull(resultClient.getBirthdate(), "Birth date should not be null");
    }

    @Test
    void testCrossSystemConsistency_PerformanceRequirement() throws Exception {
        UCSClient originalClient = createUCSClient("opensrp-perf-006");

        long startTime = System.currentTimeMillis();

        IngestionFlowService.IngestionFlowResult ingestionResult =
            ingestionFlowService.processIngestion(originalClient);
        assertTrue(ingestionResult.isSuccess(), "Ingestion should succeed: " + ingestionResult.getErrorMessage());

        Patient createdPatient = CapturingResilientFHIRClient.lastPatient;

        when(ucsApiClient.getClient("opensrp-perf-006"))
            .thenThrow(new MediatorException("Client not found"));
        when(ucsApiClient.createClient(any(UCSClient.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReverseSyncFlowService.ReverseSyncFlowResult reverseSyncResult =
            reverseSyncFlowService.processReverseSync(createdPatient);
        assertTrue(reverseSyncResult.isSuccess());

        long totalDuration = System.currentTimeMillis() - startTime;

        assertTrue(ingestionResult.getDurationMs() < 5000, "Ingestion should complete within 5 seconds");
        assertTrue(totalDuration < 10000, "Complete bidirectional sync should complete within 10 seconds");
    }

    // ===== Helper methods =====

    /**
     * Compare genders semantically: "M" == "Male", "F" == "Female", "O" == "Other".
     */
    private boolean isSameGender(String a, String b) {
        if (a == null || b == null) return a == b;
        return normalizeGenderForComparison(a).equals(normalizeGenderForComparison(b));
    }

    private String normalizeGenderForComparison(String gender) {
        if (gender == null) return "";
        switch (gender.toUpperCase()) {
            case "M": case "MALE": return "MALE";
            case "F": case "FEMALE": return "FEMALE";
            case "O": case "OTHER": return "OTHER";
            default: return gender.toUpperCase();
        }
    }

    private UCSClient createUCSClient(String opensrpId) {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("entity-" + opensrpId);

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", opensrpId);
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "national-" + opensrpId);
        client.setAttributes(attributes);

        client.setFirstName("Test");
        client.setLastName("User");
        client.setGender("M");
        client.setBirthdate("1990-01-01");

        return client;
    }

    private UCSClient createUCSClientWithDemographics(String opensrpId) {
        UCSClient client = createUCSClient(opensrpId);
        client.setFirstName("Jane");
        client.setLastName("Doe");
        client.setGender("F");
        client.setBirthdate("1992-03-20");
        return client;
    }

    private UCSClient createCompleteUCSClient(String opensrpId) {
        UCSClient client = createUCSClient(opensrpId);
        client.setFirstName("Complete");
        client.setLastName("Client");
        client.setGender("M");
        client.setBirthdate("1985-06-15");
        UCSClient.OpenSRPAddress address = new UCSClient.OpenSRPAddress();
        address.setCountyDistrict("Dar es Salaam");
        address.setCityVillage("Kinondoni");
        address.setTown("Mwenge");
        client.setAddresses(new ArrayList<>());
        client.getAddresses().add(address);
        return client;
    }

    // ===== Manual test doubles =====

    /** FHIRClientService stub with a no-arg constructor. */
    private static class StubFHIRClientServiceSimple extends FHIRClientService {
        @Override
        public String getServerBaseUrl() {
            return "http://localhost:8080/fhir";
        }
    }

    /** FHIRClientService that captures the created Patient and returns a stub outcome. */
    private static class CapturingFHIRClientService extends FHIRClientService {
        static Patient lastCreatedPatient;

        @Override
        public MethodOutcome createPatient(Patient patient) {
            lastCreatedPatient = patient;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Patient", "stub-patient-id"));
            return outcome;
        }

        @Override
        public String getServerBaseUrl() {
            return "http://localhost:8080/fhir";
        }
    }

    /** ResilientFHIRClient that captures the Patient and delegates to the stub. */
    private static class CapturingResilientFHIRClient extends ResilientFHIRClient {
        static Patient lastPatient;
        private final FHIRClientService delegate;

        CapturingResilientFHIRClient(FHIRClientService delegate) {
            super(delegate, new CircuitBreaker("test"), new RetryPolicy("test"));
            this.delegate = delegate;
        }

        @Override
        public MethodOutcome createPatient(Patient patient) throws Exception {
            // Set an ID on the patient so reverse sync can use it
            patient.setId("stub-patient-id");
            lastPatient = patient;
            return delegate.createPatient(patient);
        }
    }

    /** MessageProducerService stub that does nothing. */
    private static class StubMessageProducer extends MessageProducerService {
        StubMessageProducer() { super(null); }
        @Override public void sendMessage(QueueMessage q) {}
        @Override public void sendToRetryQueue(QueueMessage q) {}
        @Override public void sendToDeadLetterQueue(QueueMessage q) {}
    }
}
