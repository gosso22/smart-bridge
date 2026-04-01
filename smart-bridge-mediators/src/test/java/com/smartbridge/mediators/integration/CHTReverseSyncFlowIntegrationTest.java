package com.smartbridge.mediators.integration;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.transformation.FHIRToCHTPersonTransformer;
import com.smartbridge.mediators.cht.CHTApiClient;
import com.smartbridge.mediators.cht.CHTReverseSyncFlowService;
import com.smartbridge.mediators.cht.CHTReverseSyncFlowService.CHTReverseSyncResult;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CHT reverse sync flow (FHIR → CHT).
 * Verifies circular update prevention, transformation, and CHT storage.
 */
@ExtendWith(MockitoExtension.class)
class CHTReverseSyncFlowIntegrationTest {

    @Mock private FHIRChangeDetectionService changeDetectionService;
    @Mock private FHIRToCHTPersonTransformer transformer;
    @Mock private CHTApiClient chtApiClient;

    private CHTReverseSyncFlowService reverseSyncService;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLogger();
        reverseSyncService = new CHTReverseSyncFlowService(
            changeDetectionService, transformer, chtApiClient,
            auditLogger, Executors.newSingleThreadExecutor());
    }

    // ==================== Circular Update Prevention ====================

    @Test
    void skipsPatientWithCHTSourceTag() {
        Patient patient = createCHTOriginatedPatient();

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertTrue(result.isConflictDetected());
        assertEquals("CIRCULAR_UPDATE_SKIP", result.getConflictResolution());
        verifyNoInteractions(transformer);
        verifyNoInteractions(chtApiClient);
    }

    @Test
    void processesPatientWithoutCHTTag() throws Exception {
        Patient patient = createFHIROnlyPatient();
        CHTContact contact = createCHTContact();

        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-123"))
            .thenThrow(new MediatorException("Not found", "CHT", "getContact", 404));

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertFalse(result.isSkipped());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isChtStorageCompleted());
        assertEquals("CREATE", result.getChtOperationType());
    }

    // ==================== Create vs Update ====================

    @Test
    void createsNewContactWhenNotFoundInCHT() throws Exception {
        Patient patient = createFHIROnlyPatient();
        CHTContact contact = createCHTContact();

        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-123"))
            .thenThrow(new MediatorException("Not found", "CHT", "getContact", 404));

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertEquals("CREATE", result.getChtOperationType());
        verify(chtApiClient).createPerson(contact);
        verify(chtApiClient, never()).updatePerson(any(), any());
    }

    @Test
    void updatesExistingContactWhenFoundInCHT() throws Exception {
        Patient patient = createFHIROnlyPatient();
        CHTContact contact = createCHTContact();
        CHTContact existingContact = createCHTContact();

        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-123")).thenReturn(existingContact);

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertEquals("UPDATE", result.getChtOperationType());
        verify(chtApiClient).updatePerson(eq("cht-uuid-123"), eq(contact));
        verify(chtApiClient, never()).createPerson(any());
    }

    // ==================== Skip Conditions ====================

    @Test
    void skipsNonPatientResources() {
        Observation observation = new Observation();
        observation.setId("obs-1");

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(observation);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        verifyNoInteractions(transformer);
    }

    @Test
    void skipsPatientWithoutCHTIdentifiers() {
        Patient patient = new Patient();
        patient.setId("patient-no-cht");
        patient.addIdentifier()
            .setSystem("http://other-system")
            .setValue("other-id");

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        verifyNoInteractions(transformer);
    }

    @Test
    void skipsAlreadyProcessedResource() throws Exception {
        Patient patient = createFHIROnlyPatient();
        patient.getMeta().setLastUpdated(new Date(1700000000000L));
        CHTContact contact = createCHTContact();

        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-123"))
            .thenThrow(new MediatorException("Not found", "CHT", "getContact", 404));

        // Process first time
        CHTReverseSyncResult first = reverseSyncService.processReverseSync(patient);
        assertTrue(first.isSuccess());
        assertFalse(first.isSkipped());

        // Process same resource again — should be skipped
        CHTReverseSyncResult second = reverseSyncService.processReverseSync(patient);
        assertTrue(second.isSuccess());
        assertTrue(second.isSkipped());
        assertEquals("ALREADY_PROCESSED", second.getConflictResolution());
    }

    // ==================== Error Handling ====================

    @Test
    void handlesTransformationFailure() throws Exception {
        Patient patient = createFHIROnlyPatient();

        when(transformer.transform(any()))
            .thenThrow(new RuntimeException("Transformation failed"));

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Transformation failed"));
    }

    @Test
    void handlesCHTStorageFailure() throws Exception {
        Patient patient = createFHIROnlyPatient();
        CHTContact contact = createCHTContact();

        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-123"))
            .thenThrow(new MediatorException("Not found", "CHT", "getContact", 404));
        doThrow(new MediatorException("Connection refused", "CHT", "createPerson", 500))
            .when(chtApiClient).createPerson(any());

        CHTReverseSyncResult result = reverseSyncService.processReverseSync(patient);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== Test Data ====================

    private Patient createCHTOriginatedPatient() {
        Patient patient = new Patient();
        patient.setId("patient-from-cht");
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/cht-uuid")
            .setValue("cht-uuid-456");

        // Tag indicating this came from CHT
        Meta meta = new Meta();
        meta.addTag().setCode("CHT");
        patient.setMeta(meta);

        return patient;
    }

    private Patient createFHIROnlyPatient() {
        Patient patient = new Patient();
        patient.setId("patient-fhir-only");
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/cht-uuid")
            .setValue("cht-uuid-123");

        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        patient.setMeta(meta);

        patient.addName()
            .setFamily("Hassan")
            .addGiven("Ahmed");
        patient.setGender(Enumerations.AdministrativeGender.MALE);

        return patient;
    }

    private CHTContact createCHTContact() {
        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-123");
        contact.setName("Ahmed Hassan");
        contact.setType("person");
        contact.setSex("male");
        return contact;
    }
}
