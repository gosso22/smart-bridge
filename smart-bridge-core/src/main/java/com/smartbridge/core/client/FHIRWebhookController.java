package com.smartbridge.core.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for handling FHIR webhook notifications.
 * Receives notifications from FHIR server subscriptions and forwards them
 * to the change detection service for processing.
 */
@RestController
@RequestMapping("/fhir/webhook")
public class FHIRWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(FHIRWebhookController.class);
    
    @Autowired
    private FHIRChangeDetectionService changeDetectionService;
    
    private final FhirContext fhirContext;
    private final IParser jsonParser;
    
    public FHIRWebhookController() {
        this.fhirContext = FhirContext.forR4();
        this.jsonParser = fhirContext.newJsonParser();
    }
    
    /**
     * Handle webhook notification with Bundle payload
     */
    @PostMapping(value = "/notification", 
                 consumes = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"})
    public ResponseEntity<String> handleBundleNotification(@RequestBody String bundleJson) {
        logger.info("Received webhook notification");
        
        try {
            // Parse the incoming bundle
            Bundle bundle = jsonParser.parseResource(Bundle.class, bundleJson);
            
            // Process the notification
            changeDetectionService.handleWebhookNotification(bundle);
            
            logger.info("Successfully processed webhook notification with {} entries", 
                bundle.getEntry().size());
            
            return ResponseEntity.ok("Notification processed successfully");
            
        } catch (Exception e) {
            logger.error("Error processing webhook notification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing notification: " + e.getMessage());
        }
    }
    
    /**
     * Handle webhook notification with single resource payload
     */
    @PostMapping(value = "/resource/{resourceType}", 
                 consumes = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"})
    public ResponseEntity<String> handleResourceNotification(
            @PathVariable String resourceType,
            @RequestBody String resourceJson) {
        
        logger.info("Received webhook notification for {} resource", resourceType);
        
        try {
            // Parse the incoming resource
            Resource resource = (Resource) jsonParser.parseResource(resourceJson);
            
            // Verify resource type matches
            if (!resource.getResourceType().name().equals(resourceType)) {
                logger.warn("Resource type mismatch: expected {}, got {}", 
                    resourceType, resource.getResourceType().name());
                return ResponseEntity.badRequest()
                    .body("Resource type mismatch");
            }
            
            // Process the notification
            changeDetectionService.handleResourceNotification(resource);
            
            logger.info("Successfully processed {} resource notification: {}", 
                resourceType, resource.getIdElement().getIdPart());
            
            return ResponseEntity.ok("Resource notification processed successfully");
            
        } catch (Exception e) {
            logger.error("Error processing resource notification for {}", resourceType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing resource notification: " + e.getMessage());
        }
    }
    
    /**
     * Health check endpoint for webhook
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Webhook endpoint is healthy");
    }
}
