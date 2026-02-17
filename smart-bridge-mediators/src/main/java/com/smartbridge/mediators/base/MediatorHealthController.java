package com.smartbridge.mediators.base;

import com.smartbridge.core.interfaces.MediatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for mediator health check endpoints.
 * Provides health status for all registered mediators.
 */
@RestController
@RequestMapping("/mediator")
public class MediatorHealthController {

    @Autowired(required = false)
    private List<MediatorService> mediators;

    /**
     * Health check endpoint for all mediators.
     * Returns health status of all registered mediators.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> mediatorsHealth = new HashMap<>();
        
        boolean allHealthy = true;
        
        if (mediators != null && !mediators.isEmpty()) {
            for (MediatorService mediator : mediators) {
                MediatorService.HealthCheckResult result = mediator.performHealthCheck();
                
                Map<String, Object> mediatorStatus = new HashMap<>();
                mediatorStatus.put("healthy", result.isHealthy());
                mediatorStatus.put("message", result.getMessage());
                mediatorStatus.put("responseTimeMs", result.getResponseTimeMs());
                
                mediatorsHealth.put(mediator.getConfiguration().getName(), mediatorStatus);
                
                if (!result.isHealthy()) {
                    allHealthy = false;
                }
            }
        } else {
            response.put("message", "No mediators registered");
        }
        
        response.put("status", allHealthy ? "UP" : "DOWN");
        response.put("mediators", mediatorsHealth);
        response.put("timestamp", System.currentTimeMillis());
        
        HttpStatus status = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Get configuration for all mediators.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> mediatorsConfig = new HashMap<>();
        
        if (mediators != null && !mediators.isEmpty()) {
            for (MediatorService mediator : mediators) {
                MediatorService.MediatorConfig config = mediator.getConfiguration();
                
                Map<String, Object> configData = new HashMap<>();
                configData.put("name", config.getName());
                configData.put("version", config.getVersion());
                configData.put("description", config.getDescription());
                configData.put("config", config.getConfig());
                
                mediatorsConfig.put(config.getName(), configData);
            }
        }
        
        response.put("mediators", mediatorsConfig);
        response.put("count", mediators != null ? mediators.size() : 0);
        
        return ResponseEntity.ok(response);
    }
}
