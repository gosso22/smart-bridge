package com.smartbridge.mediators.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.transformation.FHIRToUCSTransformer;
import com.smartbridge.mediators.ucs.UCSApiClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reverse Sync Flow Service for FHIR to UCS data flow.
 * Orchestrates the complete reverse flow: FHIR change detection -> transformation -> UCS storage.
 * Includes conflict resolution, data consistency checks, and transaction coordination.
 * 
 * Requirements: 3.2, 3.5
 */
@Service
public class ReverseSyncFlowService {

    private static final Logger logger = LoggerFactory.getLogger(ReverseSyncFlowService.class);

    private final FHIRChangeDetectionService changeDetectionService;
    private final FHIRToUCSTransformer transformer;
    private final FHIRClientService fhirClient;
    private final UCSApiClient ucsApiClient;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final Executor reverseSyncExecutor;

    // Track processed resources to detect conflicts - thread-safe
    private final Map<String, ResourceVersion> processedResources = new ConcurrentHashMap<>();
    
    // Thread-safe tracking of in-progress reverse syncs to prevent duplicates
    private final Map<String, CompletableFuture<ReverseSyncFlowResult>> inProgressSyncs = 
        new ConcurrentHashMap<>();
    
    // Read-write lock for resource version updates
    private final ReadWriteLock versionLock = new ReentrantReadWriteLock();

    @Autowired(required = false)
    @Qualifier("reverseSyncTimer")
    private Timer reverseSyncTimer;

    @Autowired(required = false)
    @Qualifier("reverseSyncSuccessCounter")
    private Counter reverseSyncSuccessCounter;

    @Autowired(required = false)
    @Qualifier("reverseSyncErrorCounter")
    private Counter reverseSyncErrorCounter;

    @Autowired(required = false)
    @Qualifier("conflictDetectedCounter")
    private Counter conflictDetectedCounter;

    public ReverseSyncFlowService(
            FHIRChangeDetectionService changeDetectionService,
            FHIRToUCSTransformer transformer,
            FHIRClientService fhirClient,
            UCSApiClient ucsApiClient,
            AuditLogger auditLogger,
            @Qualifier("reverseSyncExecutor") Executor reverseSyncExecutor) {
        this.changeDetectionService = changeDetectionService;
        this.transformer = transformer;
        this.fhirClient = fhirClient;
        this.ucsApiClient = ucsApiClient;
        this.auditLogger = auditLogger;
        this.reverseSyncExecutor = reverseSyncExecutor;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        logger.info("ReverseSyncFlowService initialized with concurrent processing support");
    }

    /**
     * Initialize reverse sync flow by registering change listeners.
     * This sets up automatic processing of FHIR resource changes.
     */
    public void initializeReverseSyncFlow() {
        logger.info("Initializing reverse sync flow with change detection");
        
        // Register listener for Patient resource changes
        changeDetectionService.registerChangeListener("Patient", this::handlePatientChange);
        
        logger.info("Reverse sync flow initialized successfully");
    }

    /**
     * Process a single FHIR resource change through the reverse sync flow.
     * Validates, transforms, and stores data in UCS system.
     * 
     * @param resource The FHIR resource that changed
     * @return ReverseSyncFlowResult containing the outcome and metrics
     */
    public ReverseSyncFlowResult processReverseSync(Resource resource) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        logger.info("Starting reverse sync flow: transactionId={}, resourceType={}, resourceId={}",
            transactionId, resource.getResourceType(), resource.getIdElement().getIdPart());
        
        ReverseSyncFlowResult result = new ReverseSyncFlowResult(transactionId);
        result.setResourceType(resource.getResourceType().name());
        result.setFhirResourceId(resource.getIdElement().getIdPart());
        
        try {
            // Step 1: Check for conflicts
            ConflictCheckResult conflictCheck = checkForConflicts(resource);
            result.setConflictDetected(conflictCheck.hasConflict());
            
            if (conflictCheck.hasConflict()) {
                // Step 2: Resolve conflicts
                ConflictResolution resolution = resolveConflict(resource, conflictCheck);
                result.setConflictResolution(resolution.getResolutionType());
                
                if (resolution.getResolutionType() == ConflictResolutionType.SKIP) {
                    logger.info("Skipping reverse sync due to conflict resolution: transactionId={}", 
                        transactionId);
                    result.setSuccess(true);
                    result.setSkipped(true);
                    result.setDurationMs(System.currentTimeMillis() - startTime);
                    return result;
                }
            }
            
            // Step 3: Transform FHIR to UCS
            UCSClient ucsClient = transformToUCS(resource, result);
            
            // Step 4: Store in UCS system
            String ucsClientId = storeInUCS(ucsClient, result);
            
            // Step 5: Verify data consistency
            verifyDataConsistency(resource, ucsClient, result);
            
            // Step 6: Record success
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);
            result.setUcsClientId(ucsClientId);
            
            // Update processed resources tracking
            updateProcessedResourceTracking(resource);
            
            // Log audit trail
            auditLogger.logTransformation(
                "FHIR", "UCS", "REVERSE_SYNC",
                resource.getIdElement().getIdPart(), ucsClientId,
                true, "Reverse sync completed successfully"
            );
            
            // Update metrics
            if (reverseSyncSuccessCounter != null) {
                reverseSyncSuccessCounter.increment();
            }
            
            logger.info("Reverse sync flow completed successfully: transactionId={}, duration={}ms",
                transactionId, duration);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(false);
            result.setDurationMs(duration);
            result.setErrorMessage(e.getMessage());
            
            // Log error
            logger.error("Reverse sync flow failed: transactionId={}, duration={}ms, error={}",
                transactionId, duration, e.getMessage(), e);
            
            // Audit error
            auditLogger.logTransformation(
                "FHIR", "UCS", "REVERSE_SYNC",
                resource.getIdElement().getIdPart(), null,
                false, "Reverse sync failed: " + e.getMessage()
            );
            
            // Update metrics
            if (reverseSyncErrorCounter != null) {
                reverseSyncErrorCounter.increment();
            }
            
            // Queue for retry if appropriate
            handleReverseSyncFailure(resource, transactionId, e);
            
            return result;
        }
    }

    /**
     * Handle Patient resource change notification from change detection service.
     */
    private void handlePatientChange(Resource resource) {
        logger.info("Handling Patient change notification: resourceId={}", 
            resource.getIdElement().getIdPart());
        
        try {
            ReverseSyncFlowResult result = processReverseSync(resource);
            
            if (!result.isSuccess()) {
                logger.warn("Patient change processing failed: resourceId={}, error={}",
                    resource.getIdElement().getIdPart(), result.getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("Error handling Patient change notification", e);
        }
    }

    /**
     * Check for conflicts before processing reverse sync.
     * Detects if the resource has been modified since last sync.
     */
    private ConflictCheckResult checkForConflicts(Resource resource) {
        String resourceId = resource.getIdElement().getIdPart();
        ConflictCheckResult result = new ConflictCheckResult();
        
        // Check if we've processed this resource before
        ResourceVersion lastVersion = processedResources.get(resourceId);
        
        if (lastVersion == null) {
            // First time seeing this resource, no conflict
            result.setHasConflict(false);
            logger.debug("No previous version found for resource: {}", resourceId);
            return result;
        }
        
        // Get current version from resource meta
        Date currentLastUpdated = resource.getMeta() != null ? 
            resource.getMeta().getLastUpdated() : null;
        
        if (currentLastUpdated == null) {
            // No timestamp available, assume no conflict
            result.setHasConflict(false);
            logger.debug("No timestamp available for resource: {}", resourceId);
            return result;
        }
        
        // Check if resource was updated after our last processing
        if (currentLastUpdated.after(lastVersion.getLastUpdated())) {
            // Check if the update came from UCS (our system)
            if (isUpdateFromUCS(resource)) {
                // This is a circular update from our own ingestion flow
                result.setHasConflict(true);
                result.setConflictType(ConflictType.CIRCULAR_UPDATE);
                logger.warn("Detected circular update for resource: {}", resourceId);
                
                if (conflictDetectedCounter != null) {
                    conflictDetectedCounter.increment();
                }
            } else {
                // Legitimate external update
                result.setHasConflict(false);
                logger.debug("Legitimate external update for resource: {}", resourceId);
            }
        } else {
            // Resource hasn't changed since last processing
            result.setHasConflict(true);
            result.setConflictType(ConflictType.ALREADY_PROCESSED);
            logger.debug("Resource already processed: {}", resourceId);
        }
        
        return result;
    }

    /**
     * Resolve detected conflicts based on conflict type.
     */
    private ConflictResolution resolveConflict(Resource resource, ConflictCheckResult conflictCheck) {
        ConflictResolution resolution = new ConflictResolution();
        
        switch (conflictCheck.getConflictType()) {
            case CIRCULAR_UPDATE:
                // Skip circular updates to prevent infinite loops
                resolution.setResolutionType(ConflictResolutionType.SKIP);
                resolution.setReason("Circular update detected - originated from UCS ingestion");
                logger.info("Resolving circular update conflict by skipping: resourceId={}",
                    resource.getIdElement().getIdPart());
                break;
                
            case ALREADY_PROCESSED:
                // Skip already processed resources
                resolution.setResolutionType(ConflictResolutionType.SKIP);
                resolution.setReason("Resource already processed");
                logger.debug("Resolving already-processed conflict by skipping: resourceId={}",
                    resource.getIdElement().getIdPart());
                break;
                
            case VERSION_MISMATCH:
                // Use latest version (FHIR wins)
                resolution.setResolutionType(ConflictResolutionType.USE_FHIR_VERSION);
                resolution.setReason("Version mismatch - using FHIR version");
                logger.info("Resolving version mismatch by using FHIR version: resourceId={}",
                    resource.getIdElement().getIdPart());
                break;
                
            default:
                // Default to processing
                resolution.setResolutionType(ConflictResolutionType.PROCEED);
                resolution.setReason("No conflict or unknown conflict type");
                break;
        }
        
        // Log conflict resolution
        auditLogger.logError(
            "ReverseSyncFlowService", "resolveConflict",
            conflictCheck.getConflictType().name(), resolution.getReason(),
            Map.of(
                "resourceId", resource.getIdElement().getIdPart(),
                "resolutionType", resolution.getResolutionType().name()
            )
        );
        
        return resolution;
    }

    /**
     * Check if the resource update originated from UCS system.
     * This helps detect circular updates.
     */
    private boolean isUpdateFromUCS(Resource resource) {
        // Check resource meta for source system tag
        if (resource.hasMeta() && resource.getMeta().hasTag()) {
            return resource.getMeta().getTag().stream()
                .anyMatch(tag -> "UCS".equals(tag.getCode()) || 
                               "smart-bridge-ingestion".equals(tag.getCode()));
        }
        return false;
    }

    /**
     * Transform FHIR resource to UCS client format.
     */
    private UCSClient transformToUCS(Resource resource, ReverseSyncFlowResult result) {
        logger.debug("Transforming FHIR resource to UCS format");
        
        Timer.Sample sample = reverseSyncTimer != null ? Timer.start() : null;
        
        try {
            if (!(resource instanceof Patient)) {
                throw new ReverseSyncFlowException(
                    "Only Patient resources supported in reverse sync flow",
                    "UNSUPPORTED_RESOURCE_TYPE"
                );
            }
            
            Patient patientResource = (Patient) resource;
            String originalId = patientResource.getIdElement().getIdPart();
            FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
                patientResource, "FHIR", originalId
            );
            
            UCSClient ucsClient = transformer.transformFHIRToUCS(wrapper);
            
            if (sample != null && reverseSyncTimer != null) {
                sample.stop(reverseSyncTimer);
            }
            
            result.setTransformationCompleted(true);
            logger.debug("Transformation completed successfully");
            
            return ucsClient;
            
        } catch (Exception e) {
            String errorMsg = "Transformation failed: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new ReverseSyncFlowException(errorMsg, "TRANSFORMATION_FAILED", e);
        }
    }

    /**
     * Store UCS client in UCS system.
     * Determines whether to create or update based on existing data.
     */
    private String storeInUCS(UCSClient ucsClient, ReverseSyncFlowResult result) {
        logger.debug("Storing UCS client in UCS system");
        
        try {
            String opensrpId = ucsClient.getIdentifiers().getOpensrpId();
            
            // Try to get existing client first
            UCSClient existingClient = null;
            try {
                existingClient = ucsApiClient.getClient(opensrpId);
            } catch (MediatorException e) {
                // Client doesn't exist, will create new one
                logger.debug("Client not found in UCS, will create new: opensrpId={}", opensrpId);
            }
            
            if (existingClient != null) {
                // Update existing client
                logger.info("Updating existing UCS client: opensrpId={}", opensrpId);
                ucsApiClient.updateClient(opensrpId, ucsClient);
                result.setUcsOperationType("UPDATE");
            } else {
                // Create new client
                logger.info("Creating new UCS client: opensrpId={}", opensrpId);
                ucsApiClient.createClient(ucsClient);
                result.setUcsOperationType("CREATE");
            }
            
            result.setUcsStorageCompleted(true);
            logger.debug("UCS client stored successfully: opensrpId={}", opensrpId);
            
            // Log UCS operation
            auditLogger.logError(
                "ReverseSyncFlowService", "storeInUCS",
                "UCS_OPERATION", result.getUcsOperationType() + " completed",
                Map.of(
                    "opensrpId", opensrpId,
                    "operation", result.getUcsOperationType()
                )
            );
            
            return opensrpId;
            
        } catch (MediatorException e) {
            String errorMsg = "UCS storage failed: " + e.getMessage();
            logger.error(errorMsg, e);
            
            throw new ReverseSyncFlowException(errorMsg, "UCS_STORAGE_FAILED", e);
        }
    }

    /**
     * Verify data consistency between FHIR and UCS after sync.
     * Performs basic validation that critical fields match.
     */
    private void verifyDataConsistency(Resource fhirResource, UCSClient ucsClient, 
                                      ReverseSyncFlowResult result) {
        logger.debug("Verifying data consistency");
        
        List<String> inconsistencies = new ArrayList<>();
        
        if (fhirResource instanceof Patient) {
            Patient patient = (Patient) fhirResource;
            
            // Verify identifiers
            String fhirOpensrpId = patient.getIdentifier().stream()
                .filter(id -> "http://moh.go.tz/identifier/opensrp-id".equals(id.getSystem()))
                .map(id -> id.getValue())
                .findFirst()
                .orElse(null);
            
            String ucsOpensrpId = ucsClient.getIdentifiers().getOpensrpId();
            
            if (!Objects.equals(fhirOpensrpId, ucsOpensrpId)) {
                inconsistencies.add("OpenSRP ID mismatch: FHIR=" + fhirOpensrpId + 
                                  ", UCS=" + ucsOpensrpId);
            }
            
            // Verify name
            if (patient.hasName() && !patient.getName().isEmpty()) {
                String fhirFirstName = patient.getName().get(0).getGiven().get(0).getValue();
                String ucsFirstName = ucsClient.getDemographics().getFirstName();
                
                if (!Objects.equals(fhirFirstName, ucsFirstName)) {
                    inconsistencies.add("First name mismatch: FHIR=" + fhirFirstName + 
                                      ", UCS=" + ucsFirstName);
                }
            }
        }
        
        if (!inconsistencies.isEmpty()) {
            logger.warn("Data consistency issues detected: {}", inconsistencies);
            result.setConsistencyIssues(inconsistencies);
            
            // Log consistency issues
            auditLogger.logError(
                "ReverseSyncFlowService", "verifyDataConsistency",
                "CONSISTENCY_WARNING", "Data consistency issues detected",
                Map.of(
                    "resourceId", fhirResource.getIdElement().getIdPart(),
                    "issues", String.join("; ", inconsistencies)
                )
            );
        } else {
            logger.debug("Data consistency verified successfully");
            result.setConsistencyVerified(true);
        }
    }

    /**
     * Update tracking of processed resources to detect future conflicts.
     * Thread-safe update using write lock.
     */
    private void updateProcessedResourceTracking(Resource resource) {
        String resourceId = resource.getIdElement().getIdPart();
        Date lastUpdated = resource.getMeta() != null ? 
            resource.getMeta().getLastUpdated() : new Date();
        
        versionLock.writeLock().lock();
        try {
            ResourceVersion version = new ResourceVersion(resourceId, lastUpdated);
            processedResources.put(resourceId, version);
            
            logger.debug("Updated processed resource tracking: resourceId={}, lastUpdated={}",
                resourceId, lastUpdated);
        } finally {
            versionLock.writeLock().unlock();
        }
    }

    /**
     * Process multiple FHIR resources concurrently through the reverse sync flow.
     * Ensures thread safety and prevents data corruption during concurrent operations.
     * 
     * @param resources List of FHIR resources to process
     * @return List of reverse sync results for each resource
     */
    public List<ReverseSyncFlowResult> processReverseSyncConcurrently(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            logger.warn("Empty or null resource list provided for concurrent reverse sync");
            return new ArrayList<>();
        }

        logger.info("Starting concurrent reverse sync flow for {} resources", resources.size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<ReverseSyncFlowResult>> futures = new ArrayList<>();

        // Submit all reverse sync tasks
        for (Resource resource : resources) {
            String resourceId = resource.getIdElement().getIdPart();
            
            // Check if sync is already in progress for this resource
            CompletableFuture<ReverseSyncFlowResult> existingFuture = inProgressSyncs.get(resourceId);
            if (existingFuture != null && !existingFuture.isDone()) {
                logger.info("Reverse sync already in progress for resource: {}", resourceId);
                futures.add(existingFuture);
                continue;
            }

            // Submit new reverse sync task
            CompletableFuture<ReverseSyncFlowResult> future = CompletableFuture.supplyAsync(() -> {
                return processReverseSync(resource);
            }, reverseSyncExecutor);

            // Track in-progress sync
            inProgressSyncs.put(resourceId, future);
            
            // Clean up tracking when complete
            future.whenComplete((result, throwable) -> {
                inProgressSyncs.remove(resourceId);
            });

            futures.add(future);
        }

        // Wait for all reverse syncs to complete
        List<ReverseSyncFlowResult> results = new ArrayList<>();
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allFutures.join();

            // Collect results
            for (CompletableFuture<ReverseSyncFlowResult> future : futures) {
                results.add(future.get());
            }

            long duration = System.currentTimeMillis() - startTime;
            long successCount = results.stream().filter(ReverseSyncFlowResult::isSuccess).count();
            long failureCount = results.size() - successCount;

            logger.info("Concurrent reverse sync flow completed: {} successful, {} failed, duration={}ms",
                successCount, failureCount, duration);

        } catch (Exception e) {
            logger.error("Error during concurrent reverse sync", e);
        }

        return results;
    }

    /**
     * Process reverse sync asynchronously and return a CompletableFuture.
     * Allows non-blocking concurrent processing.
     * 
     * @param resource The FHIR resource to process
     * @return CompletableFuture containing the reverse sync result
     */
    public CompletableFuture<ReverseSyncFlowResult> processReverseSyncAsync(Resource resource) {
        String resourceId = resource.getIdElement().getIdPart();
        
        // Check if sync is already in progress
        CompletableFuture<ReverseSyncFlowResult> existingFuture = inProgressSyncs.get(resourceId);
        if (existingFuture != null && !existingFuture.isDone()) {
            logger.info("Reverse sync already in progress for resource: {}, returning existing future", resourceId);
            return existingFuture;
        }

        // Submit new reverse sync task
        CompletableFuture<ReverseSyncFlowResult> future = CompletableFuture.supplyAsync(() -> {
            return processReverseSync(resource);
        }, reverseSyncExecutor);

        // Track in-progress sync
        inProgressSyncs.put(resourceId, future);
        
        // Clean up tracking when complete
        future.whenComplete((result, throwable) -> {
            inProgressSyncs.remove(resourceId);
        });

        return future;
    }

    /**
     * Check if a reverse sync is currently in progress for a given resource.
     * Thread-safe check using concurrent map.
     * 
     * @param resourceId The FHIR resource identifier
     * @return true if reverse sync is in progress, false otherwise
     */
    public boolean isReverseSyncInProgress(String resourceId) {
        CompletableFuture<ReverseSyncFlowResult> future = inProgressSyncs.get(resourceId);
        return future != null && !future.isDone();
    }

    /**
     * Get the number of reverse syncs currently in progress.
     * Thread-safe count using concurrent map.
     * 
     * @return Number of in-progress reverse syncs
     */
    public int getInProgressReverseSyncCount() {
        return (int) inProgressSyncs.values().stream()
            .filter(future -> !future.isDone())
            .count();
    }

    /**
     * Handle reverse sync failure by logging for manual intervention.
     */
    private void handleReverseSyncFailure(Resource resource, String transactionId, Exception error) {
        try {
            logger.error("Reverse sync failed and needs manual intervention: transactionId={}, resourceId={}, error={}",
                transactionId, resource.getIdElement().getIdPart(), error.getMessage());
            
            // Log error for manual intervention
            Map<String, String> context = new HashMap<>();
            context.put("transactionId", transactionId);
            context.put("resourceId", resource.getIdElement().getIdPart());
            context.put("resourceType", resource.getResourceType().name());
            context.put("error", error.getMessage());
            
            auditLogger.logError(
                "ReverseSyncFlowService", "handleReverseSyncFailure",
                "REVERSE_SYNC_FAILED", "Reverse sync failed - manual intervention required",
                context
            );
            
        } catch (Exception e) {
            logger.error("Failed to log reverse sync failure: transactionId={}", 
                transactionId, e);
        }
    }

    /**
     * Result object for reverse sync flow operations.
     */
    public static class ReverseSyncFlowResult {
        private final String transactionId;
        private boolean success;
        private boolean skipped;
        private long durationMs;
        private String resourceType;
        private String fhirResourceId;
        private String ucsClientId;
        private String errorMessage;
        private boolean conflictDetected;
        private String conflictResolution;
        private boolean transformationCompleted;
        private boolean ucsStorageCompleted;
        private String ucsOperationType;
        private boolean consistencyVerified;
        private List<String> consistencyIssues;

        public ReverseSyncFlowResult(String transactionId) {
            this.transactionId = transactionId;
            this.consistencyIssues = new ArrayList<>();
        }

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public boolean isSkipped() { return skipped; }
        public void setSkipped(boolean skipped) { this.skipped = skipped; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getFhirResourceId() { return fhirResourceId; }
        public void setFhirResourceId(String fhirResourceId) { this.fhirResourceId = fhirResourceId; }
        public String getUcsClientId() { return ucsClientId; }
        public void setUcsClientId(String ucsClientId) { this.ucsClientId = ucsClientId; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public boolean isConflictDetected() { return conflictDetected; }
        public void setConflictDetected(boolean conflictDetected) { this.conflictDetected = conflictDetected; }
        public String getConflictResolution() { return conflictResolution; }
        public void setConflictResolution(ConflictResolutionType resolutionType) { 
            this.conflictResolution = resolutionType != null ? resolutionType.name() : null;
        }
        public boolean isTransformationCompleted() { return transformationCompleted; }
        public void setTransformationCompleted(boolean transformationCompleted) { 
            this.transformationCompleted = transformationCompleted; 
        }
        public boolean isUcsStorageCompleted() { return ucsStorageCompleted; }
        public void setUcsStorageCompleted(boolean ucsStorageCompleted) { 
            this.ucsStorageCompleted = ucsStorageCompleted; 
        }
        public String getUcsOperationType() { return ucsOperationType; }
        public void setUcsOperationType(String ucsOperationType) { 
            this.ucsOperationType = ucsOperationType; 
        }
        public boolean isConsistencyVerified() { return consistencyVerified; }
        public void setConsistencyVerified(boolean consistencyVerified) { 
            this.consistencyVerified = consistencyVerified; 
        }
        public List<String> getConsistencyIssues() { return consistencyIssues; }
        public void setConsistencyIssues(List<String> consistencyIssues) { 
            this.consistencyIssues = consistencyIssues; 
        }
    }

    /**
     * Conflict check result.
     */
    private static class ConflictCheckResult {
        private boolean hasConflict;
        private ConflictType conflictType;

        public boolean hasConflict() { return hasConflict; }
        public void setHasConflict(boolean hasConflict) { this.hasConflict = hasConflict; }
        public ConflictType getConflictType() { return conflictType; }
        public void setConflictType(ConflictType conflictType) { this.conflictType = conflictType; }
    }

    /**
     * Conflict resolution result.
     */
    private static class ConflictResolution {
        private ConflictResolutionType resolutionType;
        private String reason;

        public ConflictResolutionType getResolutionType() { return resolutionType; }
        public void setResolutionType(ConflictResolutionType resolutionType) { 
            this.resolutionType = resolutionType; 
        }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * Resource version tracking for conflict detection.
     */
    private static class ResourceVersion {
        private final String resourceId;
        private final Date lastUpdated;

        public ResourceVersion(String resourceId, Date lastUpdated) {
            this.resourceId = resourceId;
            this.lastUpdated = lastUpdated;
        }

        public String getResourceId() { return resourceId; }
        public Date getLastUpdated() { return lastUpdated; }
    }

    /**
     * Conflict types.
     */
    private enum ConflictType {
        CIRCULAR_UPDATE,
        ALREADY_PROCESSED,
        VERSION_MISMATCH
    }

    /**
     * Conflict resolution types.
     */
    private enum ConflictResolutionType {
        SKIP,
        PROCEED,
        USE_FHIR_VERSION,
        USE_UCS_VERSION
    }
}
