package com.smartbridge.mediators.cht;

import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTChangesResponse;
import com.smartbridge.core.model.cht.CHTContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CHTSyncServiceTest {

    @Mock private CHTApiClient chtApiClient;
    @Mock private CHTIngestionFlowService ingestionFlowService;

    private CHTSyncService syncService;

    @BeforeEach
    void setUp() throws IOException {
        // Reset since_seq file to ensure test isolation
        Path seqFile = Paths.get("data/cht-since-seq.txt");
        if (Files.exists(seqFile)) {
            Files.delete(seqFile);
        }
        // Sync enabled, changes feed mode, batch size 10
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 10, true, true);
    }

    // ==================== Incremental Sync Tests ====================

    @Test
    void incrementalSync_ProcessesChangesFromFeed() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(
                createPersonChange("c1"),
                createReportChange("r1", "data_record")
            ),
            "100-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        IngestionFlowService.IngestionFlowResult successResult = createSuccessResult();
        when(ingestionFlowService.processContactIngestion(any())).thenReturn(successResult);
        when(ingestionFlowService.processReportIngestion(any())).thenReturn(successResult);

        syncService.incrementalSync();

        verify(chtApiClient).getChanges("0", 10);
        verify(ingestionFlowService).processContactIngestion(any());
        verify(ingestionFlowService).processReportIngestion(any());
    }

    @Test
    void incrementalSync_SkipsWhenDisabled() {
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 10, true, false);

        syncService.incrementalSync();

        verifyNoInteractions(chtApiClient);
        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void incrementalSync_HandlesNullResponse() throws Exception {
        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(null);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void incrementalSync_HandlesEmptyResults() throws Exception {
        CHTChangesResponse response = new CHTChangesResponse();
        response.setResults(List.of());
        response.setLastSeq("0");

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void incrementalSync_StopsWhenBatchSmallerThanLimit() throws Exception {
        // 2 results < batch size 10 → no second fetch
        CHTChangesResponse response = createChangesResponse(
            List.of(createPersonChange("c1")),
            "50-seq", null);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(chtApiClient, times(1)).getChanges(any(), eq(10));
    }

    @Test
    void incrementalSync_StopsWhenPendingZero() throws Exception {
        // 10 results = batch size but pending=0 → stop
        CHTChangesResponse response = createChangesResponse(
            createNPersonChanges(10),
            "200-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(chtApiClient, times(1)).getChanges(any(), eq(10));
    }

    @Test
    void incrementalSync_ContinuesWhenMorePending() throws Exception {
        // First batch: 10 results, pending=5 → fetch again
        CHTChangesResponse batch1 = createChangesResponse(
            createNPersonChanges(10), "100-seq", 5);
        // Second batch: 3 results < 10 → stop
        CHTChangesResponse batch2 = createChangesResponse(
            List.of(createPersonChange("extra-1")),
            "200-seq", 0);

        when(chtApiClient.getChanges(eq("0"), eq(10))).thenReturn(batch1);
        when(chtApiClient.getChanges(eq("100-seq"), eq(10))).thenReturn(batch2);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(chtApiClient).getChanges("0", 10);
        verify(chtApiClient).getChanges("100-seq", 10);
    }

    // ==================== Bulk Sync Tests ====================

    @Test
    void bulkSync_ResetsSeqAndClearsCache() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(createPersonChange("c1")),
            "50-seq", 0);

        when(chtApiClient.getChanges(eq("0"), eq(10))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.bulkSync();

        verify(ingestionFlowService).clearDeduplicationCache();
        verify(chtApiClient).getChanges("0", 10);
    }

    // ==================== Document Routing Tests ====================

    @Test
    void incrementalSync_RoutesPersonToContactIngestion() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(createPersonChange("p1")),
            "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processContactIngestion(any());
        verify(ingestionFlowService, never()).processPlaceIngestion(any());
        verify(ingestionFlowService, never()).processReportIngestion(any());
    }

    @Test
    void incrementalSync_RoutesClinicToPlaceIngestion() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(createPlaceChange("pl1", "clinic")),
            "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processPlaceIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processPlaceIngestion(any());
        verify(ingestionFlowService, never()).processContactIngestion(any());
    }

    @Test
    void incrementalSync_RoutesHealthCenterToPlaceIngestion() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(createPlaceChange("pl2", "health_center")),
            "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processPlaceIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processPlaceIngestion(any());
    }

    @Test
    void incrementalSync_RoutesDataRecordToReportIngestion() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(createReportChange("r1", "data_record")),
            "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);
        when(ingestionFlowService.processReportIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processReportIngestion(any());
        verify(ingestionFlowService, never()).processContactIngestion(any());
    }

    @Test
    void incrementalSync_SkipsDeletedDocuments() throws Exception {
        CHTChangesResponse.CHTChangeResult deleted = new CHTChangesResponse.CHTChangeResult();
        deleted.setId("del-1");
        deleted.setDeleted(true);

        CHTChangesResponse response = createChangesResponse(List.of(deleted), "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void incrementalSync_SkipsNullDoc() throws Exception {
        CHTChangesResponse.CHTChangeResult noDoc = new CHTChangesResponse.CHTChangeResult();
        noDoc.setId("nd-1");
        // doc is null

        CHTChangesResponse response = createChangesResponse(List.of(noDoc), "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void incrementalSync_SkipsUnknownDocType() throws Exception {
        CHTChangesResponse.CHTChangeResult unknown = new CHTChangesResponse.CHTChangeResult();
        unknown.setId("u-1");
        unknown.setDoc(Map.of("type", "unknown_type", "_id", "u-1"));

        CHTChangesResponse response = createChangesResponse(List.of(unknown), "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    // ==================== REST API Fallback Tests ====================

    @Test
    void incrementalSync_FallsBackToRestApi() throws Exception {
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 10, false, true);

        List<CHTContact> persons = List.of(createTestContact("p1"), createTestContact("p2"));
        when(chtApiClient.listPersons(10, 0)).thenReturn(persons);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(chtApiClient, never()).getChanges(any(), anyInt());
        verify(ingestionFlowService, times(2)).processContactIngestion(any());
    }

    @Test
    void restApiSync_StopsOnNullResult() throws Exception {
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 10, false, true);

        when(chtApiClient.listPersons(10, 0)).thenReturn(null);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void restApiSync_PaginatesUntilPartialBatch() throws Exception {
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 2, false, true);

        List<CHTContact> page1 = List.of(createTestContact("p1"), createTestContact("p2"));
        List<CHTContact> page2 = List.of(createTestContact("p3")); // < batch size → last page
        when(chtApiClient.listPersons(2, 0)).thenReturn(page1);
        when(chtApiClient.listPersons(2, 2)).thenReturn(page2);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(createSuccessResult());

        syncService.incrementalSync();

        verify(ingestionFlowService, times(3)).processContactIngestion(any());
    }

    // ==================== Since Seq Persistence Tests ====================

    @Test
    void loadSinceSeq_ReturnsZeroByDefault() {
        String seq = syncService.loadSinceSeq();
        assertEquals("0", seq);
    }

    @Test
    void saveSinceSeq_PersistsAndLoads() {
        syncService.saveSinceSeq("999-abc");
        String loaded = syncService.loadSinceSeq();
        assertEquals("999-abc", loaded);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void incrementalSync_ContinuesOnIndividualDocumentFailure() throws Exception {
        CHTChangesResponse response = createChangesResponse(
            List.of(
                createPersonChange("ok-1"),
                createPersonChange("fail-1"),
                createPersonChange("ok-2")
            ),
            "50-seq", 0);

        when(chtApiClient.getChanges(any(), eq(10))).thenReturn(response);

        IngestionFlowService.IngestionFlowResult success = createSuccessResult();
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(success)
            .thenThrow(new RuntimeException("transient error"))
            .thenReturn(success);

        syncService.incrementalSync();

        verify(ingestionFlowService, times(3)).processContactIngestion(any());
    }

    // ==================== Helpers ====================

    private CHTChangesResponse createChangesResponse(
            List<CHTChangesResponse.CHTChangeResult> results,
            String lastSeq, Integer pending) {
        CHTChangesResponse response = new CHTChangesResponse();
        response.setResults(results);
        response.setLastSeq(lastSeq);
        response.setPending(pending);
        return response;
    }

    private CHTChangesResponse.CHTChangeResult createPersonChange(String id) {
        CHTChangesResponse.CHTChangeResult change = new CHTChangesResponse.CHTChangeResult();
        change.setId(id);
        change.setDoc(Map.of(
            "_id", id,
            "type", "person",
            "name", "Test Person"
        ));
        return change;
    }

    private CHTChangesResponse.CHTChangeResult createPlaceChange(String id, String placeType) {
        CHTChangesResponse.CHTChangeResult change = new CHTChangesResponse.CHTChangeResult();
        change.setId(id);
        change.setDoc(Map.of(
            "_id", id,
            "type", placeType,
            "name", "Test Place"
        ));
        return change;
    }

    private CHTChangesResponse.CHTChangeResult createReportChange(String id, String type) {
        CHTChangesResponse.CHTChangeResult change = new CHTChangesResponse.CHTChangeResult();
        change.setId(id);
        change.setDoc(Map.of(
            "_id", id,
            "type", type,
            "form", "pregnancy_visit",
            "reported_date", 1700000000000L
        ));
        return change;
    }

    private List<CHTChangesResponse.CHTChangeResult> createNPersonChanges(int n) {
        return java.util.stream.IntStream.range(0, n)
            .mapToObj(i -> createPersonChange("c-" + i))
            .collect(java.util.stream.Collectors.toList());
    }

    private CHTContact createTestContact(String id) {
        CHTContact c = new CHTContact();
        c.setId(id);
        c.setName("Test Person");
        c.setType("person");
        return c;
    }

    private IngestionFlowService.IngestionFlowResult createSuccessResult() {
        IngestionFlowService.IngestionFlowResult result =
            new IngestionFlowService.IngestionFlowResult("test-tx");
        result.setSuccess(true);
        return result;
    }
}
