package com.smartbridge.core.integration;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
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
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complete ingestion flow from UCS to FHIR.
 * Tests the end-to-end transformation pipeline WITHOUT any mocking framework
 * to avoid Mockito inline mock maker interference with real transformer instances.
 *
 * Requirements: 3.1, 7.1
 */
class IngestionFlowIntegrationTest {

    private UCSClientValidator ucsValidator;
    private FHIRValidator fhirValidator;
    private UCSToFHIRTransformer ucsToFhirTransformer;
    private FHIRToUCSTransformer fhirToUcsTransformer;

    @BeforeEach
    void setUp() {
        ucsValidator = new UCSClientValidator();
        fhirValidator = new FHIRValidator();
        fhirToUcsTransformer = new FHIRToUCSTransformer(ucsValidator);
        ucsToFhirTransformer = new UCSToFHIRTransformer(ucsValidator, fhirValidator, fhirToUcsTransformer);
    }

    // ===== End-to-end transformation pipeline tests =====

    @Test
    void testCompleteIngestionFlow_Success() throws Exception {
        UCSClient ucsClient = createCompleteUCSClient();

        // Step 1: Validate
        UCSClientValidator.ValidationResult valResult = ucsValidator.validate(ucsClient);
        assertTrue(valResult.isValid(),
            "UCS validation should pass: " + valResult.getErrorMessage());

        // Step 2: Transform
        FHIRResourceWrapper<?> wrapper = ucsToFhirTransformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper, "Transformation should produce a wrapper");
        assertNotNull(wrapper.getResource(), "Wrapper should contain a resource");
        assertTrue(wrapper.getResource() instanceof Patient, "Resource should be a Patient");

        // Step 3: Verify FHIR Patient content
        Patient patient = (Patient) wrapper.getResource();
        assertTrue(patient.hasIdentifier(), "Patient should have identifiers");
        assertTrue(patient.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()) &&
                          "opensrp-12345".equals(id.getValue())),
            "Patient should have OpenSRP identifier");

        assertTrue(patient.hasName(), "Patient should have a name");
        assertEquals("Smith", patient.getName().get(0).getFamily());
        assertEquals("John", patient.getName().get(0).getGiven().get(0).getValue());

        assertTrue(patient.hasGender(), "Patient should have gender");
        assertEquals("male", patient.getGender().toCode());

        assertTrue(patient.hasBirthDate(), "Patient should have birth date");
        assertTrue(patient.hasAddress(), "Patient should have address");

        // Step 4: Verify FHIR validation passes
        FHIRValidator.FHIRValidationResult fhirValResult = fhirValidator.validate(patient);
        assertTrue(fhirValResult.isValid(),
            "FHIR validation should pass: " + fhirValResult.getErrorMessage());

        // Step 5: Verify wrapper metadata
        assertEquals("UCS", wrapper.getSourceSystem());
        assertEquals("opensrp-12345", wrapper.getOriginalId());
        assertNotNull(wrapper.getTransformedAt());
    }

    @Test
    void testCompleteIngestionFlow_WithDemographicData() throws Exception {
        UCSClient ucsClient = createUCSClientWithDemographics();

        FHIRResourceWrapper<?> wrapper = ucsToFhirTransformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper);
        Patient patient = (Patient) wrapper.getResource();

        assertEquals("Doe", patient.getName().get(0).getFamily());
        assertEquals("Jane", patient.getName().get(0).getGiven().get(0).getValue());
        assertEquals("female", patient.getGender().toCode());
        assertTrue(patient.hasBirthDate());
        assertTrue(patient.hasAddress());
        assertEquals("Arusha", patient.getAddress().get(0).getDistrict());
        assertEquals("Central", patient.getAddress().get(0).getCity());
        assertEquals("Kaloleni", patient.getAddress().get(0).getText());
    }

    @Test
    void testCompleteIngestionFlow_WithMultipleIdentifiers() throws Exception {
        UCSClient ucsClient = createUCSClientWithMultipleIdentifiers();

        FHIRResourceWrapper<?> wrapper = ucsToFhirTransformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper);
        Patient patient = (Patient) wrapper.getResource();

        long opensrpIdCount = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()))
            .count();
        long nationalIdCount = patient.getIdentifier().stream()
            .filter(id -> "http://moh.go.tz/identifier/national-id".equals(id.getSystem()))
            .count();
        assertEquals(1, opensrpIdCount, "Should have one OpenSRP ID");
        assertEquals(1, nationalIdCount, "Should have one National ID");
    }

    @Test
    void testCompleteIngestionFlow_ValidationFailure() {
        UCSClient ucsClient = createInvalidUCSClient();

        // Validation should fail
        UCSClientValidator.ValidationResult valResult = ucsValidator.validate(ucsClient);
        assertFalse(valResult.isValid(), "Validation should fail for invalid data");
        assertNotNull(valResult.getErrorMessage());

        // Transformation should throw
        assertThrows(TransformationException.class,
            () -> ucsToFhirTransformer.transformUCSToFHIR(ucsClient),
            "Transformation should fail for invalid data");
    }

    @Test
    void testCompleteIngestionFlow_PerformanceRequirement() throws Exception {
        UCSClient ucsClient = createCompleteUCSClient();

        long startTime = System.currentTimeMillis();

        UCSClientValidator.ValidationResult valResult = ucsValidator.validate(ucsClient);
        assertTrue(valResult.isValid());

        FHIRResourceWrapper<?> wrapper = ucsToFhirTransformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper);

        FHIRValidator.FHIRValidationResult fhirResult = fhirValidator.validate(wrapper.getResource());
        assertTrue(fhirResult.isValid());

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 5000,
            "Complete flow should finish within 5 seconds (actual: " + duration + "ms)");
    }

    // ===== Round-trip transformation consistency tests =====

    @Test
    void testRoundTripTransformation_UCSToFHIRAndBack() throws Exception {
        UCSClient originalClient = createCompleteUCSClient();

        // UCS -> FHIR
        FHIRResourceWrapper<?> fhirWrapper = ucsToFhirTransformer.transformUCSToFHIR(originalClient);
        assertNotNull(fhirWrapper);

        // FHIR -> UCS
        UCSClient roundTrippedClient = ucsToFhirTransformer.transformFHIRToUCS(fhirWrapper);
        assertNotNull(roundTrippedClient, "Round-trip should produce a UCS client");

        // Verify key fields survived the round trip
        assertEquals(originalClient.getIdentifiers().getOpensrpId(),
            roundTrippedClient.getIdentifiers().getOpensrpId(),
            "OpenSRP ID should survive round trip");
        assertEquals(originalClient.getDemographics().getFirstName(),
            roundTrippedClient.getDemographics().getFirstName(),
            "First name should survive round trip");
        assertEquals(originalClient.getDemographics().getLastName(),
            roundTrippedClient.getDemographics().getLastName(),
            "Last name should survive round trip");
    }

    // ===== Full IngestionFlowService tests using manual test doubles =====

    @Test
    void testIngestionFlowService_SuccessfulIngestion() throws Exception {
        StubFHIRClientService stubFhirClient = new StubFHIRClientService();
        StubResilientFHIRClient stubResilient = new StubResilientFHIRClient(stubFhirClient);
        StubMessageProducer stubProducer = new StubMessageProducer();
        AuditLogger auditLogger = new AuditLogger();
        ConcurrentTransformationService concurrentService = new ConcurrentTransformationService(
            ucsToFhirTransformer, fhirToUcsTransformer, Runnable::run);
        Executor syncExecutor = Runnable::run;

        IngestionFlowService flowService = new IngestionFlowService(
            ucsValidator, ucsToFhirTransformer, stubResilient, stubFhirClient,
            stubProducer, auditLogger, concurrentService, syncExecutor);

        UCSClient ucsClient = createCompleteUCSClient();
        IngestionFlowService.IngestionFlowResult result = flowService.processIngestion(ucsClient);

        assertTrue(result.isSuccess(), "Ingestion should succeed: " + result.getErrorMessage());
        assertTrue(result.isValidationPassed(), "Validation should pass");
        assertTrue(result.isTransformationCompleted(), "Transformation should complete");
        assertTrue(result.isFhirStorageCompleted(), "FHIR storage should complete");
        assertNotNull(result.getFhirResourceId(), "Should have a FHIR resource ID");
        assertEquals("stub-patient-id", result.getFhirResourceId());
        assertTrue(result.getDurationMs() < 5000, "Should complete within 5 seconds");
    }

    @Test
    void testIngestionFlowService_FHIRServerUnavailable() throws Exception {
        StubFHIRClientService stubFhirClient = new StubFHIRClientService();
        FailingResilientFHIRClient failingResilient = new FailingResilientFHIRClient(stubFhirClient);
        StubMessageProducer stubProducer = new StubMessageProducer();
        AuditLogger auditLogger = new AuditLogger();
        ConcurrentTransformationService concurrentService = new ConcurrentTransformationService(
            ucsToFhirTransformer, fhirToUcsTransformer, Runnable::run);
        Executor syncExecutor = Runnable::run;

        IngestionFlowService flowService = new IngestionFlowService(
            ucsValidator, ucsToFhirTransformer, failingResilient, stubFhirClient,
            stubProducer, auditLogger, concurrentService, syncExecutor);

        UCSClient ucsClient = createCompleteUCSClient();
        IngestionFlowService.IngestionFlowResult result = flowService.processIngestion(ucsClient);

        assertFalse(result.isSuccess(), "Ingestion should fail when FHIR server is unavailable");
        assertNotNull(result.getErrorMessage(), "Should have an error message");
        assertTrue(result.getErrorMessage().contains("FHIR"),
            "Error should mention FHIR: " + result.getErrorMessage());
    }

    @Test
    void testIngestionFlowService_TransactionRollback() throws Exception {
        StubFHIRClientService stubFhirClient = new StubFHIRClientService();
        FailingResilientFHIRClient failingResilient = new FailingResilientFHIRClient(stubFhirClient);
        StubMessageProducer stubProducer = new StubMessageProducer();
        AuditLogger auditLogger = new AuditLogger();
        ConcurrentTransformationService concurrentService = new ConcurrentTransformationService(
            ucsToFhirTransformer, fhirToUcsTransformer, Runnable::run);
        Executor syncExecutor = Runnable::run;

        IngestionFlowService flowService = new IngestionFlowService(
            ucsValidator, ucsToFhirTransformer, failingResilient, stubFhirClient,
            stubProducer, auditLogger, concurrentService, syncExecutor);

        UCSClient ucsClient = createCompleteUCSClient();
        IngestionFlowService.IngestionFlowResult result = flowService.processIngestionWithTransaction(ucsClient);

        assertFalse(result.isSuccess(), "Transaction should fail and trigger rollback");
        assertNotNull(result.getErrorMessage());
    }

    // ===== Helper methods for creating UCS clients =====

    private UCSClient createCompleteUCSClient() {
        UCSClient client = new UCSClient();

        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId("opensrp-12345");
        identifiers.setNationalId("national-67890");
        client.setIdentifiers(identifiers);

        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName("John");
        demographics.setLastName("Smith");
        demographics.setGender("M");
        demographics.setBirthDate(LocalDate.of(1990, 5, 15));
        client.setDemographics(demographics);

        UCSClient.UCSAddress address = new UCSClient.UCSAddress();
        address.setDistrict("Dar es Salaam");
        address.setWard("Kinondoni");
        address.setVillage("Mwenge");
        demographics.setAddress(address);

        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata();
        metadata.setSource("UCS");
        client.setMetadata(metadata);

        return client;
    }

    private UCSClient createUCSClientWithDemographics() {
        UCSClient client = new UCSClient();

        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();
        identifiers.setOpensrpId("opensrp-demo-001");
        client.setIdentifiers(identifiers);

        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();
        demographics.setFirstName("Jane");
        demographics.setLastName("Doe");
        demographics.setGender("F");
        demographics.setBirthDate(LocalDate.of(1992, 3, 20));
        client.setDemographics(demographics);

        UCSClient.UCSAddress address = new UCSClient.UCSAddress();
        address.setDistrict("Arusha");
        address.setWard("Central");
        address.setVillage("Kaloleni");
        demographics.setAddress(address);

        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata();
        metadata.setSource("UCS");
        client.setMetadata(metadata);

        return client;
    }

    private UCSClient createUCSClientWithMultipleIdentifiers() {
        UCSClient client = createCompleteUCSClient();
        client.getIdentifiers().setOpensrpId("opensrp-multi-001");
        client.getIdentifiers().setNationalId("TZ-NAT-12345");
        return client;
    }

    private UCSClient createInvalidUCSClient() {
        UCSClient client = new UCSClient();
        client.setIdentifiers(new UCSClient.UCSIdentifiers());
        client.setDemographics(new UCSClient.UCSDemographics());
        return client;
    }

    // ===== Manual test doubles (no Mockito) =====

    /** Stub FHIRClientService that returns a successful outcome. */
    private static class StubFHIRClientService extends FHIRClientService {
        @Override
        public MethodOutcome createPatient(Patient patient) {
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Patient", "stub-patient-id"));
            return outcome;
        }

        @Override
        public String getServerBaseUrl() {
            return "http://localhost:8080/fhir";
        }
    }

    /** Stub ResilientFHIRClient that delegates to StubFHIRClientService directly. */
    private static class StubResilientFHIRClient extends ResilientFHIRClient {
        private final FHIRClientService delegate;

        StubResilientFHIRClient(FHIRClientService delegate) {
            super(delegate, new CircuitBreaker("test"), new RetryPolicy("test"));
            this.delegate = delegate;
        }

        @Override
        public MethodOutcome createPatient(Patient patient) throws Exception {
            return delegate.createPatient(patient);
        }
    }

    /** ResilientFHIRClient that always throws to simulate FHIR server unavailability. */
    private static class FailingResilientFHIRClient extends ResilientFHIRClient {
        FailingResilientFHIRClient(FHIRClientService delegate) {
            super(delegate, new CircuitBreaker("test"), new RetryPolicy("test"));
        }

        @Override
        public MethodOutcome createPatient(Patient patient) throws Exception {
            throw new Exception("FHIR server unavailable: connection refused");
        }
    }

    /** Stub MessageProducerService that does nothing. */
    private static class StubMessageProducer extends MessageProducerService {
        StubMessageProducer() {
            super(null);
        }

        @Override
        public void sendMessage(QueueMessage queueMessage) {
            // no-op
        }

        @Override
        public void sendToRetryQueue(QueueMessage queueMessage) {
            // no-op
        }

        @Override
        public void sendToDeadLetterQueue(QueueMessage queueMessage) {
            // no-op
        }
    }
}
