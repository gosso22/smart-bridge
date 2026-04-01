package com.smartbridge.mediators.cht;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.transformation.FHIRToCHTPersonTransformer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Reverse Sync Flow Service for FHIR to CHT data flow.
 * Orchestrates: FHIR change detection -> circular update check -> transformation -> CHT storage.
 * Includes deduplication, conflict resolution, and audit logging.
 */
@Service
public class CHTReverseSyncFlowService {

    private static final Logger logger = LoggerFactory.getLogger(CHTReverseSyncFlowService.class);
    private static final String CHT_SOURCE_TAG = "CHT";
    private static final String CHT_UUID_SYSTEM = "http://moh.go.tz/identifier/cht-uuid";
    private static final String CHT_PATIENT_ID_SYSTEM = "http://moh.go.tz/identifier/cht-patient-id";

    private final FHIRChangeDetectionService changeDetectionService;
    private final FHIRToCHTPersonTransformer transformer;
    private final CHTApiClient chtApiClient;
    private final AuditLogger auditLogger;
    private final Executor reverseSyncExecutor;

    // Track processed resources to detect already-processed and circular updates
    private final ConcurrentHashMap<String, Date> processedResources = new ConcurrentHashMap<>();

    // In-progress sync futures for deduplication
    private final ConcurrentHashMap<String, CompletableFuture<CHTReverseSyncResult>> inProgressSyncs =
        new ConcurrentHashMap<>();

    @Autowired(required = false)
    @Qualifier("chtReverseSyncTimer")
    private Timer chtReverseSyncTimer;

    @Autowired(required = false)
    @Qualifier("chtReverseSyncSuccessCounter")
    private Counter chtReverseSyncSuccessCounter;

    @Autowired(required = false)
    @Qualifier("chtReverseSyncErrorCounter")
    private Counter chtReverseSyncErrorCounter;

    public CHTReverseSyncFlowService(
            FHIRChangeDetectionService changeDetectionService,
            FHIRToCHTPersonTransformer transformer,
            CHTApiClient chtApiClient,
            AuditLogger auditLogger,
            @Qualifier("reverseSyncExecutor") Executor reverseSyncExecutor) {
        this.changeDetectionService = changeDetectionService;
        this.transformer = transformer;
        this.chtApiClient = chtApiClient;
        this.auditLogger = auditLogger;
        this.reverseSyncExecutor = reverseSyncExecutor;
        logger.info("CHTReverseSyncFlowService initialized");
    }

    /**
     * Register change listener for Patient resources.
     * Called during application startup.
     */
    public void initializeReverseSyncFlow() {
        changeDetectionService.registerChangeListener("Patient", this::handlePatientChange);
        logger.info("CHT reverse sync flow initialized — listening for Patient changes");
    }

    // ==================== Main Flow ====================

    /**
     * Process a single FHIR Patient change through the reverse sync flow.
     */
    public CHTReverseSyncResult processReverseSync(Resource resource) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        String resourceId = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : "unknown";

        logger.info("Starting CHT reverse sync: transactionId={}, resourceId={}", transactionId, resourceId);

        CHTReverseSyncResult result = new CHTReverseSyncResult(transactionId);
        result.setFhirResourceId(resourceId);

        try {
            // Step 1: Must be a Patient
            if (!(resource instanceof Patient)) {
                result.setSuccess(true);
                result.setSkipped(true);
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }
            Patient patient = (Patient) resource;

            // Step 2: Check circular update — skip if originated from CHT
            if (isUpdateFromCHT(patient)) {
                logger.info("Skipping circular update for CHT-originated resource: {}", resourceId);
                result.setSuccess(true);
                result.setSkipped(true);
                result.setConflictDetected(true);
                result.setConflictResolution("CIRCULAR_UPDATE_SKIP");
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Step 3: Check CHT identifiers present — skip if not a CHT-linked patient
            String chtUuid = extractIdentifier(patient, CHT_UUID_SYSTEM);
            if (chtUuid == null) {
                logger.debug("Skipping Patient without CHT identifiers: {}", resourceId);
                result.setSuccess(true);
                result.setSkipped(true);
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Step 4: Check already-processed (same resource, same lastUpdated)
            Date currentLastUpdated = patient.getMeta() != null ? patient.getMeta().getLastUpdated() : null;
            if (isAlreadyProcessed(resourceId, currentLastUpdated)) {
                logger.debug("Skipping already-processed resource: {}", resourceId);
                result.setSuccess(true);
                result.setSkipped(true);
                result.setConflictDetected(true);
                result.setConflictResolution("ALREADY_PROCESSED");
                result.setDurationMs(System.currentTimeMillis() - startTime);
                return result;
            }

            // Step 5: Transform FHIR Patient -> CHT Contact
            Timer.Sample sample = chtReverseSyncTimer != null ? Timer.start() : null;
            FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "FHIR", resourceId);
            CHTContact contact = transformer.transform(wrapper);
            if (sample != null && chtReverseSyncTimer != null) {
                sample.stop(chtReverseSyncTimer);
            }
            result.setTransformationCompleted(true);

            // Step 6: Determine create vs update by checking if contact exists in CHT
            String operationType = storeInCHT(chtUuid, contact, result);
            result.setChtOperationType(operationType);

            // Step 7: Record success
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);
            result.setChtContactId(chtUuid);

            // Track processed
            processedResources.put(resourceId, currentLastUpdated != null ? currentLastUpdated : new Date());

            auditLogger.logTransformation("FHIR", "CHT", "REVERSE_SYNC",
                resourceId, chtUuid, true,
                operationType + " completed for CHT contact");

            if (chtReverseSyncSuccessCounter != null) {
                chtReverseSyncSuccessCounter.increment();
            }

            logger.info("CHT reverse sync completed: transactionId={}, operation={}, duration={}ms",
                transactionId, operationType, duration);
            return result;

        } catch (Exception e) {
            return handleFailure(result, transactionId, startTime, resourceId, e);
        }
    }

    // ==================== Change Listener ====================

    private void handlePatientChange(Resource resource) {
        logger.info("Handling Patient change for CHT reverse sync: resourceId={}",
            resource.getIdElement().getIdPart());
        try {
            processReverseSync(resource);
        } catch (Exception e) {
            logger.error("Error handling Patient change for CHT reverse sync", e);
        }
    }

    // ==================== Circular Update Detection ====================

    /**
     * Check if the resource update originated from CHT (to prevent circular sync).
     * Looks for "CHT" in meta.tag codes.
     */
    boolean isUpdateFromCHT(Resource resource) {
        if (resource.hasMeta() && resource.getMeta().hasTag()) {
            return resource.getMeta().getTag().stream()
                .anyMatch(tag -> CHT_SOURCE_TAG.equals(tag.getCode()));
        }
        return false;
    }

    /**
     * Check if a resource has already been processed with the same lastUpdated timestamp.
     */
    boolean isAlreadyProcessed(String resourceId, Date lastUpdated) {
        if (resourceId == null || lastUpdated == null) return false;
        Date previousDate = processedResources.get(resourceId);
        return previousDate != null && !lastUpdated.after(previousDate);
    }

    // ==================== CHT Storage ====================

    /**
     * Store the CHT contact — creates if not found, updates if exists.
     * Returns "CREATE" or "UPDATE".
     */
    private String storeInCHT(String chtUuid, CHTContact contact, CHTReverseSyncResult result)
            throws MediatorException {
        CHTContact existing = null;
        try {
            existing = chtApiClient.getContact(chtUuid);
        } catch (MediatorException e) {
            // 404 or connection error → treat as "not found"
            logger.debug("Contact not found in CHT (will create): id={}", chtUuid);
        }

        if (existing != null) {
            logger.info("Updating existing CHT contact: id={}", chtUuid);
            chtApiClient.updatePerson(chtUuid, contact);
            result.setChtStorageCompleted(true);
            return "UPDATE";
        } else {
            logger.info("Creating new CHT contact: id={}", chtUuid);
            chtApiClient.createPerson(contact);
            result.setChtStorageCompleted(true);
            return "CREATE";
        }
    }

    // ==================== Helpers ====================

    private String extractIdentifier(Patient patient, String system) {
        if (!patient.hasIdentifier()) return null;
        for (Identifier id : patient.getIdentifier()) {
            if (id.hasSystem() && system.equals(id.getSystem()) && id.hasValue()) {
                return id.getValue();
            }
        }
        return null;
    }

    private CHTReverseSyncResult handleFailure(
            CHTReverseSyncResult result, String transactionId,
            long startTime, String resourceId, Exception e) {

        long duration = System.currentTimeMillis() - startTime;
        result.setSuccess(false);
        result.setDurationMs(duration);
        result.setErrorMessage(e.getMessage());

        logger.error("CHT reverse sync failed: transactionId={}, resourceId={}, error={}",
            transactionId, resourceId, e.getMessage(), e);

        auditLogger.logTransformation("FHIR", "CHT", "REVERSE_SYNC",
            resourceId, null, false, "Reverse sync failed: " + e.getMessage());

        if (chtReverseSyncErrorCounter != null) {
            chtReverseSyncErrorCounter.increment();
        }

        Map<String, String> context = new HashMap<>();
        context.put("transactionId", transactionId);
        context.put("resourceId", resourceId);
        context.put("error", e.getMessage());
        auditLogger.logError("CHTReverseSyncFlowService", "processReverseSync",
            "REVERSE_SYNC_FAILED", "CHT reverse sync failed", context);

        return result;
    }

    // ==================== Async / Concurrent ====================

    public CompletableFuture<CHTReverseSyncResult> processReverseSyncAsync(Resource resource) {
        String resourceId = resource.getIdElement().getIdPart();

        CompletableFuture<CHTReverseSyncResult> existing = inProgressSyncs.get(resourceId);
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        CompletableFuture<CHTReverseSyncResult> future =
            CompletableFuture.supplyAsync(() -> processReverseSync(resource), reverseSyncExecutor);

        inProgressSyncs.put(resourceId, future);
        future.whenComplete((r, t) -> inProgressSyncs.remove(resourceId));
        return future;
    }

    public boolean isReverseSyncInProgress(String resourceId) {
        CompletableFuture<CHTReverseSyncResult> future = inProgressSyncs.get(resourceId);
        return future != null && !future.isDone();
    }

    // ==================== Result ====================

    public static class CHTReverseSyncResult {
        private final String transactionId;
        private boolean success;
        private boolean skipped;
        private long durationMs;
        private String fhirResourceId;
        private String chtContactId;
        private String errorMessage;
        private boolean conflictDetected;
        private String conflictResolution;
        private boolean transformationCompleted;
        private boolean chtStorageCompleted;
        private String chtOperationType;

        public CHTReverseSyncResult(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTransactionId() { return transactionId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public boolean isSkipped() { return skipped; }
        public void setSkipped(boolean skipped) { this.skipped = skipped; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getFhirResourceId() { return fhirResourceId; }
        public void setFhirResourceId(String fhirResourceId) { this.fhirResourceId = fhirResourceId; }
        public String getChtContactId() { return chtContactId; }
        public void setChtContactId(String chtContactId) { this.chtContactId = chtContactId; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public boolean isConflictDetected() { return conflictDetected; }
        public void setConflictDetected(boolean conflictDetected) { this.conflictDetected = conflictDetected; }
        public String getConflictResolution() { return conflictResolution; }
        public void setConflictResolution(String conflictResolution) { this.conflictResolution = conflictResolution; }
        public boolean isTransformationCompleted() { return transformationCompleted; }
        public void setTransformationCompleted(boolean transformationCompleted) { this.transformationCompleted = transformationCompleted; }
        public boolean isChtStorageCompleted() { return chtStorageCompleted; }
        public void setChtStorageCompleted(boolean chtStorageCompleted) { this.chtStorageCompleted = chtStorageCompleted; }
        public String getChtOperationType() { return chtOperationType; }
        public void setChtOperationType(String chtOperationType) { this.chtOperationType = chtOperationType; }
    }
}
