package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Concurrent transformation service that processes multiple transformation requests
 * simultaneously using a thread pool. Ensures thread safety and prevents data corruption
 * during concurrent operations.
 * 
 * Requirements: 7.2 - Concurrent processing capability
 */
@Service
public class ConcurrentTransformationService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentTransformationService.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final UCSToFHIRTransformer ucsToFHIRTransformer;
    private final FHIRToUCSTransformer fhirToUCSTransformer;
    private final Executor transformationExecutor;

    public ConcurrentTransformationService(
            UCSToFHIRTransformer ucsToFHIRTransformer,
            FHIRToUCSTransformer fhirToUCSTransformer,
            @Qualifier("transformationExecutor") Executor transformationExecutor) {
        this.ucsToFHIRTransformer = ucsToFHIRTransformer;
        this.fhirToUCSTransformer = fhirToUCSTransformer;
        this.transformationExecutor = transformationExecutor;
        logger.info("ConcurrentTransformationService initialized");
    }

    /**
     * Transform multiple UCS clients to FHIR resources concurrently.
     * Each transformation is executed in a separate thread from the pool.
     * 
     * @param ucsClients List of UCS clients to transform
     * @return List of transformation results (successful and failed)
     */
    public List<TransformationResult<FHIRResourceWrapper<? extends Resource>>> transformUCSToFHIRConcurrently(
            List<UCSClient> ucsClients) {
        return transformUCSToFHIRConcurrently(ucsClients, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Transform multiple UCS clients to FHIR resources concurrently with custom timeout.
     * 
     * @param ucsClients List of UCS clients to transform
     * @param timeoutSeconds Maximum time to wait for all transformations
     * @return List of transformation results (successful and failed)
     */
    public List<TransformationResult<FHIRResourceWrapper<? extends Resource>>> transformUCSToFHIRConcurrently(
            List<UCSClient> ucsClients, long timeoutSeconds) {
        
        if (ucsClients == null || ucsClients.isEmpty()) {
            logger.warn("Empty or null UCS client list provided for concurrent transformation");
            return new ArrayList<>();
        }

        logger.info("Starting concurrent UCS to FHIR transformation for {} clients", ucsClients.size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<TransformationResult<FHIRResourceWrapper<? extends Resource>>>> futures = 
            new ArrayList<>();

        // Submit all transformation tasks
        for (int i = 0; i < ucsClients.size(); i++) {
            final UCSClient ucsClient = ucsClients.get(i);
            final int index = i;

            CompletableFuture<TransformationResult<FHIRResourceWrapper<? extends Resource>>> future = 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("Transforming UCS client {} of {}", index + 1, ucsClients.size());
                        FHIRResourceWrapper<? extends Resource> result = 
                            ucsToFHIRTransformer.transformUCSToFHIR(ucsClient);
                        return TransformationResult.success(result, index);
                    } catch (TransformationException e) {
                        logger.error("Transformation failed for UCS client {} of {}: {}", 
                            index + 1, ucsClients.size(), e.getMessage());
                        return TransformationResult.failure(e, index);
                    }
                }, transformationExecutor);

            futures.add(future);
        }

        // Wait for all transformations to complete
        List<TransformationResult<FHIRResourceWrapper<? extends Resource>>> results = new ArrayList<>();
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);

            // Collect results
            for (CompletableFuture<TransformationResult<FHIRResourceWrapper<? extends Resource>>> future : futures) {
                results.add(future.get());
            }

            long duration = System.currentTimeMillis() - startTime;
            long successCount = results.stream().filter(TransformationResult::isSuccess).count();
            long failureCount = results.size() - successCount;

            logger.info("Concurrent UCS to FHIR transformation completed: {} successful, {} failed, duration={}ms",
                successCount, failureCount, duration);

        } catch (TimeoutException e) {
            logger.error("Concurrent transformation timed out after {} seconds", timeoutSeconds);
            
            // Collect completed results
            for (CompletableFuture<TransformationResult<FHIRResourceWrapper<? extends Resource>>> future : futures) {
                if (future.isDone()) {
                    try {
                        results.add(future.get());
                    } catch (Exception ex) {
                        logger.error("Error retrieving completed transformation result", ex);
                    }
                } else {
                    results.add(TransformationResult.timeout(results.size()));
                }
            }
        } catch (InterruptedException e) {
            logger.error("Concurrent transformation interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Error during concurrent transformation", e);
        }

        return results;
    }

    /**
     * Transform multiple FHIR resources to UCS clients concurrently.
     * Each transformation is executed in a separate thread from the pool.
     * 
     * @param fhirWrappers List of FHIR resource wrappers to transform
     * @return List of transformation results (successful and failed)
     */
    public List<TransformationResult<UCSClient>> transformFHIRToUCSConcurrently(
            List<FHIRResourceWrapper<? extends Resource>> fhirWrappers) {
        return transformFHIRToUCSConcurrently(fhirWrappers, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Transform multiple FHIR resources to UCS clients concurrently with custom timeout.
     * 
     * @param fhirWrappers List of FHIR resource wrappers to transform
     * @param timeoutSeconds Maximum time to wait for all transformations
     * @return List of transformation results (successful and failed)
     */
    public List<TransformationResult<UCSClient>> transformFHIRToUCSConcurrently(
            List<FHIRResourceWrapper<? extends Resource>> fhirWrappers, long timeoutSeconds) {
        
        if (fhirWrappers == null || fhirWrappers.isEmpty()) {
            logger.warn("Empty or null FHIR wrapper list provided for concurrent transformation");
            return new ArrayList<>();
        }

        logger.info("Starting concurrent FHIR to UCS transformation for {} resources", fhirWrappers.size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<TransformationResult<UCSClient>>> futures = new ArrayList<>();

        // Submit all transformation tasks
        for (int i = 0; i < fhirWrappers.size(); i++) {
            final FHIRResourceWrapper<? extends Resource> fhirWrapper = fhirWrappers.get(i);
            final int index = i;

            CompletableFuture<TransformationResult<UCSClient>> future = 
                CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("Transforming FHIR resource {} of {}", index + 1, fhirWrappers.size());
                        UCSClient result = fhirToUCSTransformer.transformFHIRToUCS(fhirWrapper);
                        return TransformationResult.success(result, index);
                    } catch (TransformationException e) {
                        logger.error("Transformation failed for FHIR resource {} of {}: {}", 
                            index + 1, fhirWrappers.size(), e.getMessage());
                        return TransformationResult.failure(e, index);
                    }
                }, transformationExecutor);

            futures.add(future);
        }

        // Wait for all transformations to complete
        List<TransformationResult<UCSClient>> results = new ArrayList<>();
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allFutures.get(timeoutSeconds, TimeUnit.SECONDS);

            // Collect results
            for (CompletableFuture<TransformationResult<UCSClient>> future : futures) {
                results.add(future.get());
            }

            long duration = System.currentTimeMillis() - startTime;
            long successCount = results.stream().filter(TransformationResult::isSuccess).count();
            long failureCount = results.size() - successCount;

            logger.info("Concurrent FHIR to UCS transformation completed: {} successful, {} failed, duration={}ms",
                successCount, failureCount, duration);

        } catch (TimeoutException e) {
            logger.error("Concurrent transformation timed out after {} seconds", timeoutSeconds);
            
            // Collect completed results
            for (CompletableFuture<TransformationResult<UCSClient>> future : futures) {
                if (future.isDone()) {
                    try {
                        results.add(future.get());
                    } catch (Exception ex) {
                        logger.error("Error retrieving completed transformation result", ex);
                    }
                } else {
                    results.add(TransformationResult.timeout(results.size()));
                }
            }
        } catch (InterruptedException e) {
            logger.error("Concurrent transformation interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Error during concurrent transformation", e);
        }

        return results;
    }

    /**
     * Result wrapper for transformation operations.
     * Contains either the successful result or error information.
     */
    public static class TransformationResult<T> {
        private final boolean success;
        private final T result;
        private final TransformationException error;
        private final int index;
        private final boolean timeout;

        private TransformationResult(boolean success, T result, TransformationException error, 
                                    int index, boolean timeout) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.index = index;
            this.timeout = timeout;
        }

        public static <T> TransformationResult<T> success(T result, int index) {
            return new TransformationResult<>(true, result, null, index, false);
        }

        public static <T> TransformationResult<T> failure(TransformationException error, int index) {
            return new TransformationResult<>(false, null, error, index, false);
        }

        public static <T> TransformationResult<T> timeout(int index) {
            return new TransformationResult<>(false, null, 
                new TransformationException("Transformation timed out"), index, true);
        }

        public boolean isSuccess() {
            return success;
        }

        public T getResult() {
            return result;
        }

        public TransformationException getError() {
            return error;
        }

        public int getIndex() {
            return index;
        }

        public boolean isTimeout() {
            return timeout;
        }
    }
}
