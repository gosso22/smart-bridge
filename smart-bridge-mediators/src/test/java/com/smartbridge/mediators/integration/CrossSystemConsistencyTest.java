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

import java.time.LocalDate;
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
        assertEquals(originalClient.getIdentifiers().getOpensrpId(),
            resultClient.getIdentifiers().getOpensrpId(),
            "OpenSRP ID should be consistent");

        if (originalClient.getIdentifiers().getNationalId() != null) {
            assertEquals(originalClient.getIdentifiers().getNationalId(),
                resultClient.getIdentifiers().getNationalId(),
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

        assertEquals(originalClient.getDemographics().getFirstName(),
            resultClient.getDemographics().getFirstName(), "First name should be consistent");
        assertEquals(originalClient.getDemographics().getLastName(),
            resultClient.getDemographics().getLastName(), "Last name should be consistent");
        assertEquals(originalClient.getDemographics().getGender(),
            resultClient.getDemographics().getGender(), "Gender should be consistent");
        assertEquals(originalClient.getDemographics().getBirthDate(),
            resultClient.getDemographics().getBirthDate(), "Birth date should be consistent");
    }

    @Test
    void testBidirectionalSync_UCSToFHIRToUCS_GenderNormalization() throws Exception {
        String[] genders = {"M", "F", "O"};

        for (String gender : genders) {
            // Reset captured patient
            CapturingResilientFHIRClient.lastPatient = null;

            UCSClient originalClient = createUCSClient("opensrp-gender-" + gender);
            originalClient.getDemographics().setGender(gender);

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

            assertEquals(gender, resultClient.getDemographics().getGender(),
                "Gender " + gender + " should be consistent after round-trip");
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
        assertNotNull(resultClient.getDemographics(), "Demographics should not be null");
        assertNotNull(resultClient.getIdentifiers().getOpensrpId(), "OpenSRP ID should not be null");
        assertNotNull(resultClient.getDemographics().getFirstName(), "First name should not be null");
        assertNotNull(resultClient.getDemographics().getLastName(), "Last name should not be null");
        assertNotNull(resultClient.getDemographics().getGender(), "Gender should not be null");
        assertNotNull(resultClient.getDemographics().getBirthDate(), "Birth date should not be null");
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

    private UCSClient createUCSClient(String opensrpId) {
        UCSClient client = new UCSClient();
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId(opensrpId);
        identifiers.setNationalId("national-" + opensrpId);
        client.setIdentifiers(identifiers);

        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName("Test");
        demographics.setLastName("User");
        demographics.setGender("M");
        demographics.setBirthDate(LocalDate.of(1990, 1, 1));
        client.setDemographics(demographics);

        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata();
        metadata.setSource("UCS");
        client.setMetadata(metadata);
        return client;
    }

    private UCSClient createUCSClientWithDemographics(String opensrpId) {
        UCSClient client = createUCSClient(opensrpId);
        client.getDemographics().setFirstName("Jane");
        client.getDemographics().setLastName("Doe");
        client.getDemographics().setGender("F");
        client.getDemographics().setBirthDate(LocalDate.of(1992, 3, 20));
        return client;
    }

    private UCSClient createCompleteUCSClient(String opensrpId) {
        UCSClient client = createUCSClient(opensrpId);
        client.getDemographics().setFirstName("Complete");
        client.getDemographics().setLastName("Client");
        client.getDemographics().setGender("M");
        client.getDemographics().setBirthDate(LocalDate.of(1985, 6, 15));
        UCSClient.UCSAddress address = new UCSClient.UCSAddress();
        address.setDistrict("Dar es Salaam");
        address.setWard("Kinondoni");
        address.setVillage("Mwenge");
        client.getDemographics().setAddress(address);
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
