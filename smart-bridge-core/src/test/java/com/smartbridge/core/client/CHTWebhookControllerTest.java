package com.smartbridge.core.client;

import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CHTWebhookControllerTest {

    @Mock private CHTIngestionFlowService ingestionFlowService;

    private CHTWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new CHTWebhookController(ingestionFlowService);
    }

    // ==================== Contact Push ====================

    @Test
    void handleContactPush_Success() {
        String json = "{\"_id\":\"c1\",\"name\":\"Amina Juma\",\"type\":\"contact\",\"contact_type\":\"person\"}";

        IngestionFlowService.IngestionFlowResult result = successResult();
        when(ingestionFlowService.processContactIngestion(any())).thenReturn(result);

        ResponseEntity<String> response = controller.handleContactPush(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ingestionFlowService).processContactIngestion(any());
    }

    @Test
    void handleContactPush_InvalidJson_Returns400() {
        String badJson = "{ not valid json }}}";

        ResponseEntity<String> response = controller.handleContactPush(badJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid JSON"));
    }

    @Test
    void handleContactPush_ProcessingFails_Returns500() {
        String json = "{\"_id\":\"c2\",\"name\":\"Test\",\"type\":\"contact\"}";

        IngestionFlowService.IngestionFlowResult result = failResult("Validation failed");
        when(ingestionFlowService.processContactIngestion(any())).thenReturn(result);

        ResponseEntity<String> response = controller.handleContactPush(json);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("failed"));
    }

    // ==================== Report Push ====================

    @Test
    void handleReportPush_Success() {
        String json = "{\"_id\":\"r1\",\"type\":\"data_record\",\"form\":\"pregnancy_visit\",\"reported_date\":1700000000000}";

        IngestionFlowService.IngestionFlowResult result = successResult();
        when(ingestionFlowService.processReportIngestion(any())).thenReturn(result);

        ResponseEntity<String> response = controller.handleReportPush(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ingestionFlowService).processReportIngestion(any());
    }

    @Test
    void handleReportPush_InvalidJson_Returns400() {
        ResponseEntity<String> response = controller.handleReportPush("{bad}}}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ==================== Generic Push ====================

    @Test
    void handleGenericPush_RoutesContactCorrectly() {
        String json = "{\"_id\":\"g1\",\"type\":\"contact\",\"contact_type\":\"person\",\"name\":\"Test\"}";

        when(ingestionFlowService.processContactIngestion(any())).thenReturn(successResult());

        ResponseEntity<String> response = controller.handleGenericPush(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ingestionFlowService).processContactIngestion(any());
        verify(ingestionFlowService, never()).processReportIngestion(any());
    }

    @Test
    void handleGenericPush_RoutesDataRecordCorrectly() {
        String json = "{\"_id\":\"g2\",\"type\":\"data_record\",\"form\":\"visit\",\"reported_date\":1700000000000}";

        when(ingestionFlowService.processReportIngestion(any())).thenReturn(successResult());

        ResponseEntity<String> response = controller.handleGenericPush(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(ingestionFlowService).processReportIngestion(any());
        verify(ingestionFlowService, never()).processContactIngestion(any());
    }

    @Test
    void handleGenericPush_IgnoresUnknownType() {
        String json = "{\"_id\":\"g3\",\"type\":\"unknown_type\"}";

        ResponseEntity<String> response = controller.handleGenericPush(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Ignored"));
        verifyNoInteractions(ingestionFlowService);
    }

    @Test
    void handleGenericPush_InvalidJson_Returns400() {
        ResponseEntity<String> response = controller.handleGenericPush("{bad}}}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ==================== Health Check ====================

    @Test
    void healthCheck_Returns200() {
        ResponseEntity<String> response = controller.healthCheck();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("healthy"));
    }

    // ==================== Helpers ====================

    private IngestionFlowService.IngestionFlowResult successResult() {
        IngestionFlowService.IngestionFlowResult r = new IngestionFlowService.IngestionFlowResult("tx-1");
        r.setSuccess(true);
        r.setFhirResourceId("fhir-123");
        return r;
    }

    private IngestionFlowService.IngestionFlowResult failResult(String error) {
        IngestionFlowService.IngestionFlowResult r = new IngestionFlowService.IngestionFlowResult("tx-2");
        r.setSuccess(false);
        r.setErrorMessage(error);
        return r;
    }
}
