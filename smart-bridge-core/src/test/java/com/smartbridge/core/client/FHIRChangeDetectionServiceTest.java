package com.smartbridge.core.client;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FHIRChangeDetectionServiceTest {

    @Mock
    private FHIRClientService fhirClientService;

    @InjectMocks
    private FHIRChangeDetectionService changeDetectionService;

    // ========== Polling Tests ==========

    @Test
    void testEnablePolling() {
        changeDetectionService.enablePolling(10000);
        
        assertTrue(changeDetectionService.isPollingEnabled());
        assertEquals(10000, changeDetectionService.getPollingIntervalMs());
    }

    @Test
    void testDisablePolling() {
        changeDetectionService.enablePolling(10000);
        changeDetectionService.disablePolling();
        
        assertFalse(changeDetectionService.isPollingEnabled());
    }

    @Test
    void testPollForPatientChanges() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        changeDetectionService.enablePolling(30000);
        
        Patient patient1 = createTestPatient("patient1");
        Patient patient2 = createTestPatient("patient2");
        List<Patient> patients = Arrays.asList(patient1, patient2);
        
        when(fhirClientService.searchPatientsUpdatedAfter(any(Date.class)))
            .thenReturn(patients);
        
        AtomicInteger notificationCount = new AtomicInteger(0);
        changeDetectionService.registerChangeListener("Patient", resource -> {
            notificationCount.incrementAndGet();
        });
        
        // Execute
        changeDetectionService.pollForPatientChanges();
        
        // Verify
        verify(fhirClientService).searchPatientsUpdatedAfter(any(Date.class));
        assertEquals(2, notificationCount.get());
        assertNotNull(changeDetectionService.getLastUpdateTimestamp("Patient"));
    }

    @Test
    void testPollForPatientChangesWhenPollingDisabled() {
        // Setup
        changeDetectionService.disablePolling();
        
        // Execute
        changeDetectionService.pollForPatientChanges();
        
        // Verify - should not call FHIR client
        verify(fhirClientService, never()).searchPatientsUpdatedAfter(any(Date.class));
    }

    @Test
    void testPollForObservationChanges() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        String patientId = "patient123";
        Observation obs1 = createTestObservation("obs1", patientId);
        Observation obs2 = createTestObservation("obs2", patientId);
        List<Observation> observations = Arrays.asList(obs1, obs2);
        
        when(fhirClientService.searchObservationsByPatient(eq(patientId), any(Date.class)))
            .thenReturn(observations);
        
        AtomicInteger notificationCount = new AtomicInteger(0);
        changeDetectionService.registerChangeListener("Observation", resource -> {
            notificationCount.incrementAndGet();
        });
        
        // Execute
        List<Observation> result = changeDetectionService.pollForObservationChanges(patientId);
        
        // Verify
        assertEquals(2, result.size());
        assertEquals(2, notificationCount.get());
        verify(fhirClientService).searchObservationsByPatient(eq(patientId), any(Date.class));
    }

    @Test
    void testResetPollingTimestamp() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        changeDetectionService.enablePolling(30000);
        when(fhirClientService.searchPatientsUpdatedAfter(any(Date.class)))
            .thenReturn(Collections.singletonList(createTestPatient("patient1")));
        
        changeDetectionService.pollForPatientChanges();
        assertNotNull(changeDetectionService.getLastUpdateTimestamp("Patient"));
        
        // Execute
        changeDetectionService.resetPollingTimestamp("Patient");
        
        // Verify
        assertNull(changeDetectionService.getLastUpdateTimestamp("Patient"));
    }

    // ========== Subscription Tests ==========

    @Test
    void testCreateSubscription() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        String resourceType = "Patient";
        String criteria = "Patient?";
        String webhookUrl = "http://localhost:8080/fhir/webhook/notification";
        
        Subscription createdSubscription = new Subscription();
        createdSubscription.setId("sub123");
        createdSubscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
        
        MethodOutcome outcome = new MethodOutcome();
        outcome.setResource(createdSubscription);
        
        when(fhirClientService.createSubscription(any(Subscription.class)))
            .thenReturn(outcome);
        
        // Execute
        Subscription result = changeDetectionService.createSubscription(resourceType, criteria, webhookUrl);
        
        // Verify
        assertNotNull(result);
        assertEquals("sub123", result.getIdElement().getIdPart());
        verify(fhirClientService).createSubscription(any(Subscription.class));
        
        Map<String, Subscription> activeSubscriptions = changeDetectionService.getActiveSubscriptions();
        assertTrue(activeSubscriptions.containsKey("sub123"));
    }

    @Test
    void testActivateSubscription() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        String subscriptionId = "sub123";
        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
        
        when(fhirClientService.getSubscription(subscriptionId))
            .thenReturn(subscription);
        when(fhirClientService.updateSubscription(any(Subscription.class)))
            .thenReturn(new MethodOutcome());
        
        // Execute
        changeDetectionService.activateSubscription(subscriptionId);
        
        // Verify
        verify(fhirClientService).getSubscription(subscriptionId);
        verify(fhirClientService).updateSubscription(argThat(sub -> 
            sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE
        ));
    }

    @Test
    void testDeactivateSubscription() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        String subscriptionId = "sub123";
        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        
        when(fhirClientService.getSubscription(subscriptionId))
            .thenReturn(subscription);
        when(fhirClientService.updateSubscription(any(Subscription.class)))
            .thenReturn(new MethodOutcome());
        
        // Execute
        changeDetectionService.deactivateSubscription(subscriptionId);
        
        // Verify
        verify(fhirClientService).getSubscription(subscriptionId);
        verify(fhirClientService).updateSubscription(argThat(sub -> 
            sub.getStatus() == Subscription.SubscriptionStatus.OFF
        ));
    }

    @Test
    void testDeleteSubscription() {
        // Setup
        when(fhirClientService.isConfigured()).thenReturn(true);
        String subscriptionId = "sub123";
        
        doNothing().when(fhirClientService).deleteSubscription(subscriptionId);
        
        // Execute
        changeDetectionService.deleteSubscription(subscriptionId);
        
        // Verify
        verify(fhirClientService).deleteSubscription(subscriptionId);
    }

    // ========== Webhook Tests ==========

    @Test
    void testHandleWebhookNotification() {
        // Setup
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.HISTORY);
        
        Patient patient = createTestPatient("patient1");
        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setResource(patient);
        bundle.addEntry(entry);
        
        AtomicInteger notificationCount = new AtomicInteger(0);
        changeDetectionService.registerChangeListener("Patient", resource -> {
            notificationCount.incrementAndGet();
        });
        
        // Execute
        changeDetectionService.handleWebhookNotification(bundle);
        
        // Verify
        assertEquals(1, notificationCount.get());
    }

    @Test
    void testHandleResourceNotification() {
        // Setup
        Patient patient = createTestPatient("patient1");
        
        AtomicInteger notificationCount = new AtomicInteger(0);
        changeDetectionService.registerChangeListener("Patient", resource -> {
            notificationCount.incrementAndGet();
            assertEquals("patient1", resource.getIdElement().getIdPart());
        });
        
        // Execute
        changeDetectionService.handleResourceNotification(patient);
        
        // Verify
        assertEquals(1, notificationCount.get());
    }

    @Test
    void testHandleWebhookNotificationWithNullBundle() {
        // Execute - should not throw exception
        changeDetectionService.handleWebhookNotification(null);
        
        // Verify - no exception thrown
    }

    @Test
    void testHandleResourceNotificationWithNullResource() {
        // Execute - should not throw exception
        changeDetectionService.handleResourceNotification(null);
        
        // Verify - no exception thrown
    }

    // ========== Change Listener Tests ==========

    @Test
    void testRegisterChangeListener() {
        // Setup
        AtomicInteger callCount = new AtomicInteger(0);
        
        // Execute
        changeDetectionService.registerChangeListener("Patient", resource -> {
            callCount.incrementAndGet();
        });
        
        // Trigger notification
        Patient patient = createTestPatient("patient1");
        changeDetectionService.handleResourceNotification(patient);
        
        // Verify
        assertEquals(1, callCount.get());
    }

    @Test
    void testMultipleChangeListeners() {
        // Setup
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        
        changeDetectionService.registerChangeListener("Patient", resource -> {
            listener1Count.incrementAndGet();
        });
        changeDetectionService.registerChangeListener("Patient", resource -> {
            listener2Count.incrementAndGet();
        });
        
        // Execute
        Patient patient = createTestPatient("patient1");
        changeDetectionService.handleResourceNotification(patient);
        
        // Verify
        assertEquals(1, listener1Count.get());
        assertEquals(1, listener2Count.get());
    }

    @Test
    void testUnregisterChangeListeners() {
        // Setup
        AtomicInteger callCount = new AtomicInteger(0);
        changeDetectionService.registerChangeListener("Patient", resource -> {
            callCount.incrementAndGet();
        });
        
        // Execute
        changeDetectionService.unregisterChangeListeners("Patient");
        
        // Trigger notification
        Patient patient = createTestPatient("patient1");
        changeDetectionService.handleResourceNotification(patient);
        
        // Verify - listener should not be called
        assertEquals(0, callCount.get());
    }

    // ========== Helper Methods ==========

    private Patient createTestPatient(String id) {
        Patient patient = new Patient();
        patient.setId(id);
        
        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        patient.setMeta(meta);
        
        HumanName name = new HumanName();
        name.setFamily("Doe");
        name.addGiven("John");
        patient.addName(name);
        
        return patient;
    }

    private Observation createTestObservation(String id, String patientId) {
        Observation observation = new Observation();
        observation.setId(id);
        
        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        observation.setMeta(meta);
        
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setSubject(new Reference("Patient/" + patientId));
        
        return observation;
    }
}
