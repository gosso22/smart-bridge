package com.smartbridge.core.flow;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.queue.QueueMessage;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.transformation.CHTToFHIRPersonTransformer;
import com.smartbridge.core.transformation.CHTToFHIRPlaceTransformer;
import com.smartbridge.core.transformation.CHTToFHIRReportTransformer;
import com.smartbridge.core.validation.CHTContactValidator;
import com.smartbridge.core.validation.CHTReportValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Ingestion Flow Service for CHT to FHIR data flow.
 * Orchestrates: validation -> transformation -> FHIR storage for contacts, places, and reports.
 * Includes deduplication, metrics, audit logging, and failure queuing.
 */
@Service
public class CHTIngestionFlowService {

    private static final Logger logger = LoggerFactory.getLogger(CHTIngestionFlowService.class);
    private static final long PERFORMANCE_THRESHOLD_MS = 5000;

    private final CHTContactValidator contactValidator;
    private final CHTReportValidator reportValidator;
    private final CHTToFHIRPersonTransformer personTransformer;
    private final CHTToFHIRPlaceTransformer placeTransformer;
    private final CHTToFHIRReportTransformer reportTransformer;
    private final ResilientFHIRClient resilientFHIRClient;
    private final FHIRClientService fhirClient;
    private final MessageProducerService messageProducer;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Executor transformationExecutor;

    // Deduplication: _id -> _rev — skip if same _id+_rev already processed
    private final ConcurrentHashMap<String, String> processedDocuments = new ConcurrentHashMap<>();

    // Track in-progress ingestions
    private final ConcurrentHashMap<String, CompletableFuture<IngestionFlowService.IngestionFlowResult>> inProgressIngestions =
        new ConcurrentHashMap<>();

    @Autowired(required = false)
    @Qualifier("chtTransformationTimer")
    private Timer chtTransformationTimer;

    @Autowired(required = false)
    @Qualifier("chtTransformationSuccessCounter")
    private Counter chtTransformationSuccessCounter;

    @Autowired(required = false)
    @Qualifier("chtTransformationErrorCounter")
    private Counter chtTransformationErrorCounter;

    public CHTIngestionFlowService(
            CHTContactValidator contactValidator,
            CHTReportValidator reportValidator,
            CHTToFHIRPersonTransformer personTransformer,
            CHTToFHIRPlaceTransformer placeTransformer,
            CHTToFHIRReportTransformer reportTransformer,
            ResilientFHIRClient resilientFHIRClient,
            FHIRClientService fhirClient,
            MessageProducerService messageProducer,
            AuditLogger auditLogger,
            @Qualifier("transformationExecutor") Executor transformationExecutor) {
        this.contactValidator = contactValidator;
        this.reportValidator = reportValidator;
        this.personTransformer = personTransformer;
        this.placeTransformer = placeTransformer;
        this.reportTransformer = reportTransformer;
        this.resilientFHIRClient = resilientFHIRClient;
        this.fhirClient = fhirClient;
        this.messageProducer = messageProducer;
        this.auditLogger = auditLogger;
        this.transformationExecutor = transformationExecutor;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        logger.info("CHTIngestionFlowService initialized");
    }

    /**
     * Check if a document has already been processed (deduplication).
     * Returns true if already processed with the same _rev.
     */
    public boolean isDuplicate(String id, String rev) {
        if (id == null) return false;
        if (rev == null) return false;
        String previousRev = processedDocuments.get(id);
        return rev.equals(previousRev);
    }

    private void markProcessed(String id, String rev) {
        if (id != null && rev != null) {
            processedDocuments.put(id, rev);
        }
    }

    // ==================== Contact Ingestion ====================

    /**
     * Process a CHT person contact through: validate -> transform -> FHIR store -> audit.
     */
    public IngestionFlowService.IngestionFlowResult processContactIngestion(CHTContact contact) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        logger.info("Starting CHT contact ingestion: transactionId={}, contactId={}",
            transactionId, contact != null ? contact.getId() : "null");

        IngestionFlowService.IngestionFlowResult result = new IngestionFlowService.IngestionFlowResult(transactionId);

        try {
            // Deduplication check
            if (contact != null && isDuplicate(contact.getId(), contact.getRev())) {
                logger.info("Skipping duplicate contact: id={}, rev={}", contact.getId(), contact.getRev());
                result.setSuccess(true);
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Step 1: Validate
            CHTContactValidator.ValidationResult validation = contactValidator.validate(contact);
            if (!validation.isValid()) {
                throw new IngestionFlowException(
                    "CHT contact validation failed: " + validation.getErrorMessage(),
                    "CHT_VALIDATION_FAILED");
            }
            result.setValidationPassed(true);

            // Step 2: Transform
            Timer.Sample sample = chtTransformationTimer != null ? Timer.start() : null;
            FHIRResourceWrapper<Patient> wrapper = personTransformer.transform(contact);
            if (sample != null && chtTransformationTimer != null) {
                sample.stop(chtTransformationTimer);
            }
            result.setTransformationCompleted(true);

            // Step 3: Store in FHIR
            Patient patient = wrapper.getResource();
            MethodOutcome outcome = resilientFHIRClient.createPatient(patient);
            String resourceId = outcome.getId() != null ? outcome.getId().getIdPart() : null;
            result.setFhirStorageCompleted(true);
            result.setFhirResourceId(resourceId);

            // Mark processed for deduplication
            markProcessed(contact.getId(), contact.getRev());

            // Record success
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);

            auditLogger.logTransformation("CHT", "FHIR", "CONTACT_INGESTION",
                contact.getId(), resourceId, true, "Contact ingested successfully");

            if (chtTransformationSuccessCounter != null) {
                chtTransformationSuccessCounter.increment();
            }

            checkPerformance(duration);

            logger.info("CHT contact ingestion completed: transactionId={}, duration={}ms",
                transactionId, duration);
            return result;

        } catch (Exception e) {
            return handleFailure(result, transactionId, startTime, contact != null ? contact.getId() : "null",
                "CONTACT_INGESTION", contact, e);
        }
    }

    // ==================== Place Ingestion ====================

    /**
     * Process a CHT place through: transform -> store Organization + optional Location -> audit.
     */
    public IngestionFlowService.IngestionFlowResult processPlaceIngestion(CHTPlace place) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        logger.info("Starting CHT place ingestion: transactionId={}, placeId={}",
            transactionId, place != null ? place.getId() : "null");

        IngestionFlowService.IngestionFlowResult result = new IngestionFlowService.IngestionFlowResult(transactionId);

        try {
            if (place != null && isDuplicate(place.getId(), place.getRev())) {
                logger.info("Skipping duplicate place: id={}, rev={}", place.getId(), place.getRev());
                result.setSuccess(true);
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Transform (place validation is done inside the transformer)
            Timer.Sample sample = chtTransformationTimer != null ? Timer.start() : null;
            List<FHIRResourceWrapper<? extends Resource>> wrappers = placeTransformer.transform(place);
            if (sample != null && chtTransformationTimer != null) {
                sample.stop(chtTransformationTimer);
            }
            result.setTransformationCompleted(true);

            // Store each resource
            List<String> resourceIds = new ArrayList<>();
            for (FHIRResourceWrapper<? extends Resource> wrapper : wrappers) {
                Resource resource = wrapper.getResource();
                MethodOutcome outcome;
                if (resource instanceof Organization) {
                    outcome = resilientFHIRClient.createOrganization((Organization) resource);
                } else if (resource instanceof Location) {
                    outcome = resilientFHIRClient.createLocation((Location) resource);
                } else {
                    logger.warn("Unexpected resource type in place transformation: {}",
                        resource.getClass().getSimpleName());
                    continue;
                }
                if (outcome.getId() != null) {
                    resourceIds.add(outcome.getId().getIdPart());
                }
            }
            result.setFhirStorageCompleted(true);
            result.setFhirResourceId(String.join(",", resourceIds));

            markProcessed(place.getId(), place.getRev());

            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);

            auditLogger.logTransformation("CHT", "FHIR", "PLACE_INGESTION",
                place.getId(), String.join(",", resourceIds), true,
                "Place ingested: " + wrappers.size() + " resources");

            if (chtTransformationSuccessCounter != null) {
                chtTransformationSuccessCounter.increment();
            }

            checkPerformance(duration);

            logger.info("CHT place ingestion completed: transactionId={}, resources={}, duration={}ms",
                transactionId, wrappers.size(), duration);
            return result;

        } catch (Exception e) {
            return handleFailure(result, transactionId, startTime, place != null ? place.getId() : "null",
                "PLACE_INGESTION", place, e);
        }
    }

    // ==================== Report Ingestion ====================

    /**
     * Process a CHT report through: validate -> transform -> store Encounter + Observations -> audit.
     */
    public IngestionFlowService.IngestionFlowResult processReportIngestion(CHTReport report) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        logger.info("Starting CHT report ingestion: transactionId={}, reportId={}",
            transactionId, report != null ? report.getId() : "null");

        IngestionFlowService.IngestionFlowResult result = new IngestionFlowService.IngestionFlowResult(transactionId);

        try {
            if (report != null && isDuplicate(report.getId(), report.getRev())) {
                logger.info("Skipping duplicate report: id={}, rev={}", report.getId(), report.getRev());
                result.setSuccess(true);
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Step 1: Validate
            CHTReportValidator.ValidationResult validation = reportValidator.validate(report);
            if (!validation.isValid()) {
                throw new IngestionFlowException(
                    "CHT report validation failed: " + validation.getErrorMessage(),
                    "CHT_VALIDATION_FAILED");
            }
            result.setValidationPassed(true);

            // Step 2: Transform
            Timer.Sample sample = chtTransformationTimer != null ? Timer.start() : null;
            List<FHIRResourceWrapper<? extends Resource>> wrappers = reportTransformer.transform(report);
            if (sample != null && chtTransformationTimer != null) {
                sample.stop(chtTransformationTimer);
            }
            result.setTransformationCompleted(true);

            // Step 3: Store each resource (1 Encounter + N Observations)
            List<String> resourceIds = new ArrayList<>();
            for (FHIRResourceWrapper<? extends Resource> wrapper : wrappers) {
                Resource resource = wrapper.getResource();
                MethodOutcome outcome;
                if (resource instanceof Encounter) {
                    outcome = resilientFHIRClient.createEncounter((Encounter) resource);
                } else if (resource instanceof Observation) {
                    outcome = resilientFHIRClient.createObservation((Observation) resource);
                } else {
                    logger.warn("Unexpected resource type in report transformation: {}",
                        resource.getClass().getSimpleName());
                    continue;
                }
                if (outcome.getId() != null) {
                    resourceIds.add(outcome.getId().getIdPart());
                }
            }
            result.setFhirStorageCompleted(true);
            result.setFhirResourceId(String.join(",", resourceIds));

            markProcessed(report.getId(), report.getRev());

            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);

            auditLogger.logTransformation("CHT", "FHIR", "REPORT_INGESTION",
                report.getId(), String.join(",", resourceIds), true,
                "Report ingested: " + wrappers.size() + " resources");

            if (chtTransformationSuccessCounter != null) {
                chtTransformationSuccessCounter.increment();
            }

            checkPerformance(duration);

            logger.info("CHT report ingestion completed: transactionId={}, resources={}, duration={}ms",
                transactionId, wrappers.size(), duration);
            return result;

        } catch (Exception e) {
            return handleFailure(result, transactionId, startTime, report != null ? report.getId() : "null",
                "REPORT_INGESTION", report, e);
        }
    }

    // ==================== Concurrent Processing ====================

    /**
     * Process multiple contacts concurrently.
     */
    public List<IngestionFlowService.IngestionFlowResult> processContactsConcurrently(List<CHTContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return new ArrayList<>();
        }

        logger.info("Starting concurrent CHT contact ingestion for {} contacts", contacts.size());

        List<CompletableFuture<IngestionFlowService.IngestionFlowResult>> futures = new ArrayList<>();
        for (CHTContact contact : contacts) {
            futures.add(processContactIngestionAsync(contact));
        }

        List<IngestionFlowService.IngestionFlowResult> results = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<IngestionFlowService.IngestionFlowResult> f : futures) {
                results.add(f.get());
            }
        } catch (Exception e) {
            logger.error("Error during concurrent CHT contact ingestion", e);
        }

        long successCount = results.stream().filter(IngestionFlowService.IngestionFlowResult::isSuccess).count();
        logger.info("Concurrent CHT contact ingestion done: {} success, {} errors",
            successCount, results.size() - successCount);
        return results;
    }

    /**
     * Process a contact ingestion asynchronously.
     */
    public CompletableFuture<IngestionFlowService.IngestionFlowResult> processContactIngestionAsync(CHTContact contact) {
        String contactId = contact != null ? contact.getId() : "null";

        CompletableFuture<IngestionFlowService.IngestionFlowResult> existing = inProgressIngestions.get(contactId);
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        CompletableFuture<IngestionFlowService.IngestionFlowResult> future =
            CompletableFuture.supplyAsync(() -> processContactIngestion(contact), transformationExecutor);

        inProgressIngestions.put(contactId, future);
        future.whenComplete((r, t) -> inProgressIngestions.remove(contactId));
        return future;
    }

    // ==================== Helpers ====================

    private IngestionFlowService.IngestionFlowResult handleFailure(
            IngestionFlowService.IngestionFlowResult result, String transactionId,
            long startTime, String sourceId, String operation, Object sourceObject, Exception e) {

        long duration = System.currentTimeMillis() - startTime;
        result.setSuccess(false);
        result.setDurationMs(duration);
        result.setErrorMessage(e.getMessage());

        logger.error("CHT ingestion failed: transactionId={}, operation={}, error={}",
            transactionId, operation, e.getMessage(), e);

        auditLogger.logTransformation("CHT", "FHIR", operation,
            sourceId, null, false, "Ingestion failed: " + e.getMessage());

        if (chtTransformationErrorCounter != null) {
            chtTransformationErrorCounter.increment();
        }

        // Queue for retry
        queueForRetry(sourceObject, transactionId, e);

        return result;
    }

    private void queueForRetry(Object sourceObject, String transactionId, Exception error) {
        try {
            String payload = objectMapper.writeValueAsString(sourceObject);
            QueueMessage queueMessage = new QueueMessage(payload, "CHT_INGESTION_RETRY");
            queueMessage.setId(transactionId);
            queueMessage.setErrorMessage(error.getMessage());
            messageProducer.sendToRetryQueue(queueMessage);
            logger.info("Failed CHT ingestion queued for retry: transactionId={}", transactionId);
        } catch (Exception e) {
            logger.error("Failed to queue CHT ingestion for retry: transactionId={}", transactionId, e);
            Map<String, String> context = new HashMap<>();
            context.put("transactionId", transactionId);
            context.put("originalError", error.getMessage());
            auditLogger.logError("CHTIngestionFlowService", "queueForRetry",
                "QUEUE_FAILED", "Failed to queue for retry", context);
        }
    }

    private void checkPerformance(long duration) {
        if (duration > PERFORMANCE_THRESHOLD_MS) {
            auditLogger.logPerformanceAlert("CHTIngestionFlowService", "ingestion_duration",
                duration, PERFORMANCE_THRESHOLD_MS, "CHT ingestion exceeded threshold");
            logger.warn("CHT ingestion exceeded performance threshold: {}ms > {}ms",
                duration, PERFORMANCE_THRESHOLD_MS);
        }
    }

    /**
     * Clear the deduplication cache (useful for bulk sync reset).
     */
    public void clearDeduplicationCache() {
        processedDocuments.clear();
    }
}
