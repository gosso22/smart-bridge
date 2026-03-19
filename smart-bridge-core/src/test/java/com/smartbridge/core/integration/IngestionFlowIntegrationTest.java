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

import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complete ingestion flow from UCS (OpenSRP) to FHIR.
 * Tests the end-to-end transformation pipeline WITHOUT any mocking framework.
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

    @Test
    void testCompleteIngestionFlow_Success() throws Exception {
        UCSClient ucsClient = createCompleteUCSClient();

        // Step 1: Validate
        UCSClientValidator.ValidationResult valResult = ucsValidator.validate(ucsClient);
        assertTrue(valResult.isValid(),
            "UCS validation should pass: " + valResult.getErrorMessage());

        // Step 2: Transform
        FHIRResourceWrapper<?> wrapper = ucsToFhirTransformer.transformUCSToFHIR(ucsClient);
        assertNotNull(wrapper);
        assertNotNull(wrapper.getResource());
        assertTrue(wrapper.getResource() instanceof Patient);

        // Step 3: Verify FHIR Patient content
        Patient patient = (Patient) wrapper.getResource();
        assertTrue(patient.hasIdentifier());
        assertTrue(patient.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()) &&
                          "opensrp-12345".equals(id.getValue())));

        assertTrue(patient.hasName());
        assertEquals("Smith", patient.getName().get(0).getFamily());
        assertEquals("John", patient.getName().get(0).getGiven().get(0).getValue());

        assertTrue(patient.hasGender());
        assertEquals("male", patient.getGender().toCode());

        assertTrue(patient.hasBirthDate());
        assertTrue(patient.hasAddress());

        // Step 4: Verify FHIR validation passes
        FHIRValidator.FHIRValidationResult fhirValResult = fhirValidator.validate(patient);
        assertTrue(fhirValResult.isValid(),
            "FHIR validation should pass: " + fhirValResult.getErrorMessage());

        // Step 5: Verify wrapper metadata
        assertEquals("UCS", wrapper.getSourceSystem());
        assertEquals("opensrp-12345", wrapper.getOriginalId());
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
    }

    @Test
    void testCompleteIngestionFlow_WithMultipleIdentifiers() throws Exception {
        UCSClient ucsClient = createCompleteUCSClient();

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

        UCSClientValidator.ValidationResult valResult = ucsValidator.validate(ucsClient);
        assertFalse(valResult.isValid(), "Validation should fail for invalid data");
        assertNotNull(valResult.getErrorMessage());

        assertThrows(TransformationException.class,
            () -> ucsToFhirTransformer.transformUCSToFHIR(ucsClient));
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
        assertEquals(originalClient.getOpensrpId(), roundTrippedClient.getOpensrpId(),
            "OpenSRP ID should survive round trip");
        assertEquals(originalClient.getFirstName(), roundTrippedClient.getFirstName(),
            "First name should survive round trip");
        assertEquals(originalClient.getLastName(), roundTrippedClient.getLastName(),
            "Last name should survive round trip");
    }

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
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
        assertEquals("stub-patient-id", result.getFhirResourceId());
        assertTrue(result.getDurationMs() < 5000);
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

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
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

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ===== Helper methods — flat OpenSRP structure =====

    private UCSClient createCompleteUCSClient() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("opensrp-12345");
        client.setType("Client");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "opensrp-12345");
        client.setIdentifiers(identifiers);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("national_id", "national-67890");
        client.setAttributes(attributes);

        client.setFirstName("John");
        client.setLastName("Smith");
        client.setGender("Male");
        client.setBirthdate("1990-05-15");

        UCSClient.OpenSRPAddress address = new UCSClient.OpenSRPAddress();
        address.setCountry("Tanzania");
        address.setStateProvince("Dar es Salaam");
        address.setCountyDistrict("Ilala");
        address.setCityVillage("Kinondoni");
        address.setTown("Mwenge");
        client.setAddresses(List.of(address));

        client.setServerVersion(1567890123456L);

        return client;
    }

    private UCSClient createUCSClientWithDemographics() {
        UCSClient client = new UCSClient();
        client.setBaseEntityId("opensrp-demo-001");

        Map<String, String> identifiers = new HashMap<>();
        identifiers.put("opensrp_id", "opensrp-demo-001");
        client.setIdentifiers(identifiers);

        client.setFirstName("Jane");
        client.setLastName("Doe");
        client.setGender("Female");
        client.setBirthdate("1992-03-20");

        UCSClient.OpenSRPAddress address = new UCSClient.OpenSRPAddress();
        address.setCountyDistrict("Arusha");
        address.setCityVillage("Central");
        address.setTown("Kaloleni");
        client.setAddresses(List.of(address));

        return client;
    }

    private UCSClient createInvalidUCSClient() {
        UCSClient client = new UCSClient();
        // Missing required fields: baseEntityId, firstName, lastName, gender, identifiers
        client.setIdentifiers(new HashMap<>());
        return client;
    }

    // ===== Manual test doubles (no Mockito) =====

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

    private static class FailingResilientFHIRClient extends ResilientFHIRClient {
        FailingResilientFHIRClient(FHIRClientService delegate) {
            super(delegate, new CircuitBreaker("test"), new RetryPolicy("test"));
        }

        @Override
        public MethodOutcome createPatient(Patient patient) throws Exception {
            throw new Exception("FHIR server unavailable: connection refused");
        }
    }

    private static class StubMessageProducer extends MessageProducerService {
        StubMessageProducer() {
            super(null);
        }

        @Override
        public void sendMessage(QueueMessage queueMessage) {}

        @Override
        public void sendToRetryQueue(QueueMessage queueMessage) {}

        @Override
        public void sendToDeadLetterQueue(QueueMessage queueMessage) {}
    }
}
