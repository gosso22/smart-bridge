package com.smartbridge.mediators.cht;

import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTChangesResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for incremental sync checkpoint consistency.
 * Feature: cht-integration, Property 6: Incremental Sync Checkpoint Consistency
 *
 * Persisted since_seq always matches last_seq of most recently completed batch;
 * restart resumes without skip or reprocess.
 * Validates: Requirements 4.1-4.2, 4.7-4.8
 */
class CHTSyncCheckpointPropertyTest {

    // ==================== Checkpoint Matches Last Batch ====================

    @Property(tries = 50)
    void sinceSeqMatchesLastSeqOfMostRecentBatch(
            @ForAll("seqToken") String lastSeq) throws Exception {

        cleanSeqFile();
        CHTApiClient apiClient = mock(CHTApiClient.class);
        CHTIngestionFlowService flowService = mock(CHTIngestionFlowService.class);

        CHTSyncService syncService = new CHTSyncService(
            apiClient, flowService, 100, true, true);

        // Return a batch with 1 document and the given last_seq
        CHTChangesResponse response = createResponse(1, lastSeq, 0);
        when(apiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(flowService.processContactIngestion(any()))
            .thenReturn(successResult());

        syncService.incrementalSync();

        String savedSeq = syncService.loadSinceSeq();
        assertEquals(lastSeq, savedSeq,
            "Persisted since_seq must match last_seq of the most recent batch");
    }

    // ==================== Restart Resumes From Saved Checkpoint ====================

    @Property(tries = 30)
    void restartResumesFromSavedCheckpoint(
            @ForAll("seqToken") String lastSeq) throws Exception {

        cleanSeqFile();
        CHTApiClient apiClient = mock(CHTApiClient.class);
        CHTIngestionFlowService flowService = mock(CHTIngestionFlowService.class);

        CHTSyncService syncService = new CHTSyncService(
            apiClient, flowService, 100, true, true);

        // First sync: process batch, save checkpoint
        CHTChangesResponse batch1 = createResponse(1, lastSeq, 0);
        when(apiClient.getChanges(eq("0"), eq(100))).thenReturn(batch1);
        when(flowService.processContactIngestion(any())).thenReturn(successResult());

        syncService.incrementalSync();

        // Simulate restart: create new service instance, verify it starts from saved seq
        CHTSyncService restarted = new CHTSyncService(
            apiClient, flowService, 100, true, true);

        String resumeSeq = restarted.loadSinceSeq();
        assertEquals(lastSeq, resumeSeq,
            "Restarted service should resume from the saved checkpoint");
    }

    // ==================== Multi-Batch Checkpoint Updates ====================

    @Property(tries = 20)
    void multiBatchCheckpointUpdatesCorrectly(
            @ForAll @IntRange(min = 2, max = 5) int batchCount) throws Exception {

        cleanSeqFile();
        CHTApiClient apiClient = mock(CHTApiClient.class);
        CHTIngestionFlowService flowService = mock(CHTIngestionFlowService.class);

        int batchSize = 2;
        CHTSyncService syncService = new CHTSyncService(
            apiClient, flowService, batchSize, true, true);

        when(flowService.processContactIngestion(any())).thenReturn(successResult());

        // Set up multiple batches: each batch has exactly batchSize docs (except last)
        String currentSeq = "0";
        String finalSeq = null;
        for (int i = 0; i < batchCount; i++) {
            String seq = (i + 1) * 100 + "-seq";
            int pending = (i < batchCount - 1) ? (batchCount - i - 1) * batchSize : 0;
            int docCount = (i < batchCount - 1) ? batchSize : 1; // last batch smaller

            CHTChangesResponse response = createResponse(docCount, seq, pending);
            when(apiClient.getChanges(eq(currentSeq), eq(batchSize))).thenReturn(response);

            currentSeq = seq;
            finalSeq = seq;
        }

        syncService.incrementalSync();

        String savedSeq = syncService.loadSinceSeq();
        assertEquals(finalSeq, savedSeq,
            "After " + batchCount + " batches, checkpoint should be the last batch's seq");
    }

    // ==================== Bulk Sync Resets Checkpoint ====================

    @Property(tries = 20)
    void bulkSyncResetsCheckpointToZero(
            @ForAll("seqToken") String previousSeq) throws Exception {

        cleanSeqFile();
        CHTApiClient apiClient = mock(CHTApiClient.class);
        CHTIngestionFlowService flowService = mock(CHTIngestionFlowService.class);

        CHTSyncService syncService = new CHTSyncService(
            apiClient, flowService, 100, true, true);

        // Set a checkpoint
        syncService.saveSinceSeq(previousSeq);
        assertEquals(previousSeq, syncService.loadSinceSeq());

        // Bulk sync should start from "0"
        CHTChangesResponse response = createResponse(1, "new-seq-1", 0);
        when(apiClient.getChanges(eq("0"), eq(100))).thenReturn(response);
        when(flowService.processContactIngestion(any())).thenReturn(successResult());

        syncService.bulkSync();

        String savedSeq = syncService.loadSinceSeq();
        assertEquals("new-seq-1", savedSeq,
            "After bulk sync, checkpoint should be the new last_seq, not the previous one");
    }

    // ==================== Empty Batch Does Not Advance Checkpoint ====================

    @Property(tries = 20)
    void emptyBatchDoesNotAdvanceCheckpoint(
            @ForAll("seqToken") String initialSeq) throws Exception {

        cleanSeqFile();
        CHTApiClient apiClient = mock(CHTApiClient.class);
        CHTIngestionFlowService flowService = mock(CHTIngestionFlowService.class);

        CHTSyncService syncService = new CHTSyncService(
            apiClient, flowService, 100, true, true);

        syncService.saveSinceSeq(initialSeq);

        // Return empty response
        CHTChangesResponse emptyResponse = new CHTChangesResponse();
        emptyResponse.setResults(List.of());
        emptyResponse.setLastSeq(initialSeq);
        when(apiClient.getChanges(eq(initialSeq), eq(100))).thenReturn(emptyResponse);

        syncService.incrementalSync();

        assertEquals(initialSeq, syncService.loadSinceSeq(),
            "Empty batch should not change the checkpoint");
    }

    // ==================== Helpers ====================

    private CHTChangesResponse createResponse(int docCount, String lastSeq, int pending) {
        CHTChangesResponse response = new CHTChangesResponse();
        List<CHTChangesResponse.CHTChangeResult> results = new ArrayList<>();
        for (int i = 0; i < docCount; i++) {
            CHTChangesResponse.CHTChangeResult change = new CHTChangesResponse.CHTChangeResult();
            change.setId("doc-" + i);
            change.setDoc(Map.of("_id", "doc-" + i, "type", "person", "name", "Test"));
            results.add(change);
        }
        response.setResults(results);
        response.setLastSeq(lastSeq);
        response.setPending(pending);
        return response;
    }

    private IngestionFlowService.IngestionFlowResult successResult() {
        IngestionFlowService.IngestionFlowResult result =
            new IngestionFlowService.IngestionFlowResult("test-tx");
        result.setSuccess(true);
        return result;
    }

    private void cleanSeqFile() {
        try {
            Path seqFile = Paths.get("data/cht-since-seq.txt");
            if (Files.exists(seqFile)) {
                Files.delete(seqFile);
            }
        } catch (IOException ignored) {}
    }

    @Provide
    Arbitrary<String> seqToken() {
        return Arbitraries.integers().between(1, 99999)
            .map(n -> n + "-g1AAAABXeJzLYWBg4Mhg");
    }
}
