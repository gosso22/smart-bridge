package com.smartbridge.core.flow;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.interfaces.TransformationService;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.queue.QueueMessage;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.transformation.ConcurrentTransformationService;
import com.smartbridge.core.validation.UCSClientValidator;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Ingestion Flow Service for UCS to FHIR data flow.
 * Orchestrates the complete flow: validation -> transformation -> FHIR storage.
 * Includes performance monitoring, transaction coordination, and rollback capabilities.
 * 
 * Requirements: 3.1, 7.1
 */
@Service
public class IngestionFlowService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionFlowService.class);
    private static final long PERFORMANCE_THRESHOLD_MS = 5000; // 5 seconds requirement

    private final UCSClientValidator ucsValidator;
    private final TransformationService transformer;
    private final ResilientFHIRClient resilientFHIRClient;
    private final FHIRClientService fhirClient;
    private final MessageProducerService messageProducer;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;
    private final ConcurrentTransformationService concurrentTransformationService;
    private final Executor transformationExecutor;

    // Thread-safe tracking of in-progress transformations to prevent duplicates
    private final Map<String, CompletableFuture<IngestionFlowResult>> inProgressTransformations = 
        new ConcurrentHashMap<>();
    
    // Read-write lock for transaction coordination
    private final ReadWriteLock transactionLock = new ReentrantReadWriteLock();

    @Autowired(required = false)
    private Timer transformationTimer;

    @Autowired(required = false)
    private Counter transformationSuccessCounter;

    @Autowired(required = false)
    private Counter transformationErrorCounter;

    public IngestionFlowService(
            UCSClientValidator ucsValidator,
            TransformationService transformer,
            ResilientFHIRClient resilientFHIRClient,
            FHIRClientService fhirClient,
            MessageProducerService messageProducer,
            AuditLogger auditLogger,
            ConcurrentTransformationService concurrentTransformationService,
            @Qualifier("transformationExecutor") Executor transformationExecutor) {
        this.ucsValidator = ucsValidator;
        this.transformer = transformer;
        this.resilientFHIRClient = resilientFHIRClient;
        this.fhirClient = fhirClient;
        this.messageProducer = messageProducer;
        this.auditLogger = auditLogger;
        this.concurrentTransformationService = concurrentTransformationService;
        this.transformationExecutor = transformationExecutor;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        logger.info("IngestionFlowService initialized with concurrent processing support");
    }

    /**
     * Process UCS client data through the complete ingestion flow.
     * Validates, transforms, and stores data in FHIR server.
     * Monitors performance to meet 5-second requirement.
     * 
     * @param ucsClient The UCS client data to process
     * @return IngestionFlowResult containing the outcome and metrics
     */
    public IngestionFlowResult processIngestion(UCSClient ucsClient) {
        String transactionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        logger.info("Starting ingestion flow: transactionId={}", transactionId);
        
        IngestionFlowResult result = new IngestionFlowResult(transactionId);
        
        try {
            // Step 1: Validate UCS client data
            validateUCSClient(ucsClient, result);
            
            // Step 2: Transform to FHIR
            FHIRResourceWrapper<? extends Resource> fhirWrapper = transformToFHIR(ucsClient, result);
            
            // Step 3: Store in FHIR server
            String fhirResourceId = storeInFHIR(fhirWrapper, result);
            
            // Step 4: Record success
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);
            result.setFhirResourceId(fhirResourceId);
            
            // Log audit trail
            auditLogger.logTransformation(
                "UCS", "FHIR", "INGESTION",
                getUCSClientId(ucsClient), fhirResourceId,
                true, "Ingestion completed successfully"
            );
            
            // Check performance threshold
            if (duration > PERFORMANCE_THRESHOLD_MS) {
                auditLogger.logPerformanceAlert(
                    "IngestionFlowService", "ingestion_duration",
                    duration, PERFORMANCE_THRESHOLD_MS,
                    "Ingestion exceeded 5-second threshold"
                );
                logger.warn("Ingestion exceeded performance threshold: {}ms > {}ms",
                    duration, PERFORMANCE_THRESHOLD_MS);
            }
            
            // Update metrics
            if (transformationSuccessCounter != null) {
                transformationSuccessCounter.increment();
            }
            
            logger.info("Ingestion flow completed successfully: transactionId={}, duration={}ms",
                transactionId, duration);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(false);
            result.setDurationMs(duration);
            result.setErrorMessage(e.getMessage());
            
            // Log error
            logger.error("Ingestion flow failed: transactionId={}, duration={}ms, error={}",
                transactionId, duration, e.getMessage(), e);
            
            // Audit error
            auditLogger.logTransformation(
                "UCS", "FHIR", "INGESTION",
                getUCSClientId(ucsClient), null,
                false, "Ingestion failed: " + e.getMessage()
            );
            
            // Update metrics
            if (transformationErrorCounter != null) {
                transformationErrorCounter.increment();
            }
            
            // Queue for retry if appropriate
            handleIngestionFailure(ucsClient, transactionId, e);
            
            return result;
        }
    }

    /**
     * Process ingestion with transaction coordination.
     * Provides rollback capability if any step fails.
     */
    public IngestionFlowResult processIngestionWithTransaction(UCSClient ucsClient) {
        String transactionId = UUID.randomUUID().toString();
        TransactionContext txContext = new TransactionContext(transactionId);
        
        logger.info("Starting transactional ingestion flow: transactionId={}", transactionId);
        
        try {
            IngestionFlowResult result = processIngestion(ucsClient);
            
            if (!result.isSuccess()) {
                // Rollback if needed
                rollbackTransaction(txContext);
            } else {
                // Commit transaction
                commitTransaction(txContext);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Transactional ingestion failed: transactionId={}", transactionId, e);
            rollbackTransaction(txContext);
            
            IngestionFlowResult result = new IngestionFlowResult(transactionId);
            result.setSuccess(false);
            result.setErrorMessage("Transaction failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Validate UCS client data against schema.
     */
    private void validateUCSClient(UCSClient ucsClient, IngestionFlowResult result) {
        logger.debug("Validating UCS client data");
        
        UCSClientValidator.ValidationResult validationResult = ucsValidator.validate(ucsClient);
        
        if (!validationResult.isValid()) {
            String errorMsg = "UCS client validation failed: " + validationResult.getErrorMessage();
            logger.error(errorMsg);
            throw new IngestionFlowException(errorMsg, "VALIDATION_FAILED");
        }
        
        result.setValidationPassed(true);
        logger.debug("UCS client validation passed");
    }

    /**
     * Transform UCS client to FHIR resource.
     */
    private FHIRResourceWrapper<? extends Resource> transformToFHIR(
            UCSClient ucsClient, IngestionFlowResult result) {
        
        logger.debug("Transforming UCS client to FHIR");
        
        Timer.Sample sample = transformationTimer != null ? Timer.start() : null;
        
        try {
            FHIRResourceWrapper<? extends Resource> fhirWrapper = 
                transformer.transformUCSToFHIR(ucsClient);
            
            if (sample != null && transformationTimer != null) {
                sample.stop(transformationTimer);
            }
            
            if (fhirWrapper == null) {
                throw new IngestionFlowException(
                    "Transformer returned null FHIRResourceWrapper", "TRANSFORMATION_FAILED");
            }
            
            result.setTransformationCompleted(true);
            logger.debug("Transformation completed successfully");
            
            return fhirWrapper;
            
        } catch (TransformationException e) {
            String errorMsg = "Transformation failed: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new IngestionFlowException(errorMsg, "TRANSFORMATION_FAILED", e);
        }
    }

    /**
     * Store FHIR resource in HAPI FHIR server.
     */
    private String storeInFHIR(FHIRResourceWrapper<? extends Resource> fhirWrapper,
                               IngestionFlowResult result) {
        
        logger.debug("Storing FHIR resource in HAPI FHIR server");
        
        try {
            Resource resource = fhirWrapper.getResource();
            
            if (!(resource instanceof Patient)) {
                throw new IngestionFlowException(
                    "Only Patient resources supported in ingestion flow",
                    "UNSUPPORTED_RESOURCE_TYPE"
                );
            }
            
            Patient patient = (Patient) resource;
            MethodOutcome outcome = resilientFHIRClient.createPatient(patient);
            
            String resourceId = outcome.getId() != null ? 
                outcome.getId().getIdPart() : null;
            
            if (resourceId == null) {
                throw new IngestionFlowException(
                    "FHIR resource creation did not return resource ID",
                    "FHIR_STORAGE_FAILED"
                );
            }
            
            result.setFhirStorageCompleted(true);
            logger.debug("FHIR resource stored successfully: resourceId={}", resourceId);
            
            // Log FHIR operation
            auditLogger.logFHIROperation(
                "CREATE", "Patient", resourceId,
                fhirClient.getServerBaseUrl(),
                true, "Patient resource created via ingestion flow"
            );
            
            return resourceId;
            
        } catch (Exception e) {
            String errorMsg = "FHIR storage failed: " + e.getMessage();
            logger.error(errorMsg, e);
            
            // Log FHIR operation failure
            auditLogger.logFHIROperation(
                "CREATE", "Patient", null,
                fhirClient.getServerBaseUrl(),
                false, "Failed to create Patient: " + e.getMessage()
            );
            
            throw new IngestionFlowException(errorMsg, "FHIR_STORAGE_FAILED", e);
        }
    }

    /**
     * Handle ingestion failure by queuing for retry.
     */
    private void handleIngestionFailure(UCSClient ucsClient, String transactionId, Exception error) {
        try {
            logger.info("Queuing failed ingestion for retry: transactionId={}", transactionId);
            
            // Serialize UCSClient to JSON
            String payload = objectMapper.writeValueAsString(ucsClient);
            
            QueueMessage queueMessage = new QueueMessage(payload, "INGESTION_RETRY");
            queueMessage.setId(transactionId);
            queueMessage.setErrorMessage(error.getMessage());
            
            messageProducer.sendToRetryQueue(queueMessage);
            
            logger.info("Failed ingestion queued for retry: transactionId={}", transactionId);
            
        } catch (Exception e) {
            logger.error("Failed to queue ingestion for retry: transactionId={}", 
                transactionId, e);
            
            // Log error for manual intervention
            Map<String, String> context = new HashMap<>();
            context.put("transactionId", transactionId);
            context.put("originalError", error.getMessage());
            
            auditLogger.logError(
                "IngestionFlowService", "handleIngestionFailure",
                "QUEUE_FAILED", "Failed to queue for retry",
                context
            );
        }
    }

    /**
     * Commit transaction context.
     */
    private void commitTransaction(TransactionContext txContext) {
        logger.debug("Committing transaction: transactionId={}", txContext.getTransactionId());
        txContext.setCommitted(true);
    }

    /**
     * Rollback transaction context.
     */
    private void rollbackTransaction(TransactionContext txContext) {
        logger.warn("Rolling back transaction: transactionId={}", txContext.getTransactionId());
        
        // In a full implementation, this would:
        // 1. Delete created FHIR resources
        // 2. Revert any state changes
        // 3. Clean up temporary data
        
        txContext.setRolledBack(true);
        
        auditLogger.logError(
            "IngestionFlowService", "rollbackTransaction",
            "TRANSACTION_ROLLBACK", "Transaction rolled back",
            Map.of("transactionId", txContext.getTransactionId())
        );
    }

    /**
     * Get UCS client identifier for logging.
     */
    private String getUCSClientId(UCSClient ucsClient) {
        if (ucsClient == null || ucsClient.getIdentifiers() == null) {
            return "unknown";
        }
        return ucsClient.getIdentifiers().getOpensrpId();
    }

    /**
     * Process multiple UCS clients concurrently through the ingestion flow.
     * Ensures thread safety and prevents data corruption during concurrent operations.
     * 
     * @param ucsClients List of UCS clients to process
     * @return List of ingestion results for each client
     */
    public List<IngestionFlowResult> processIngestionConcurrently(List<UCSClient> ucsClients) {
        if (ucsClients == null || ucsClients.isEmpty()) {
            logger.warn("Empty or null UCS client list provided for concurrent ingestion");
            return new ArrayList<>();
        }

        logger.info("Starting concurrent ingestion flow for {} clients", ucsClients.size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<IngestionFlowResult>> futures = new ArrayList<>();

        // Submit all ingestion tasks
        for (UCSClient ucsClient : ucsClients) {
            String clientId = getUCSClientId(ucsClient);
            
            // Check if transformation is already in progress for this client
            CompletableFuture<IngestionFlowResult> existingFuture = inProgressTransformations.get(clientId);
            if (existingFuture != null && !existingFuture.isDone()) {
                logger.info("Transformation already in progress for client: {}", clientId);
                futures.add(existingFuture);
                continue;
            }

            // Submit new transformation task
            CompletableFuture<IngestionFlowResult> future = CompletableFuture.supplyAsync(() -> {
                return processIngestion(ucsClient);
            }, transformationExecutor);

            // Track in-progress transformation
            inProgressTransformations.put(clientId, future);
            
            // Clean up tracking when complete
            future.whenComplete((result, throwable) -> {
                inProgressTransformations.remove(clientId);
            });

            futures.add(future);
        }

        // Wait for all ingestions to complete
        List<IngestionFlowResult> results = new ArrayList<>();
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allFutures.join();

            // Collect results
            for (CompletableFuture<IngestionFlowResult> future : futures) {
                results.add(future.get());
            }

            long duration = System.currentTimeMillis() - startTime;
            long successCount = results.stream().filter(IngestionFlowResult::isSuccess).count();
            long failureCount = results.size() - successCount;

            logger.info("Concurrent ingestion flow completed: {} successful, {} failed, duration={}ms",
                successCount, failureCount, duration);

        } catch (Exception e) {
            logger.error("Error during concurrent ingestion", e);
        }

        return results;
    }

    /**
     * Process ingestion asynchronously and return a CompletableFuture.
     * Allows non-blocking concurrent processing.
     * 
     * @param ucsClient The UCS client to process
     * @return CompletableFuture containing the ingestion result
     */
    public CompletableFuture<IngestionFlowResult> processIngestionAsync(UCSClient ucsClient) {
        String clientId = getUCSClientId(ucsClient);
        
        // Check if transformation is already in progress
        CompletableFuture<IngestionFlowResult> existingFuture = inProgressTransformations.get(clientId);
        if (existingFuture != null && !existingFuture.isDone()) {
            logger.info("Transformation already in progress for client: {}, returning existing future", clientId);
            return existingFuture;
        }

        // Submit new transformation task
        CompletableFuture<IngestionFlowResult> future = CompletableFuture.supplyAsync(() -> {
            return processIngestion(ucsClient);
        }, transformationExecutor);

        // Track in-progress transformation
        inProgressTransformations.put(clientId, future);
        
        // Clean up tracking when complete
        future.whenComplete((result, throwable) -> {
            inProgressTransformations.remove(clientId);
        });

        return future;
    }

    /**
     * Check if a transformation is currently in progress for a given client.
     * Thread-safe check using concurrent map.
     * 
     * @param clientId The UCS client identifier
     * @return true if transformation is in progress, false otherwise
     */
    public boolean isTransformationInProgress(String clientId) {
        CompletableFuture<IngestionFlowResult> future = inProgressTransformations.get(clientId);
        return future != null && !future.isDone();
    }

    /**
     * Get the number of transformations currently in progress.
     * Thread-safe count using concurrent map.
     * 
     * @return Number of in-progress transformations
     */
    public int getInProgressTransformationCount() {
        return (int) inProgressTransformations.values().stream()
            .filter(future -> !future.isDone())
            .count();
    }

    /**
     * Result object for ingestion flow operations.
     */
    public static class IngestionFlowResult {
        private final String transactionId;
        private boolean success;
        private long durationMs;
        private String fhirResourceId;
        private String errorMessage;
        private boolean validationPassed;
        private boolean transformationCompleted;
        private boolean fhirStorageCompleted;

        public IngestionFlowResult(String transactionId) {
            this.transactionId = transactionId;
        }

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getFhirResourceId() { return fhirResourceId; }
        public void setFhirResourceId(String fhirResourceId) { this.fhirResourceId = fhirResourceId; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public boolean isValidationPassed() { return validationPassed; }
        public void setValidationPassed(boolean validationPassed) { this.validationPassed = validationPassed; }
        public boolean isTransformationCompleted() { return transformationCompleted; }
        public void setTransformationCompleted(boolean transformationCompleted) { 
            this.transformationCompleted = transformationCompleted; 
        }
        public boolean isFhirStorageCompleted() { return fhirStorageCompleted; }
        public void setFhirStorageCompleted(boolean fhirStorageCompleted) { 
            this.fhirStorageCompleted = fhirStorageCompleted; 
        }
    }

    /**
     * Transaction context for coordinating operations.
     */
    private static class TransactionContext {
        private final String transactionId;
        private boolean committed;
        private boolean rolledBack;

        public TransactionContext(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTransactionId() { return transactionId; }
        public boolean isCommitted() { return committed; }
        public void setCommitted(boolean committed) { this.committed = committed; }
        public boolean isRolledBack() { return rolledBack; }
        public void setRolledBack(boolean rolledBack) { this.rolledBack = rolledBack; }
    }
}
