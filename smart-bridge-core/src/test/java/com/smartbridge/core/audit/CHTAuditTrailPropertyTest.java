package com.smartbridge.core.audit;

import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.transformation.CHTToFHIRPersonTransformer;
import com.smartbridge.core.transformation.CHTToFHIRPlaceTransformer;
import com.smartbridge.core.transformation.CHTToFHIRReportTransformer;
import com.smartbridge.core.validation.CHTContactValidator;
import com.smartbridge.core.validation.CHTReportValidator;
import net.jqwik.api.*;
import org.hl7.fhir.r4.model.Patient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CHT audit trail completeness.
 * Feature: cht-integration, Property 10: CHT Audit Trail Completeness
 *
 * For any CHT operation, audit log entry contains: source, target, operation,
 * source ID, target ID, success/failure, timestamp.
 * Validates: Requirements 6.6, 7.5, 10.6
 */
class CHTAuditTrailPropertyTest {

    // ==================== Transformation Audit Always Contains Required Fields ====================

    @Property(tries = 50)
    void auditLogTransformationAlwaysContainsRequiredFields(
            @ForAll("sourceSystem") String source,
            @ForAll("targetSystem") String target,
            @ForAll("operation") String operation,
            @ForAll("resourceId") String sourceId,
            @ForAll("resourceId") String targetId,
            @ForAll boolean success) {

        RecordingAuditLogger recorder = new RecordingAuditLogger();

        recorder.logTransformation(source, target, operation, sourceId, targetId, success, "test details");

        assertEquals(1, recorder.getTransformationCount(),
            "Exactly one audit entry should be recorded");

        RecordingAuditLogger.TransformationEntry entry = recorder.getLastTransformation();
        assertNotNull(entry);
        assertEquals(source, entry.sourceSystem, "Source system must be present");
        assertEquals(target, entry.targetSystem, "Target system must be present");
        assertEquals(operation, entry.operation, "Operation must be present");
        assertEquals(sourceId, entry.sourceId, "Source ID must be present");
        assertEquals(targetId, entry.targetId, "Target ID must be present");
        assertEquals(success, entry.success, "Success/failure must be present");
        assertNotNull(entry.timestamp, "Timestamp must be present");
    }

    // ==================== Successful Ingestion Always Produces Audit Entry ====================

    @Property(tries = 50)
    void successfulContactIngestionAlwaysProducesAuditEntry(
            @ForAll("validContact") CHTContact contact) {

        RecordingAuditLogger recorder = new RecordingAuditLogger();
        CHTIngestionFlowService flowService = createFlowServiceWithAudit(recorder);

        IngestionFlowService.IngestionFlowResult result =
            flowService.processContactIngestion(contact);

        // Regardless of success, there should be at least one audit entry
        assertTrue(recorder.getTransformationCount() > 0,
            "Contact ingestion must produce at least one audit entry");

        RecordingAuditLogger.TransformationEntry entry = recorder.getLastTransformation();
        assertNotNull(entry.sourceSystem, "Source system must not be null");
        assertNotNull(entry.targetSystem, "Target system must not be null");
        assertNotNull(entry.operation, "Operation must not be null");
    }

    // ==================== Failed Operations Always Include Error Context ====================

    @Property(tries = 50)
    void failedIngestionAlwaysProducesAuditWithSourceId(
            @ForAll("invalidContact") CHTContact contact) {

        RecordingAuditLogger recorder = new RecordingAuditLogger();
        CHTIngestionFlowService flowService = createFlowServiceWithAudit(recorder);

        IngestionFlowService.IngestionFlowResult result =
            flowService.processContactIngestion(contact);

        assertFalse(result.isSuccess());

        // Failed operations must also have audit entries
        assertTrue(recorder.getTransformationCount() > 0,
            "Failed ingestion must produce audit entry");

        RecordingAuditLogger.TransformationEntry entry = recorder.getLastTransformation();
        assertFalse(entry.success, "Audit entry for failure must have success=false");
        // sourceId may be "null" (string) for contacts with null _id, but it must be recorded
        assertNotNull(entry.sourceId, "Audit entry must record source ID field");
    }

    // ==================== Error Audit Always Contains Error Details ====================

    @Property(tries = 20)
    void errorAuditAlwaysContainsRequiredFields(
            @ForAll("component") String component,
            @ForAll("operation") String operation,
            @ForAll("errorCode") String errorCode,
            @ForAll("errorMessage") String errorMessage) {

        RecordingAuditLogger recorder = new RecordingAuditLogger();

        Map<String, String> context = new LinkedHashMap<>();
        context.put("transactionId", "tx-123");
        context.put("resourceId", "res-456");

        recorder.logError(component, operation, errorCode, errorMessage, context);

        assertEquals(1, recorder.getErrorCount());
        RecordingAuditLogger.ErrorEntry entry = recorder.getLastError();
        assertEquals(component, entry.component);
        assertEquals(operation, entry.operation);
        assertEquals(errorCode, entry.errorCode);
        assertEquals(errorMessage, entry.errorMessage);
        assertNotNull(entry.timestamp);
    }

    // ==================== Helpers ====================

    private CHTIngestionFlowService createFlowServiceWithAudit(RecordingAuditLogger auditLogger) {
        return new CHTIngestionFlowService(
            new CHTContactValidator(),
            new CHTReportValidator(),
            new CHTToFHIRPersonTransformer(),
            new CHTToFHIRPlaceTransformer(),
            new CHTToFHIRReportTransformer(),
            new StubResilientFHIRClient(),
            new StubFHIRClientService(),
            new StubMessageProducer(),
            auditLogger,
            Runnable::run);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CHTContact> validContact() {
        return Combinators.combine(
            Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('-').ofMinLength(5).ofMaxLength(36),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(30),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(30)
        ).as((id, given, family) -> {
            CHTContact c = new CHTContact();
            c.setId(id);
            c.setName(given + " " + family);
            c.setType("person");
            return c;
        });
    }

    @Provide
    Arbitrary<CHTContact> invalidContact() {
        return Arbitraries.of(
            createContactMissingName(),
            createContactMissingType()
        );
    }

    @Provide
    Arbitrary<String> sourceSystem() {
        return Arbitraries.of("CHT", "FHIR", "UCS");
    }

    @Provide
    Arbitrary<String> targetSystem() {
        return Arbitraries.of("FHIR", "CHT", "UCS");
    }

    @Provide
    Arbitrary<String> operation() {
        return Arbitraries.of("CONTACT_INGESTION", "PLACE_INGESTION", "REPORT_INGESTION", "REVERSE_SYNC");
    }

    @Provide
    Arbitrary<String> resourceId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(36);
    }

    @Provide
    Arbitrary<String> component() {
        return Arbitraries.of("CHTIngestionFlowService", "CHTReverseSyncFlowService", "CHTSyncService");
    }

    @Provide
    Arbitrary<String> errorCode() {
        return Arbitraries.of("VALIDATION_FAILED", "TRANSFORMATION_ERROR", "FHIR_STORAGE_FAILED", "CHT_API_ERROR");
    }

    @Provide
    Arbitrary<String> errorMessage() {
        return Arbitraries.of("Validation failed", "Transformation error", "Connection refused", "Timeout");
    }

    private CHTContact createContactMissingName() {
        CHTContact c = new CHTContact();
        c.setId("test-id-no-name");
        c.setType("person");
        // name is null → validation fails
        return c;
    }

    private CHTContact createContactMissingType() {
        CHTContact c = new CHTContact();
        c.setId("test-id-no-type");
        c.setName("Test Name");
        // type is null → validation fails
        return c;
    }

    // ==================== Recording Audit Logger ====================

    static class RecordingAuditLogger extends AuditLogger {
        private final java.util.List<TransformationEntry> transformations = new java.util.ArrayList<>();
        private final java.util.List<ErrorEntry> errors = new java.util.ArrayList<>();

        @Override
        public void logTransformation(String sourceSystem, String targetSystem, String operation,
                                      String sourceId, String targetId, boolean success, String details) {
            transformations.add(new TransformationEntry(
                sourceSystem, targetSystem, operation, sourceId, targetId, success,
                java.time.LocalDateTime.now()));
            super.logTransformation(sourceSystem, targetSystem, operation, sourceId, targetId, success, details);
        }

        @Override
        public void logError(String component, String operation, String errorCode,
                             String errorMessage, Map<String, String> context) {
            errors.add(new ErrorEntry(component, operation, errorCode, errorMessage,
                java.time.LocalDateTime.now()));
            super.logError(component, operation, errorCode, errorMessage, context);
        }

        int getTransformationCount() { return transformations.size(); }
        TransformationEntry getLastTransformation() {
            return transformations.isEmpty() ? null : transformations.get(transformations.size() - 1);
        }
        int getErrorCount() { return errors.size(); }
        ErrorEntry getLastError() {
            return errors.isEmpty() ? null : errors.get(errors.size() - 1);
        }

        static class TransformationEntry {
            final String sourceSystem, targetSystem, operation, sourceId, targetId;
            final boolean success;
            final java.time.LocalDateTime timestamp;

            TransformationEntry(String sourceSystem, String targetSystem, String operation,
                                String sourceId, String targetId, boolean success,
                                java.time.LocalDateTime timestamp) {
                this.sourceSystem = sourceSystem;
                this.targetSystem = targetSystem;
                this.operation = operation;
                this.sourceId = sourceId;
                this.targetId = targetId;
                this.success = success;
                this.timestamp = timestamp;
            }
        }

        static class ErrorEntry {
            final String component, operation, errorCode, errorMessage;
            final java.time.LocalDateTime timestamp;

            ErrorEntry(String component, String operation, String errorCode,
                       String errorMessage, java.time.LocalDateTime timestamp) {
                this.component = component;
                this.operation = operation;
                this.errorCode = errorCode;
                this.errorMessage = errorMessage;
                this.timestamp = timestamp;
            }
        }
    }

    // ==================== Stub Test Doubles ====================

    private static class StubFHIRClientService extends com.smartbridge.core.client.FHIRClientService {
        @Override
        public ca.uhn.fhir.rest.api.MethodOutcome createPatient(Patient patient) {
            ca.uhn.fhir.rest.api.MethodOutcome outcome = new ca.uhn.fhir.rest.api.MethodOutcome();
            outcome.setId(new org.hl7.fhir.r4.model.IdType("Patient", "stub-1"));
            return outcome;
        }

        @Override
        public String getServerBaseUrl() { return "http://localhost:8080/fhir"; }
    }

    private static class StubResilientFHIRClient extends com.smartbridge.core.resilience.ResilientFHIRClient {
        StubResilientFHIRClient() {
            super(new StubFHIRClientService(),
                new com.smartbridge.core.resilience.CircuitBreaker("test"),
                new com.smartbridge.core.resilience.RetryPolicy("test"));
        }

        @Override
        public ca.uhn.fhir.rest.api.MethodOutcome createPatient(Patient patient) throws Exception {
            ca.uhn.fhir.rest.api.MethodOutcome outcome = new ca.uhn.fhir.rest.api.MethodOutcome();
            outcome.setId(new org.hl7.fhir.r4.model.IdType("Patient", "stub-1"));
            return outcome;
        }
    }

    private static class StubMessageProducer extends com.smartbridge.core.queue.MessageProducerService {
        StubMessageProducer() { super(null); }
        @Override public void sendMessage(com.smartbridge.core.queue.QueueMessage q) {}
        @Override public void sendToRetryQueue(com.smartbridge.core.queue.QueueMessage q) {}
        @Override public void sendToDeadLetterQueue(com.smartbridge.core.queue.QueueMessage q) {}
    }
}
