package com.smartbridge.mediators.cht;

import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRChangeDetectionService;
import com.smartbridge.core.interfaces.MediatorException;
import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.transformation.FHIRToCHTPersonTransformer;
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

@ExtendWith(MockitoExtension.class)
class CHTReverseSyncFlowServiceTest {

    @Mock private FHIRChangeDetectionService changeDetectionService;
    @Mock private FHIRToCHTPersonTransformer transformer;
    @Mock private CHTApiClient chtApiClient;
    @Mock private AuditLogger auditLogger;

    private CHTReverseSyncFlowService service;

    @BeforeEach
    void setUp() {
        service = new CHTReverseSyncFlowService(
            changeDetectionService, transformer, chtApiClient, auditLogger,
            Executors.newSingleThreadExecutor());
    }

    // ==================== Success Flows ====================

    @Test
    void processReverseSync_CreatesNewCHTContact() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-1", "pid-1");

        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-1");
        when(transformer.transform(any())).thenReturn(contact);
        // Contact not found in CHT → create
        when(chtApiClient.getContact("cht-uuid-1")).thenThrow(new MediatorException("Not found", "CHT_CLIENT", "getContact", 404));
        when(chtApiClient.createPerson(contact)).thenReturn(contact);

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertFalse(result.isSkipped());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isChtStorageCompleted());
        assertEquals("CREATE", result.getChtOperationType());
        assertEquals("cht-uuid-1", result.getChtContactId());
    }

    @Test
    void processReverseSync_UpdatesExistingCHTContact() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-2", "pid-2");

        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-2");
        when(transformer.transform(any())).thenReturn(contact);
        // Contact found in CHT → update
        CHTContact existing = new CHTContact();
        existing.setId("cht-uuid-2");
        when(chtApiClient.getContact("cht-uuid-2")).thenReturn(existing);
        when(chtApiClient.updatePerson(eq("cht-uuid-2"), eq(contact))).thenReturn(contact);

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertEquals("UPDATE", result.getChtOperationType());
        assertTrue(result.isChtStorageCompleted());
        verify(chtApiClient).updatePerson("cht-uuid-2", contact);
    }

    // ==================== Circular Update Prevention ====================

    @Test
    void processReverseSync_SkipsCHTOriginatedChange() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-3", null);
        // Tag with CHT source
        patient.getMeta().addTag().setCode("CHT");

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertTrue(result.isConflictDetected());
        assertEquals("CIRCULAR_UPDATE_SKIP", result.getConflictResolution());
        verifyNoInteractions(transformer);
        verifyNoInteractions(chtApiClient);
    }

    @Test
    void isUpdateFromCHT_ReturnsTrueForCHTTag() {
        Patient patient = new Patient();
        patient.getMeta().addTag().setCode("CHT");
        assertTrue(service.isUpdateFromCHT(patient));
    }

    @Test
    void isUpdateFromCHT_ReturnsFalseForNoTags() {
        Patient patient = new Patient();
        assertFalse(service.isUpdateFromCHT(patient));
    }

    @Test
    void isUpdateFromCHT_ReturnsFalseForOtherTags() {
        Patient patient = new Patient();
        patient.getMeta().addTag().setCode("UCS");
        assertFalse(service.isUpdateFromCHT(patient));
    }

    // ==================== Missing CHT Identifiers ====================

    @Test
    void processReverseSync_SkipsPatientWithoutCHTIdentifiers() throws Exception {
        Patient patient = new Patient();
        patient.setId("Patient/fhir-only-1");
        // No CHT identifier
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/opensrp-id")
            .setValue("opensrp-123");

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        verifyNoInteractions(transformer);
    }

    @Test
    void processReverseSync_SkipsPatientWithNoIdentifiers() throws Exception {
        Patient patient = new Patient();
        patient.setId("Patient/bare-1");

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
    }

    // ==================== Non-Patient Resource ====================

    @Test
    void processReverseSync_SkipsNonPatientResource() {
        Observation obs = new Observation();
        obs.setId("Observation/obs-1");

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(obs);

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        verifyNoInteractions(transformer);
    }

    // ==================== Already Processed ====================

    @Test
    void processReverseSync_SkipsAlreadyProcessedResource() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-4", null);
        Date lastUpdated = new Date(1700000000000L);
        patient.getMeta().setLastUpdated(lastUpdated);

        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-4");
        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-4")).thenThrow(new MediatorException("Not found", "CHT_CLIENT", "getContact", 404));
        when(chtApiClient.createPerson(contact)).thenReturn(contact);

        // First call succeeds
        service.processReverseSync(patient);

        // Second call with same lastUpdated should be skipped
        CHTReverseSyncFlowService.CHTReverseSyncResult result2 = service.processReverseSync(patient);

        assertTrue(result2.isSuccess());
        assertTrue(result2.isSkipped());
        // Transformer only called once (first time)
        verify(transformer, times(1)).transform(any());
    }

    @Test
    void isAlreadyProcessed_ReturnsFalseForNewResource() {
        assertFalse(service.isAlreadyProcessed("new-id", new Date()));
    }

    @Test
    void isAlreadyProcessed_ReturnsFalseForNulls() {
        assertFalse(service.isAlreadyProcessed(null, new Date()));
        assertFalse(service.isAlreadyProcessed("id", null));
    }

    // ==================== Transformation Failure ====================

    @Test
    void processReverseSync_HandleTransformationFailure() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-5", null);

        when(transformer.transform(any()))
            .thenThrow(new TransformationException("Bad data", "FHIR", "CHT", "ERROR"));

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        verifyNoInteractions(chtApiClient);
    }

    // ==================== CHT Storage Failure ====================

    @Test
    void processReverseSync_HandleCHTStorageFailure() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-6", null);

        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-6");
        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-6")).thenThrow(new MediatorException("Not found", "CHT_CLIENT", "getContact", 404));
        when(chtApiClient.createPerson(contact)).thenThrow(new MediatorException("Server error", "CHT_CLIENT", "createPerson", 500));

        CHTReverseSyncFlowService.CHTReverseSyncResult result = service.processReverseSync(patient);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== Audit Logging ====================

    @Test
    void processReverseSync_AuditsSuccessfulSync() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-7", null);

        CHTContact contact = new CHTContact();
        contact.setId("cht-uuid-7");
        when(transformer.transform(any())).thenReturn(contact);
        when(chtApiClient.getContact("cht-uuid-7")).thenThrow(new MediatorException("Not found", "CHT_CLIENT", "getContact", 404));
        when(chtApiClient.createPerson(contact)).thenReturn(contact);

        service.processReverseSync(patient);

        verify(auditLogger).logTransformation(
            eq("FHIR"), eq("CHT"), eq("REVERSE_SYNC"),
            any(), eq("cht-uuid-7"), eq(true), any());
    }

    @Test
    void processReverseSync_AuditsFailedSync() throws Exception {
        Patient patient = createPatientWithCHTIdentifiers("cht-uuid-8", null);

        when(transformer.transform(any()))
            .thenThrow(new TransformationException("fail", "FHIR", "CHT", "ERROR"));

        service.processReverseSync(patient);

        verify(auditLogger).logTransformation(
            eq("FHIR"), eq("CHT"), eq("REVERSE_SYNC"),
            any(), isNull(), eq(false), any());
    }

    // ==================== Initialization ====================

    @Test
    void initializeReverseSyncFlow_RegistersChangeListener() {
        service.initializeReverseSyncFlow();

        verify(changeDetectionService).registerChangeListener(eq("Patient"), any());
    }

    // ==================== Helpers ====================

    private Patient createPatientWithCHTIdentifiers(String chtUuid, String chtPatientId) {
        Patient patient = new Patient();
        patient.setId("Patient/" + chtUuid);
        patient.addIdentifier()
            .setSystem("http://moh.go.tz/identifier/cht-uuid")
            .setValue(chtUuid);
        if (chtPatientId != null) {
            patient.addIdentifier()
                .setSystem("http://moh.go.tz/identifier/cht-patient-id")
                .setValue(chtPatientId);
        }
        return patient;
    }
}
