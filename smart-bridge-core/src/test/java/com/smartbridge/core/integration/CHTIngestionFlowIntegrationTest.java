package com.smartbridge.core.integration;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.smartbridge.core.audit.AuditLogger;
import com.smartbridge.core.client.FHIRClientService;
import com.smartbridge.core.flow.CHTIngestionFlowService;
import com.smartbridge.core.flow.IngestionFlowService;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.queue.MessageProducerService;
import com.smartbridge.core.queue.QueueMessage;
import com.smartbridge.core.resilience.CircuitBreaker;
import com.smartbridge.core.resilience.ResilientFHIRClient;
import com.smartbridge.core.resilience.RetryPolicy;
import com.smartbridge.core.transformation.CHTToFHIRPersonTransformer;
import com.smartbridge.core.transformation.CHTToFHIRPlaceTransformer;
import com.smartbridge.core.transformation.CHTToFHIRReportTransformer;
import com.smartbridge.core.validation.CHTContactValidator;
import com.smartbridge.core.validation.CHTReportValidator;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CHT ingestion flows.
 * Tests end-to-end: validation -> transformation -> FHIR storage
 * using real validators and transformers with stub FHIR clients.
 */
class CHTIngestionFlowIntegrationTest {

    private CHTIngestionFlowService flowService;
    private StubFHIRClientService stubFhirClient;

    @BeforeEach
    void setUp() {
        CHTContactValidator contactValidator = new CHTContactValidator();
        CHTReportValidator reportValidator = new CHTReportValidator();
        CHTToFHIRPersonTransformer personTransformer = new CHTToFHIRPersonTransformer();
        CHTToFHIRPlaceTransformer placeTransformer = new CHTToFHIRPlaceTransformer();
        CHTToFHIRReportTransformer reportTransformer = new CHTToFHIRReportTransformer();
        stubFhirClient = new StubFHIRClientService();
        StubResilientFHIRClient resilientClient = new StubResilientFHIRClient(stubFhirClient);
        StubMessageProducer messageProducer = new StubMessageProducer();
        AuditLogger auditLogger = new AuditLogger();

        flowService = new CHTIngestionFlowService(
            contactValidator, reportValidator,
            personTransformer, placeTransformer, reportTransformer,
            resilientClient, stubFhirClient, messageProducer,
            auditLogger, Runnable::run);
    }

    // ==================== Contact Ingestion ====================

    @Test
    void contactIngestion_RealCHTPersonData() {
        CHTContact contact = createRealCHTContact();

        IngestionFlowService.IngestionFlowResult result = flowService.processContactIngestion(contact);

        assertTrue(result.isSuccess(), "Contact ingestion should succeed: " + result.getErrorMessage());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
        assertNotNull(result.getFhirResourceId());
    }

    @Test
    void contactIngestion_ProducesCorrectFHIRPatient() {
        CHTContact contact = createRealCHTContact();

        flowService.processContactIngestion(contact);

        Patient patient = stubFhirClient.getLastCreatedPatient();
        assertNotNull(patient);

        // CHT UUID identifier
        assertTrue(patient.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-uuid".equals(id.getSystem())
                && "3973f2aa-3032-41e5-9418-5c493927b2c2".equals(id.getValue())));

        // Name mapping: "Ahmed Ali Hassan" -> given="Ahmed", family="Ali Hassan"
        assertEquals("Ahmed", patient.getName().get(0).getGiven().get(0).getValue());
        assertEquals("Ali Hassan", patient.getName().get(0).getFamily());

        // Gender
        assertEquals(Enumerations.AdministrativeGender.MALE, patient.getGender());

        // Birth date
        assertNotNull(patient.getBirthDate());

        // Phone
        assertTrue(patient.getTelecom().stream()
            .anyMatch(t -> "0777123456".equals(t.getValue())));

        // Managing organization from parent
        assertNotNull(patient.getManagingOrganization());
        assertEquals("Organization/88f6dc1f-b4d8-4238-90de-ca274921be9b",
            patient.getManagingOrganization().getReference());
    }

    @Test
    void contactIngestion_DeduplicatesOnIdAndRev() {
        CHTContact contact = createRealCHTContact();

        IngestionFlowService.IngestionFlowResult first = flowService.processContactIngestion(contact);
        IngestionFlowService.IngestionFlowResult second = flowService.processContactIngestion(contact);

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        // Second call should be deduplicated — only 1 patient created
        assertEquals(1, stubFhirClient.getPatientCreateCount());
    }

    @Test
    void contactIngestion_InvalidContactFails() {
        CHTContact contact = new CHTContact();
        // Missing required fields

        IngestionFlowService.IngestionFlowResult result = flowService.processContactIngestion(contact);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== Place Ingestion ====================

    @Test
    void placeIngestion_RealCHTHousehold() {
        CHTPlace place = createRealCHTPlace();

        IngestionFlowService.IngestionFlowResult result = flowService.processPlaceIngestion(place);

        assertTrue(result.isSuccess(), "Place ingestion should succeed: " + result.getErrorMessage());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
    }

    @Test
    void placeIngestion_ProducesOrganizationWithCorrectType() {
        CHTPlace place = createRealCHTPlace();

        flowService.processPlaceIngestion(place);

        Organization org = stubFhirClient.getLastCreatedOrganization();
        assertNotNull(org, "Organization should have been created");

        // CHT place UUID identifier
        assertTrue(org.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-place-uuid".equals(id.getSystem())
                && "88f6dc1f-b4d8-4238-90de-ca274921be9b".equals(id.getValue())));

        // Name
        assertEquals("Ahmed Ali Hassan - 12/56", org.getName());

        // Type: "clinic" -> "team" (Organizational team)
        assertTrue(org.getType().stream()
            .flatMap(cc -> cc.getCoding().stream())
            .anyMatch(c -> "team".equals(c.getCode())));

        // Parent hierarchy
        assertNotNull(org.getPartOf());
        assertEquals("Organization/41da8c22-6f4e-5d8c-9a6f-52bd10f29b60",
            org.getPartOf().getReference());
    }

    @Test
    void placeIngestion_EmptyGeolocationProducesOnlyOrganization() {
        CHTPlace place = createRealCHTPlace();
        // Real CHT data has geolocation: "" (empty string) for this household

        flowService.processPlaceIngestion(place);

        // Only Organization, no Location (empty geolocation)
        assertEquals(1, stubFhirClient.getOrganizationCreateCount());
        assertEquals(0, stubFhirClient.getLocationCreateCount());
    }

    // ==================== Report Ingestion ====================

    @Test
    void reportIngestion_RealCHTReport() {
        CHTReport report = createRealCHTReport();

        IngestionFlowService.IngestionFlowResult result = flowService.processReportIngestion(report);

        assertTrue(result.isSuccess(), "Report ingestion should succeed: " + result.getErrorMessage());
        assertTrue(result.isValidationPassed());
        assertTrue(result.isTransformationCompleted());
        assertTrue(result.isFhirStorageCompleted());
    }

    @Test
    void reportIngestion_ProducesEncounterAndObservations() {
        CHTReport report = createRealCHTReport();

        flowService.processReportIngestion(report);

        Encounter encounter = stubFhirClient.getLastCreatedEncounter();
        assertNotNull(encounter, "Encounter should have been created");

        // Encounter identifier
        assertTrue(encounter.getIdentifier().stream()
            .anyMatch(id -> "http://moh.go.tz/identifier/cht-report-uuid".equals(id.getSystem())
                && "42762c2b-b082-445f-9cc7-17093e5520a8".equals(id.getValue())));

        // Encounter status
        assertEquals(Encounter.EncounterStatus.FINISHED, encounter.getStatus());

        // Form type
        assertTrue(encounter.getType().stream()
            .flatMap(cc -> cc.getCoding().stream())
            .anyMatch(c -> "infant_child".equals(c.getCode())));

        // Observations should have been created for clinical fields
        assertTrue(stubFhirClient.getObservationCreateCount() > 0);
    }

    @Test
    void reportIngestion_PerformanceUnder5Seconds() {
        CHTReport report = createRealCHTReport();
        long start = System.currentTimeMillis();

        IngestionFlowService.IngestionFlowResult result = flowService.processReportIngestion(report);

        long duration = System.currentTimeMillis() - start;
        assertTrue(result.isSuccess());
        assertTrue(duration < 5000,
            "Report ingestion should finish within 5 seconds (actual: " + duration + "ms)");
    }

    // ==================== Test Data (matches real CHT structures) ====================

    private CHTContact createRealCHTContact() {
        CHTContact contact = new CHTContact();
        contact.setId("3973f2aa-3032-41e5-9418-5c493927b2c2");
        contact.setRev("1-d0b29abb43b7802bfb3ce84185b9f9af");
        contact.setType("person");
        contact.setName("Ahmed Ali Hassan");
        contact.setSex("male");
        contact.setDateOfBirth("1991-01-01");
        contact.setPhone("0777123456");
        contact.setReportedDate(1773666649147L);

        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("88f6dc1f-b4d8-4238-90de-ca274921be9b");
        CHTContact.CHTParentRef grandparent = new CHTContact.CHTParentRef();
        grandparent.setId("41da8c22-6f4e-5d8c-9a6f-52bd10f29b60");
        parent.setParent(grandparent);
        contact.setParent(parent);

        return contact;
    }

    private CHTPlace createRealCHTPlace() {
        CHTPlace place = new CHTPlace();
        place.setId("88f6dc1f-b4d8-4238-90de-ca274921be9b");
        place.setRev("1-d1c46c8e46e843115ac5a79998e7348c");
        place.setType("clinic");
        place.setName("Ahmed Ali Hassan - 12/56");
        place.setGeolocation(""); // Real data has empty string
        place.setReportedDate(1773666649147L);

        CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
        parent.setId("41da8c22-6f4e-5d8c-9a6f-52bd10f29b60");
        CHTContact.CHTParentRef grandparent = new CHTContact.CHTParentRef();
        grandparent.setId("91fa8bc1-eac1-5b2a-9fc4-cb1c893a6c73");
        parent.setParent(grandparent);
        place.setParent(parent);

        CHTPlace.CHTContactRef contactRef = new CHTPlace.CHTContactRef();
        contactRef.setId("3973f2aa-3032-41e5-9418-5c493927b2c2");
        place.setContact(contactRef);

        return place;
    }

    private CHTReport createRealCHTReport() {
        CHTReport report = new CHTReport();
        report.setId("42762c2b-b082-445f-9cc7-17093e5520a8");
        report.setRev("1-99db299061fa55824a3bc5669dd48b20");
        report.setType("data_record");
        report.setForm("infant_child");
        report.setReportedDate(1774948502253L);

        CHTPlace.CHTContactRef contact = new CHTPlace.CHTContactRef();
        contact.setId("f9120a3c-1b55-5d5e-87cc-6b6773d7984a");
        report.setContact(contact);

        // Real CHT report fields — flat keys only (nested objects become Maps)
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("patient_id", "9339db36-040b-4c0c-b2b2-347f5c44983c");
        fields.put("child_name", "Hassan Ali Juma");
        fields.put("child_date_of_birth", "2022-10-15");
        fields.put("age_months", "42");
        fields.put("immunization_status", "delayed");
        fields.put("person_sex", "male");

        // Nested vitals become a sub-map
        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("child_weight", "7");
        vitals.put("child_height", "52");
        fields.put("vitals", vitals);

        report.setFields(fields);
        report.setPatientId("9339db36-040b-4c0c-b2b2-347f5c44983c");

        return report;
    }

    // ===== Stub test doubles =====

    private static class StubFHIRClientService extends FHIRClientService {
        private Patient lastCreatedPatient;
        private Organization lastCreatedOrganization;
        private Encounter lastCreatedEncounter;
        private int patientCreateCount = 0;
        private int organizationCreateCount = 0;
        private int locationCreateCount = 0;
        private int encounterCreateCount = 0;
        private int observationCreateCount = 0;

        @Override
        public MethodOutcome createPatient(Patient patient) {
            lastCreatedPatient = patient;
            patientCreateCount++;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Patient", "stub-patient-" + patientCreateCount));
            return outcome;
        }

        @Override
        public MethodOutcome createOrganization(Organization organization) {
            lastCreatedOrganization = organization;
            organizationCreateCount++;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Organization", "stub-org-" + organizationCreateCount));
            return outcome;
        }

        @Override
        public MethodOutcome createLocation(Location location) {
            locationCreateCount++;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Location", "stub-loc-" + locationCreateCount));
            return outcome;
        }

        @Override
        public MethodOutcome createEncounter(Encounter encounter) {
            lastCreatedEncounter = encounter;
            encounterCreateCount++;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Encounter", "stub-enc-" + encounterCreateCount));
            return outcome;
        }

        @Override
        public MethodOutcome createObservation(Observation observation) {
            observationCreateCount++;
            MethodOutcome outcome = new MethodOutcome();
            outcome.setId(new IdType("Observation", "stub-obs-" + observationCreateCount));
            return outcome;
        }

        @Override
        public String getServerBaseUrl() {
            return "http://localhost:8080/fhir";
        }

        Patient getLastCreatedPatient() { return lastCreatedPatient; }
        Organization getLastCreatedOrganization() { return lastCreatedOrganization; }
        Encounter getLastCreatedEncounter() { return lastCreatedEncounter; }
        int getPatientCreateCount() { return patientCreateCount; }
        int getOrganizationCreateCount() { return organizationCreateCount; }
        int getLocationCreateCount() { return locationCreateCount; }
        int getObservationCreateCount() { return observationCreateCount; }
    }

    private static class StubResilientFHIRClient extends ResilientFHIRClient {
        private final FHIRClientService delegate;

        StubResilientFHIRClient(FHIRClientService delegate) {
            super(delegate, new CircuitBreaker("test"), new RetryPolicy("test"));
            this.delegate = delegate;
        }

        @Override
        public MethodOutcome createPatient(Patient patient) throws Exception {
            return delegate.createPatient(patient);
        }

        @Override
        public MethodOutcome createOrganization(Organization organization) throws Exception {
            return delegate.createOrganization(organization);
        }

        @Override
        public MethodOutcome createLocation(Location location) throws Exception {
            return delegate.createLocation(location);
        }

        @Override
        public MethodOutcome createEncounter(Encounter encounter) throws Exception {
            return delegate.createEncounter(encounter);
        }

        @Override
        public MethodOutcome createObservation(Observation observation) throws Exception {
            return delegate.createObservation(observation);
        }
    }

    private static class StubMessageProducer extends MessageProducerService {
        StubMessageProducer() {
            super(null);
        }

        @Override
        public void sendMessage(QueueMessage queueMessage) {}

        @Override
        public void sendToRetryQueue(QueueMessage queueMessage) {}

        @Override
        public void sendToDeadLetterQueue(QueueMessage queueMessage) {}
    }
}
