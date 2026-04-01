package com.smartbridge.mediators.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTChangesResponse;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.mediators.cht.CHTApiClient;
import com.smartbridge.mediators.cht.CHTSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CHTSyncService document routing with real CHT document structures.
 * Verifies that the changes feed correctly routes person, clinic, and data_record documents
 * to the appropriate ingestion methods.
 */
@ExtendWith(MockitoExtension.class)
class CHTSyncServiceIntegrationTest {

    @Mock private CHTApiClient chtApiClient;
    @Mock private CHTIngestionFlowService ingestionFlowService;

    private CHTSyncService syncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        Path seqFile = Paths.get("data/cht-since-seq.txt");
        if (Files.exists(seqFile)) {
            Files.delete(seqFile);
        }
        syncService = new CHTSyncService(
            chtApiClient, ingestionFlowService, 100, true, true);
    }

    // ==================== Real Document Routing ====================

    @Test
    void routesRealPersonDocumentToContactIngestion() throws Exception {
        Map<String, Object> personDoc = new LinkedHashMap<>();
        personDoc.put("_id", "3973f2aa-3032-41e5-9418-5c493927b2c2");
        personDoc.put("_rev", "1-d0b29abb43b7802bfb3ce84185b9f9af");
        personDoc.put("name", "Ahmed Ali Hassan");
        personDoc.put("type", "person");
        personDoc.put("sex", "male");
        personDoc.put("date_of_birth", "1991-01-01");
        personDoc.put("phone", "0777123456");
        personDoc.put("reported_date", 1773666649147L);
        personDoc.put("parent", Map.of("_id", "88f6dc1f-b4d8-4238-90de-ca274921be9b"));

        CHTChangesResponse response = createResponseWithDocs(
            List.of(personDoc), "243-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any()))
            .thenReturn(successResult());

        syncService.incrementalSync();

        ArgumentCaptor<CHTContact> captor = ArgumentCaptor.forClass(CHTContact.class);
        verify(ingestionFlowService).processContactIngestion(captor.capture());
        verify(ingestionFlowService, never()).processPlaceIngestion(any());
        verify(ingestionFlowService, never()).processReportIngestion(any());

        CHTContact captured = captor.getValue();
        assertEquals("3973f2aa-3032-41e5-9418-5c493927b2c2", captured.getId());
        assertEquals("Ahmed Ali Hassan", captured.getName());
        assertEquals("person", captured.getType());
        assertEquals("male", captured.getSex());
    }

    @Test
    void routesRealClinicDocumentToPlaceIngestion() throws Exception {
        Map<String, Object> clinicDoc = new LinkedHashMap<>();
        clinicDoc.put("_id", "88f6dc1f-b4d8-4238-90de-ca274921be9b");
        clinicDoc.put("_rev", "1-d1c46c8e46e843115ac5a79998e7348c");
        clinicDoc.put("name", "Ahmed Ali Hassan - 12/56");
        clinicDoc.put("type", "clinic");
        clinicDoc.put("reported_date", 1773666649147L);
        clinicDoc.put("parent", Map.of("_id", "41da8c22-6f4e-5d8c-9a6f-52bd10f29b60"));
        clinicDoc.put("contact", Map.of("_id", "3973f2aa-3032-41e5-9418-5c493927b2c2"));

        CHTChangesResponse response = createResponseWithDocs(
            List.of(clinicDoc), "244-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processPlaceIngestion(any()))
            .thenReturn(successResult());

        syncService.incrementalSync();

        ArgumentCaptor<CHTPlace> captor = ArgumentCaptor.forClass(CHTPlace.class);
        verify(ingestionFlowService).processPlaceIngestion(captor.capture());
        verify(ingestionFlowService, never()).processContactIngestion(any());

        CHTPlace captured = captor.getValue();
        assertEquals("88f6dc1f-b4d8-4238-90de-ca274921be9b", captured.getId());
        assertEquals("clinic", captured.getType());
    }

    @Test
    void routesRealDataRecordToReportIngestion() throws Exception {
        Map<String, Object> reportDoc = new LinkedHashMap<>();
        reportDoc.put("_id", "42762c2b-b082-445f-9cc7-17093e5520a8");
        reportDoc.put("_rev", "1-99db299061fa55824a3bc5669dd48b20");
        reportDoc.put("type", "data_record");
        reportDoc.put("form", "infant_child");
        reportDoc.put("reported_date", 1774948502253L);
        reportDoc.put("contact", Map.of("_id", "f9120a3c-1b55-5d5e-87cc-6b6773d7984a"));
        reportDoc.put("fields", Map.of(
            "patient_id", "9339db36-040b-4c0c-b2b2-347f5c44983c",
            "child_name", "Hassan Ali Juma"));

        CHTChangesResponse response = createResponseWithDocs(
            List.of(reportDoc), "245-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processReportIngestion(any()))
            .thenReturn(successResult());

        syncService.incrementalSync();

        ArgumentCaptor<CHTReport> captor = ArgumentCaptor.forClass(CHTReport.class);
        verify(ingestionFlowService).processReportIngestion(captor.capture());
        verify(ingestionFlowService, never()).processContactIngestion(any());

        CHTReport captured = captor.getValue();
        assertEquals("42762c2b-b082-445f-9cc7-17093e5520a8", captured.getId());
        assertEquals("infant_child", captured.getForm());
    }

    // ==================== Mixed Document Batch ====================

    @Test
    void routesMixedBatchCorrectly() throws Exception {
        // Simulate a real _changes feed with person + clinic + data_record
        Map<String, Object> personDoc = Map.of(
            "_id", "person-1", "type", "person", "name", "Test Person");
        Map<String, Object> clinicDoc = Map.of(
            "_id", "clinic-1", "type", "clinic", "name", "Test Clinic");
        Map<String, Object> reportDoc = new LinkedHashMap<>(Map.of(
            "_id", "report-1", "type", "data_record", "form", "pregnancy_visit",
            "reported_date", 1700000000000L));

        CHTChangesResponse response = createResponseWithDocs(
            List.of(personDoc, clinicDoc, reportDoc), "300-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any())).thenReturn(successResult());
        when(ingestionFlowService.processPlaceIngestion(any())).thenReturn(successResult());
        when(ingestionFlowService.processReportIngestion(any())).thenReturn(successResult());

        syncService.incrementalSync();

        verify(ingestionFlowService, times(1)).processContactIngestion(any());
        verify(ingestionFlowService, times(1)).processPlaceIngestion(any());
        verify(ingestionFlowService, times(1)).processReportIngestion(any());
    }

    @Test
    void routesHealthCenterToPlaceIngestion() throws Exception {
        Map<String, Object> hcDoc = Map.of(
            "_id", "hc-1", "type", "health_center", "name", "Temeke HC");

        CHTChangesResponse response = createResponseWithDocs(
            List.of(hcDoc), "50-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processPlaceIngestion(any())).thenReturn(successResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processPlaceIngestion(any());
    }

    @Test
    void routesDistrictHospitalToPlaceIngestion() throws Exception {
        Map<String, Object> dhDoc = Map.of(
            "_id", "dh-1", "type", "district_hospital", "name", "Muhimbili");

        CHTChangesResponse response = createResponseWithDocs(
            List.of(dhDoc), "50-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processPlaceIngestion(any())).thenReturn(successResult());

        syncService.incrementalSync();

        verify(ingestionFlowService).processPlaceIngestion(any());
    }

    // ==================== Seq Persistence After Real Batch ====================

    @Test
    void persistsLastSeqAfterProcessingRealDocuments() throws Exception {
        Map<String, Object> personDoc = Map.of(
            "_id", "p-1", "type", "person", "name", "Test");

        CHTChangesResponse response = createResponseWithDocs(
            List.of(personDoc),
            "245-g1AAAABXeJzLYWBg4MhgTmHgzcvPy09JdcjLz0lVSMrITElVSC",
            0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);
        when(ingestionFlowService.processContactIngestion(any())).thenReturn(successResult());

        syncService.incrementalSync();

        String savedSeq = syncService.loadSinceSeq();
        assertEquals("245-g1AAAABXeJzLYWBg4MhgTmHgzcvPy09JdcjLz0lVSMrITElVSC", savedSeq);
    }

    // ==================== Skipped Document Types ====================

    @Test
    void skipsDesignDocuments() throws Exception {
        Map<String, Object> designDoc = Map.of(
            "_id", "_design/medic", "type", "design");

        CHTChangesResponse response = createResponseWithDocs(
            List.of(designDoc), "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void skipsTaskDocuments() throws Exception {
        Map<String, Object> taskDoc = Map.of(
            "_id", "task-1", "type", "task", "name", "Follow up");

        CHTChangesResponse response = createResponseWithDocs(
            List.of(taskDoc), "10-seq", 0);

        when(chtApiClient.getChanges(any(), eq(100))).thenReturn(response);

        syncService.incrementalSync();

        verifyNoInteractions(ingestionFlowService);
    }

    // ==================== Helpers ====================

    private CHTChangesResponse createResponseWithDocs(
            List<Map<String, Object>> docs, String lastSeq, Integer pending) {
        CHTChangesResponse response = new CHTChangesResponse();
        List<CHTChangesResponse.CHTChangeResult> results = new ArrayList<>();

        int seq = 1;
        for (Map<String, Object> doc : docs) {
            CHTChangesResponse.CHTChangeResult change = new CHTChangesResponse.CHTChangeResult();
            change.setId((String) doc.get("_id"));
            change.setDoc(doc);
            results.add(change);
            seq++;
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
}
