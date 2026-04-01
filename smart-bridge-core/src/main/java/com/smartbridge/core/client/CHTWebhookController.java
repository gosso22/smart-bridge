package com.smartbridge.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for handling CHT webhook push notifications.
 * Receives real-time document pushes from CHT outbound and routes them
 * to the ingestion flow service.
 */
@RestController
@RequestMapping("/cht/webhook")
public class CHTWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(CHTWebhookController.class);

    private final CHTIngestionFlowService ingestionFlowService;
    private final ObjectMapper objectMapper;

    public CHTWebhookController(CHTIngestionFlowService ingestionFlowService) {
        this.ingestionFlowService = ingestionFlowService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Receive a CHT person contact push.
     */
    @PostMapping(value = "/contact", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleContactPush(@RequestBody String contactJson) {
        logger.info("Received CHT webhook contact push");

        try {
            CHTContact contact = objectMapper.readValue(contactJson, CHTContact.class);
            IngestionFlowService.IngestionFlowResult result = ingestionFlowService.processContactIngestion(contact);

            if (result.isSuccess()) {
                return ResponseEntity.ok("Contact processed: " + result.getFhirResourceId());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Contact processing failed: " + result.getErrorMessage());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.warn("Invalid contact JSON in webhook push", e);
            return ResponseEntity.badRequest().body("Invalid JSON payload: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing CHT webhook contact push", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing contact: " + e.getMessage());
        }
    }

    /**
     * Receive a CHT report (data_record) push.
     */
    @PostMapping(value = "/report", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleReportPush(@RequestBody String reportJson) {
        logger.info("Received CHT webhook report push");

        try {
            CHTReport report = objectMapper.readValue(reportJson, CHTReport.class);
            IngestionFlowService.IngestionFlowResult result = ingestionFlowService.processReportIngestion(report);

            if (result.isSuccess()) {
                return ResponseEntity.ok("Report processed: " + result.getFhirResourceId());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Report processing failed: " + result.getErrorMessage());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.warn("Invalid report JSON in webhook push", e);
            return ResponseEntity.badRequest().body("Invalid JSON payload: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing CHT webhook report push", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing report: " + e.getMessage());
        }
    }

    /**
     * Generic push endpoint — inspects the doc type field and routes accordingly.
     * CHT outbound config can push any doc type here.
     */
    @SuppressWarnings("unchecked")
    @PostMapping(value = "/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleGenericPush(@RequestBody String docJson) {
        logger.info("Received CHT webhook generic push");

        try {
            Map<String, Object> doc = objectMapper.readValue(docJson, Map.class);
            String type = (String) doc.get("type");

            if ("contact".equals(type)) {
                CHTContact contact = objectMapper.convertValue(doc, CHTContact.class);
                IngestionFlowService.IngestionFlowResult result = ingestionFlowService.processContactIngestion(contact);
                return result.isSuccess()
                    ? ResponseEntity.ok("Contact processed")
                    : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Contact processing failed: " + result.getErrorMessage());

            } else if ("data_record".equals(type)) {
                CHTReport report = objectMapper.convertValue(doc, CHTReport.class);
                IngestionFlowService.IngestionFlowResult result = ingestionFlowService.processReportIngestion(report);
                return result.isSuccess()
                    ? ResponseEntity.ok("Report processed")
                    : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Report processing failed: " + result.getErrorMessage());

            } else {
                logger.debug("Ignoring webhook push with unhandled type: {}", type);
                return ResponseEntity.ok("Ignored: unhandled document type");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.warn("Invalid JSON in webhook push", e);
            return ResponseEntity.badRequest().body("Invalid JSON payload: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing CHT webhook push", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing push: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint for the CHT webhook.
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("CHT webhook endpoint is healthy");
    }
}
