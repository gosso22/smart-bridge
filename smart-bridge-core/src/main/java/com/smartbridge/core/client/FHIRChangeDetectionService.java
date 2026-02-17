package com.smartbridge.core.client;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * FHIR change detection service that monitors FHIR resources for changes.
 * Supports three mechanisms:
 * 1. Polling-based detection using _lastUpdated parameter
 * 2. FHIR R4 subscription handling for real-time notifications
 * 3. Webhook integration for immediate change notification
 */
@Service
public class FHIRChangeDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(FHIRChangeDetectionService.class);
    
    @Autowired
    private FHIRClientService fhirClientService;
    
    // Track last update timestamps for polling
    private final Map<String, Date> lastUpdateTimestamps = new ConcurrentHashMap<>();
    
    // Track active subscriptions
    private final Map<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    
    // Change listeners for different resource types
    private final Map<String, List<Consumer<Resource>>> changeListeners = new ConcurrentHashMap<>();
    
    // Polling configuration
    private boolean pollingEnabled = false;
    private long pollingIntervalMs = 30000; // 30 seconds default
    
    /**
     * Enable polling-based change detection
     */
    public void enablePolling(long intervalMs) {
        this.pollingEnabled = true;
        this.pollingIntervalMs = intervalMs;
        logger.info("Polling enabled with interval: {} ms", intervalMs);
    }
    
    /**
     * Disable polling-based change detection
     */
    public void disablePolling() {
        this.pollingEnabled = false;
        logger.info("Polling disabled");
    }
    
    /**
     * Register a change listener for a specific resource type
     */
    public void registerChangeListener(String resourceType, Consumer<Resource> listener) {
        changeListeners.computeIfAbsent(resourceType, k -> new ArrayList<>()).add(listener);
        logger.info("Registered change listener for resource type: {}", resourceType);
    }
    
    /**
     * Unregister all change listeners for a resource type
     */
    public void unregisterChangeListeners(String resourceType) {
        changeListeners.remove(resourceType);
        logger.info("Unregistered all change listeners for resource type: {}", resourceType);
    }
    
    // ========== Polling-Based Change Detection ==========
    
    /**
     * Poll for Patient changes using _lastUpdated parameter
     */
    @Scheduled(fixedDelayString = "${fhir.polling.interval:30000}")
    public void pollForPatientChanges() {
        if (!pollingEnabled || !fhirClientService.isConfigured()) {
            return;
        }
        
        try {
            Date lastUpdate = lastUpdateTimestamps.getOrDefault("Patient", new Date(0));
            List<Patient> updatedPatients = fhirClientService.searchPatientsUpdatedAfter(lastUpdate);
            
            if (!updatedPatients.isEmpty()) {
                logger.info("Detected {} Patient changes since {}", updatedPatients.size(), lastUpdate);
                
                // Notify listeners
                notifyListeners("Patient", updatedPatients);
                
                // Update timestamp
                Date latestUpdate = updatedPatients.stream()
                    .map(p -> p.getMeta().getLastUpdated())
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(new Date());
                
                lastUpdateTimestamps.put("Patient", latestUpdate);
            }
        } catch (Exception e) {
            logger.error("Error polling for Patient changes", e);
        }
    }
    
    /**
     * Poll for Observation changes for a specific patient
     */
    public List<Observation> pollForObservationChanges(String patientId) {
        if (!fhirClientService.isConfigured()) {
            throw new IllegalStateException("FHIR client not configured");
        }
        
        try {
            String key = "Observation_" + patientId;
            Date lastUpdate = lastUpdateTimestamps.getOrDefault(key, new Date(0));
            List<Observation> updatedObservations = fhirClientService.searchObservationsByPatient(patientId, lastUpdate);
            
            if (!updatedObservations.isEmpty()) {
                logger.info("Detected {} Observation changes for patient {} since {}", 
                    updatedObservations.size(), patientId, lastUpdate);
                
                // Notify listeners
                notifyListeners("Observation", updatedObservations);
                
                // Update timestamp
                Date latestUpdate = updatedObservations.stream()
                    .map(o -> o.getMeta().getLastUpdated())
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(new Date());
                
                lastUpdateTimestamps.put(key, latestUpdate);
            }
            
            return updatedObservations;
        } catch (Exception e) {
            logger.error("Error polling for Observation changes for patient: {}", patientId, e);
            throw new FHIRClientException("Failed to poll for Observation changes", e);
        }
    }
    
    /**
     * Reset polling timestamp for a resource type
     */
    public void resetPollingTimestamp(String resourceType) {
        lastUpdateTimestamps.remove(resourceType);
        logger.info("Reset polling timestamp for resource type: {}", resourceType);
    }
    
    // ========== FHIR R4 Subscription Handling ==========
    
    /**
     * Create a FHIR R4 subscription for resource changes
     */
    public Subscription createSubscription(String resourceType, String criteria, String webhookUrl) {
        if (!fhirClientService.isConfigured()) {
            throw new IllegalStateException("FHIR client not configured");
        }
        
        try {
            Subscription subscription = new Subscription();
            subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
            subscription.setReason("Smart Bridge change detection for " + resourceType);
            
            // Set criteria for subscription
            subscription.setCriteria(criteria);
            
            // Configure webhook channel
            Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
            channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
            channel.setEndpoint(webhookUrl);
            channel.setPayload("application/fhir+json");
            
            // Add headers for authentication if needed
            channel.addHeader("Content-Type: application/fhir+json");
            
            subscription.setChannel(channel);
            
            // Create subscription on FHIR server
            MethodOutcome outcome = fhirClientService.createSubscription(subscription);
            Subscription createdSubscription = (Subscription) outcome.getResource();
            
            if (createdSubscription == null && outcome.getId() != null) {
                // Fetch the created subscription
                createdSubscription = fhirClientService.getSubscription(outcome.getId().getIdPart());
            }
            
            // Track active subscription
            if (createdSubscription != null) {
                activeSubscriptions.put(createdSubscription.getIdElement().getIdPart(), createdSubscription);
                logger.info("Created subscription {} for {} with criteria: {}", 
                    createdSubscription.getIdElement().getIdPart(), resourceType, criteria);
            }
            
            return createdSubscription;
        } catch (Exception e) {
            logger.error("Error creating subscription for {}", resourceType, e);
            throw new FHIRClientException("Failed to create subscription", e);
        }
    }
    
    /**
     * Activate a subscription
     */
    public void activateSubscription(String subscriptionId) {
        if (!fhirClientService.isConfigured()) {
            throw new IllegalStateException("FHIR client not configured");
        }
        
        try {
            Subscription subscription = fhirClientService.getSubscription(subscriptionId);
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            
            fhirClientService.updateSubscription(subscription);
            activeSubscriptions.put(subscriptionId, subscription);
            
            logger.info("Activated subscription: {}", subscriptionId);
        } catch (Exception e) {
            logger.error("Error activating subscription: {}", subscriptionId, e);
            throw new FHIRClientException("Failed to activate subscription", e);
        }
    }
    
    /**
     * Deactivate a subscription
     */
    public void deactivateSubscription(String subscriptionId) {
        if (!fhirClientService.isConfigured()) {
            throw new IllegalStateException("FHIR client not configured");
        }
        
        try {
            Subscription subscription = fhirClientService.getSubscription(subscriptionId);
            subscription.setStatus(Subscription.SubscriptionStatus.OFF);
            
            fhirClientService.updateSubscription(subscription);
            activeSubscriptions.remove(subscriptionId);
            
            logger.info("Deactivated subscription: {}", subscriptionId);
        } catch (Exception e) {
            logger.error("Error deactivating subscription: {}", subscriptionId, e);
            throw new FHIRClientException("Failed to deactivate subscription", e);
        }
    }
    
    /**
     * Delete a subscription
     */
    public void deleteSubscription(String subscriptionId) {
        if (!fhirClientService.isConfigured()) {
            throw new IllegalStateException("FHIR client not configured");
        }
        
        try {
            fhirClientService.deleteSubscription(subscriptionId);
            activeSubscriptions.remove(subscriptionId);
            
            logger.info("Deleted subscription: {}", subscriptionId);
        } catch (Exception e) {
            logger.error("Error deleting subscription: {}", subscriptionId, e);
            throw new FHIRClientException("Failed to delete subscription", e);
        }
    }
    
    /**
     * Get all active subscriptions
     */
    public Map<String, Subscription> getActiveSubscriptions() {
        return new HashMap<>(activeSubscriptions);
    }
    
    // ========== Webhook Integration ==========
    
    /**
     * Handle incoming webhook notification from FHIR server
     * This method should be called by a REST controller when webhook is received
     */
    public void handleWebhookNotification(Bundle notificationBundle) {
        if (notificationBundle == null) {
            logger.warn("Received null notification bundle");
            return;
        }
        
        logger.info("Processing webhook notification with {} entries", 
            notificationBundle.getEntry().size());
        
        try {
            for (Bundle.BundleEntryComponent entry : notificationBundle.getEntry()) {
                Resource resource = entry.getResource();
                
                if (resource != null) {
                    String resourceType = resource.getResourceType().name();
                    logger.debug("Webhook notification for {} resource: {}", 
                        resourceType, resource.getIdElement().getIdPart());
                    
                    // Notify listeners
                    notifyListeners(resourceType, Collections.singletonList(resource));
                }
            }
        } catch (Exception e) {
            logger.error("Error processing webhook notification", e);
        }
    }
    
    /**
     * Handle single resource webhook notification
     */
    public void handleResourceNotification(Resource resource) {
        if (resource == null) {
            logger.warn("Received null resource notification");
            return;
        }
        
        String resourceType = resource.getResourceType().name();
        logger.info("Processing resource notification for {}: {}", 
            resourceType, resource.getIdElement().getIdPart());
        
        try {
            notifyListeners(resourceType, Collections.singletonList(resource));
        } catch (Exception e) {
            logger.error("Error processing resource notification", e);
        }
    }
    
    // ========== Utility Methods ==========
    
    /**
     * Notify all registered listeners for a resource type
     */
    private void notifyListeners(String resourceType, List<? extends Resource> resources) {
        List<Consumer<Resource>> listeners = changeListeners.get(resourceType);
        
        if (listeners != null && !listeners.isEmpty()) {
            for (Resource resource : resources) {
                for (Consumer<Resource> listener : listeners) {
                    try {
                        listener.accept(resource);
                    } catch (Exception e) {
                        logger.error("Error notifying listener for {} resource", resourceType, e);
                    }
                }
            }
        }
    }
    
    /**
     * Get last update timestamp for a resource type
     */
    public Date getLastUpdateTimestamp(String resourceType) {
        return lastUpdateTimestamps.get(resourceType);
    }
    
    /**
     * Check if polling is enabled
     */
    public boolean isPollingEnabled() {
        return pollingEnabled;
    }
    
    /**
     * Get polling interval in milliseconds
     */
    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }
}
