package com.smartbridge.core.flow;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.transformation.CHTToFHIRPersonTransformer;
import com.smartbridge.core.transformation.CHTToFHIRPlaceTransformer;
import com.smartbridge.core.transformation.CHTToFHIRReportTransformer;
import com.smartbridge.core.validation.CHTContactValidator;
import com.smartbridge.core.validation.CHTReportValidator;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CHTIngestionFlowServiceTest {

    @Mock private CHTContactValidator contactValidator;
    @Mock private CHTReportValidator reportValidator;
    @Mock private CHTToFHIRPersonTransformer personTransformer;
    @Mock private CHTToFHIRPlaceTransformer placeTransformer;
    @Mock private CHTToFHIRReportTransformer reportTransformer;
    @Mock private ResilientFHIRClient resilientFHIRClient;
    @Mock private FHIRClientService fhirClient;
    @Mock private MessageProducerService messageProducer;
    @Mock private AuditLogger auditLogger;

    private CHTIngestionFlowService service;

    @BeforeEach
    void setUp() {
        service = new CHTIngestionFlowService(
            contactValidator, reportValidator,
            personTransformer, placeTransformer, reportTransformer,
            resilientFHIRClient, fhirClient,
            messageProducer, auditLogger,
            Executors.newSingleThreadExecutor());
    }

    // ==================== Contact Ingestion Tests ====================

    @Test
    void processContactIngestion_Success() throws Exception {
        CHTContact contact = createTestContact();

        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.valid());

        Patient patient = new Patient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "CHT", "id-1");
        when(personTransformer.transform(contact)).thenReturn(wrapper);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient/fhir-123"));
        when(resilientFHIRClient.createPatient(any())).thenReturn(outcome);

        IngestionFlowService.IngestionFlowResult result = service.processContactIngestion(contact);

        assertTrue(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
        assertEquals("fhir-123", result.getFhirResourceId());
    }

    @Test
    void processContactIngestion_ValidationFails() throws Exception {
        CHTContact contact = createTestContact();

        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.invalid("Missing name"));

        IngestionFlowService.IngestionFlowResult result = service.processContactIngestion(contact);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("validation failed"));
        verify(personTransformer, never()).transform(any());
    }

    @Test
    void processContactIngestion_TransformationFails() throws Exception {
        CHTContact contact = createTestContact();

        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.valid());
        when(personTransformer.transform(contact))
            .thenThrow(new com.smartbridge.core.interfaces.TransformationException(
                "Bad data", "CHT", "FHIR", "ERROR"));

        IngestionFlowService.IngestionFlowResult result = service.processContactIngestion(contact);

        assertFalse(result.isSuccess());
        verify(resilientFHIRClient, never()).createPatient(any());
        verify(messageProducer).sendToRetryQueue(any());
    }

    @Test
    void processContactIngestion_FHIRStorageFails() throws Exception {
        CHTContact contact = createTestContact();

        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.valid());

        Patient patient = new Patient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "CHT", "id-1");
        when(personTransformer.transform(contact)).thenReturn(wrapper);
        when(resilientFHIRClient.createPatient(any()))
            .thenThrow(new RuntimeException("FHIR server down"));

        IngestionFlowService.IngestionFlowResult result = service.processContactIngestion(contact);

        assertFalse(result.isSuccess());
        verify(messageProducer).sendToRetryQueue(any());
    }

    // ==================== Deduplication Tests ====================

    @Test
    void processContactIngestion_SkipsDuplicate() throws Exception {
        CHTContact contact = createTestContact();
        contact.setRev("1-abc");

        // First call — process normally
        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.valid());
        Patient patient = new Patient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "CHT", "id-1");
        when(personTransformer.transform(contact)).thenReturn(wrapper);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient/fhir-123"));
        when(resilientFHIRClient.createPatient(any())).thenReturn(outcome);

        service.processContactIngestion(contact);

        // Second call — should be skipped
        IngestionFlowService.IngestionFlowResult result2 = service.processContactIngestion(contact);
        assertTrue(result2.isSuccess());

        // Transformer should only be called once (first time)
        verify(personTransformer, times(1)).transform(contact);
    }

    @Test
    void isDuplicate_ReturnsFalseForNewDocument() {
        assertFalse(service.isDuplicate("new-id", "1-rev"));
    }

    @Test
    void isDuplicate_ReturnsFalseForNulls() {
        assertFalse(service.isDuplicate(null, "rev"));
        assertFalse(service.isDuplicate("id", null));
    }

    // ==================== Place Ingestion Tests ====================

    @Test
    void processPlaceIngestion_OrganizationOnly() throws Exception {
        CHTPlace place = createTestPlace(false);

        Organization org = new Organization();
        FHIRResourceWrapper<Organization> orgWrapper = FHIRResourceWrapper.forOrganization(org, "CHT", "place-1");
        when(placeTransformer.transform(place)).thenReturn(List.of(orgWrapper));

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Organization/org-1"));
        when(resilientFHIRClient.createOrganization(any())).thenReturn(outcome);

        IngestionFlowService.IngestionFlowResult result = service.processPlaceIngestion(place);

        assertTrue(result.isSuccess());
        assertTrue(result.isFhirStorageCompleted());
        verify(resilientFHIRClient).createOrganization(any());
        verify(resilientFHIRClient, never()).createLocation(any());
    }

    @Test
    void processPlaceIngestion_OrganizationAndLocation() throws Exception {
        CHTPlace place = createTestPlace(true);

        Organization org = new Organization();
        Location loc = new Location();
        FHIRResourceWrapper<Organization> orgWrapper = FHIRResourceWrapper.forOrganization(org, "CHT", "place-1");
        FHIRResourceWrapper<Location> locWrapper = FHIRResourceWrapper.forLocation(loc, "CHT", "place-1");
        when(placeTransformer.transform(place)).thenReturn(List.of(orgWrapper, locWrapper));

        MethodOutcome orgOutcome = new MethodOutcome();
        orgOutcome.setId(new IdType("Organization/org-1"));
        when(resilientFHIRClient.createOrganization(any())).thenReturn(orgOutcome);

        MethodOutcome locOutcome = new MethodOutcome();
        locOutcome.setId(new IdType("Location/loc-1"));
        when(resilientFHIRClient.createLocation(any())).thenReturn(locOutcome);

        IngestionFlowService.IngestionFlowResult result = service.processPlaceIngestion(place);

        assertTrue(result.isSuccess());
        verify(resilientFHIRClient).createOrganization(any());
        verify(resilientFHIRClient).createLocation(any());
    }

    // ==================== Report Ingestion Tests ====================

    @Test
    void processReportIngestion_EncounterAndObservations() throws Exception {
        CHTReport report = createTestReport();

        when(reportValidator.validate(report))
            .thenReturn(CHTReportValidator.ValidationResult.valid());

        Encounter encounter = new Encounter();
        Observation obs = new Observation();
        FHIRResourceWrapper<Encounter> encWrapper = FHIRResourceWrapper.forEncounter(encounter, "CHT", "rep-1");
        FHIRResourceWrapper<Observation> obsWrapper = FHIRResourceWrapper.forObservation(obs, "CHT", "rep-1");
        when(reportTransformer.transform(report)).thenReturn(List.of(encWrapper, obsWrapper));

        MethodOutcome encOutcome = new MethodOutcome();
        encOutcome.setId(new IdType("Encounter/enc-1"));
        when(resilientFHIRClient.createEncounter(any())).thenReturn(encOutcome);

        MethodOutcome obsOutcome = new MethodOutcome();
        obsOutcome.setId(new IdType("Observation/obs-1"));
        when(resilientFHIRClient.createObservation(any())).thenReturn(obsOutcome);

        IngestionFlowService.IngestionFlowResult result = service.processReportIngestion(report);

        assertTrue(result.isSuccess());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
        verify(resilientFHIRClient).createEncounter(any());
        verify(resilientFHIRClient).createObservation(any());
    }

    @Test
    void processReportIngestion_ValidationFails() throws Exception {
        CHTReport report = createTestReport();

        when(reportValidator.validate(report))
            .thenReturn(CHTReportValidator.ValidationResult.invalid("Missing form"));

        IngestionFlowService.IngestionFlowResult result = service.processReportIngestion(report);

        assertFalse(result.isSuccess());
        verify(reportTransformer, never()).transform(any());
    }

    // ==================== Concurrent Processing Tests ====================

    @Test
    void processContactsConcurrently_EmptyList() {
        List<IngestionFlowService.IngestionFlowResult> results =
            service.processContactsConcurrently(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void processContactsConcurrently_NullList() {
        List<IngestionFlowService.IngestionFlowResult> results =
            service.processContactsConcurrently(null);
        assertTrue(results.isEmpty());
    }

    // ==================== Deduplication Cache Tests ====================

    @Test
    void clearDeduplicationCache_AllowsReprocessing() throws Exception {
        CHTContact contact = createTestContact();
        contact.setRev("1-abc");

        when(contactValidator.validate(contact))
            .thenReturn(CHTContactValidator.ValidationResult.valid());
        Patient patient = new Patient();
        FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(patient, "CHT", "id-1");
        when(personTransformer.transform(contact)).thenReturn(wrapper);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Patient/fhir-123"));
        when(resilientFHIRClient.createPatient(any())).thenReturn(outcome);

        service.processContactIngestion(contact);
        service.clearDeduplicationCache();
        service.processContactIngestion(contact);

        // Should be called twice after cache clear
        verify(personTransformer, times(2)).transform(contact);
    }

    // ==================== Helpers ====================

    private CHTContact createTestContact() {
        CHTContact c = new CHTContact();
        c.setId("contact-uuid-1");
        c.setName("Amina Juma");
        c.setType("contact");
        c.setContactType("person");
        c.setSex("female");
        c.setDateOfBirth("1990-01-15");
        return c;
    }

    private CHTPlace createTestPlace(boolean withGeo) {
        CHTPlace p = new CHTPlace();
        p.setId("place-uuid-1");
        p.setName("Mwananyamala Clinic");
        p.setType("contact");
        p.setContactType("clinic");
        if (withGeo) {
            p.setGeolocation("-6.7924 39.2083");
        }
        return p;
    }

    private CHTReport createTestReport() {
        CHTReport r = new CHTReport();
        r.setId("report-uuid-1");
        r.setType("data_record");
        r.setForm("pregnancy_visit");
        r.setReportedDate(1700000000000L);
        r.setFields(Map.of("weight", 65.5, "temperature", 36.8));
        return r;
    }
}
