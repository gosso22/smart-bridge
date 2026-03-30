package com.smartbridge.core.model.fhir;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FHIR resource builders
 */
class FHIRResourceBuilderTest {

    @Test
    void testPatientBuilder_withAllFields() {
        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        
        Patient patient = FHIRResourceBuilder.patient()
            .withIdentifier("http://moh.go.tz/identifier/opensrp-id", "12345")
            .withIdentifier("http://moh.go.tz/identifier/national-id", "NID-67890")
            .withName("John", "Doe")
            .withGender("M")
            .withBirthDate(birthDate)
            .withAddress("Dar es Salaam", "Kinondoni", "Mwenge Village")
            .withId("patient-123")
            .build();

        assertNotNull(patient);
        assertEquals("patient-123", patient.getId());
        assertEquals(2, patient.getIdentifier().size());
        assertEquals("12345", patient.getIdentifier().get(0).getValue());
        assertEquals(AdministrativeGender.MALE, patient.getGender());
        assertEquals(1, patient.getName().size());
        assertEquals("John", patient.getName().get(0).getGivenAsSingleString());
        assertEquals("Doe", patient.getName().get(0).getFamily());
        assertNotNull(patient.getBirthDate());
    }

    @Test
    void testPatientBuilder_genderNormalization() {
        Patient male = FHIRResourceBuilder.patient().withGender("M").build();
        assertEquals(AdministrativeGender.MALE, male.getGender());

        Patient female = FHIRResourceBuilder.patient().withGender("F").build();
        assertEquals(AdministrativeGender.FEMALE, female.getGender());

        Patient other = FHIRResourceBuilder.patient().withGender("O").build();
        assertEquals(AdministrativeGender.OTHER, other.getGender());

        Patient unknown = FHIRResourceBuilder.patient().withGender(null).build();
        assertEquals(AdministrativeGender.UNKNOWN, unknown.getGender());
    }

    @Test
    void testObservationBuilder_withQuantityValue() {
        Observation observation = FHIRResourceBuilder.observation()
            .withId("obs-123")
            .withSubject("Patient/patient-123")
            .withCode("http://loinc.org", "8867-4", "Heart rate")
            .withValueQuantity(72.0, "beats/min", "http://unitsofmeasure.org", "/min")
            .withEffectiveDateTime(new Date())
            .withStatus(Observation.ObservationStatus.FINAL)
            .build();

        assertNotNull(observation);
        assertEquals("obs-123", observation.getId());
        assertEquals("Patient/patient-123", observation.getSubject().getReference());
        assertEquals(Observation.ObservationStatus.FINAL, observation.getStatus());
        assertTrue(observation.getValue() instanceof Quantity);
        Quantity quantity = (Quantity) observation.getValue();
        assertEquals(72.0, quantity.getValue().doubleValue());
    }

    @Test
    void testObservationBuilder_withStringValue() {
        Observation observation = FHIRResourceBuilder.observation()
            .withSubject("Patient/patient-123")
            .withCode("http://loinc.org", "8302-2", "Body height")
            .withValueString("Normal")
            .build();

        assertNotNull(observation);
        assertTrue(observation.getValue() instanceof StringType);
        assertEquals("Normal", ((StringType) observation.getValue()).getValue());
    }

    @Test
    void testTaskBuilder_withAllFields() {
        Task task = FHIRResourceBuilder.task()
            .withId("task-123")
            .withStatus(Task.TaskStatus.INPROGRESS)
            .withIntent(Task.TaskIntent.ORDER)
            .withFor("Patient/patient-123")
            .withDescription("Follow-up appointment")
            .withCode("http://hl7.org/fhir/CodeSystem/task-code", "fulfill", "Fulfill")
            .withAuthoredOn(new Date())
            .build();

        assertNotNull(task);
        assertEquals("task-123", task.getId());
        assertEquals(Task.TaskStatus.INPROGRESS, task.getStatus());
        assertEquals(Task.TaskIntent.ORDER, task.getIntent());
        assertEquals("Patient/patient-123", task.getFor().getReference());
        assertEquals("Follow-up appointment", task.getDescription());
    }

    @Test
    void testMedicationRequestBuilder_withAllFields() {
        MedicationRequest medRequest = FHIRResourceBuilder.medicationRequest()
            .withId("med-123")
            .withStatus(MedicationRequest.MedicationRequestStatus.ACTIVE)
            .withIntent(MedicationRequest.MedicationRequestIntent.ORDER)
            .withSubject("Patient/patient-123")
            .withMedicationCodeableConcept("http://www.nlm.nih.gov/research/umls/rxnorm", 
                "313782", "Acetaminophen 325 MG Oral Tablet")
            .withAuthoredOn(new Date())
            .withDosageInstruction("Take 1 tablet every 6 hours as needed")
            .build();

        assertNotNull(medRequest);
        assertEquals("med-123", medRequest.getId());
        assertEquals(MedicationRequest.MedicationRequestStatus.ACTIVE, medRequest.getStatus());
        assertEquals(MedicationRequest.MedicationRequestIntent.ORDER, medRequest.getIntent());
        assertEquals("Patient/patient-123", medRequest.getSubject().getReference());
        assertTrue(medRequest.getMedication() instanceof CodeableConcept);
        assertEquals(1, medRequest.getDosageInstruction().size());
    }

    @Test
    void testPatientBuilder_minimalFields() {
        Patient patient = FHIRResourceBuilder.patient()
            .withIdentifier("http://example.org", "123")
            .build();

        assertNotNull(patient);
        assertEquals(1, patient.getIdentifier().size());
        assertTrue(patient.getName().isEmpty());
    }

    // ========== Encounter Builder Tests ==========

    @Test
    void testEncounterBuilder_withAllFields() {
        Date now = new Date();

        Encounter encounter = FHIRResourceBuilder.encounter()
            .withId("enc-001")
            .withIdentifier("http://moh.go.tz/identifier/cht-report-uuid", "cht-report-001")
            .withStatus(Encounter.EncounterStatus.FINISHED)
            .withClass("http://terminology.hl7.org/CodeSystem/v3-ActCode", "AMB", "ambulatory")
            .withType("http://moh.go.tz/identifier/cht-form-type", "pregnancy_visit", "Pregnancy Visit")
            .withSubject("Patient/patient-123")
            .withParticipant("Practitioner/chw-001")
            .withPeriodStart(now)
            .build();

        assertNotNull(encounter);
        assertEquals("enc-001", encounter.getId());
        assertEquals(Encounter.EncounterStatus.FINISHED, encounter.getStatus());
        assertEquals(1, encounter.getIdentifier().size());
        assertEquals("cht-report-001", encounter.getIdentifier().get(0).getValue());
        assertEquals("AMB", encounter.getClass_().getCode());
        assertEquals(1, encounter.getType().size());
        assertEquals("pregnancy_visit", encounter.getType().get(0).getCodingFirstRep().getCode());
        assertEquals("Patient/patient-123", encounter.getSubject().getReference());
        assertEquals(1, encounter.getParticipant().size());
        assertEquals("Practitioner/chw-001", encounter.getParticipant().get(0).getIndividual().getReference());
        assertEquals(now, encounter.getPeriod().getStart());
    }

    @Test
    void testEncounterBuilder_minimalFields() {
        Encounter encounter = FHIRResourceBuilder.encounter()
            .withIdentifier("http://example.org", "enc-min")
            .build();

        assertNotNull(encounter);
        assertEquals(Encounter.EncounterStatus.FINISHED, encounter.getStatus());
        assertEquals(1, encounter.getIdentifier().size());
    }

    @Test
    void testEncounterBuilder_withPeriodStartAndEnd() {
        Date start = new Date(1680000000000L);
        Date end = new Date(1680003600000L);

        Encounter encounter = FHIRResourceBuilder.encounter()
            .withPeriodStart(start)
            .withPeriodEnd(end)
            .build();

        assertNotNull(encounter.getPeriod());
        assertEquals(start, encounter.getPeriod().getStart());
        assertEquals(end, encounter.getPeriod().getEnd());
    }

    // ========== Organization Builder Tests ==========

    @Test
    void testOrganizationBuilder_withAllFields() {
        Organization org = FHIRResourceBuilder.organization()
            .withId("org-001")
            .withIdentifier("http://moh.go.tz/identifier/cht-place-uuid", "cht-clinic-001")
            .withName("Kariakoo Clinic")
            .withType("http://terminology.hl7.org/CodeSystem/organization-type", "team", "Team")
            .withPartOf("Organization/org-parent-001")
            .withActive(true)
            .withContact("Dr. Hassan", "+255712345678")
            .build();

        assertNotNull(org);
        assertEquals("org-001", org.getId());
        assertEquals("Kariakoo Clinic", org.getName());
        assertTrue(org.getActive());
        assertEquals(1, org.getIdentifier().size());
        assertEquals("cht-clinic-001", org.getIdentifier().get(0).getValue());
        assertEquals(1, org.getType().size());
        assertEquals("team", org.getType().get(0).getCodingFirstRep().getCode());
        assertEquals("Organization/org-parent-001", org.getPartOf().getReference());
        assertEquals(1, org.getContact().size());
        assertEquals("Dr. Hassan", org.getContact().get(0).getName().getText());
        assertEquals("+255712345678", org.getContact().get(0).getTelecomFirstRep().getValue());
    }

    @Test
    void testOrganizationBuilder_minimalFields() {
        Organization org = FHIRResourceBuilder.organization()
            .withName("Test Org")
            .build();

        assertNotNull(org);
        assertEquals("Test Org", org.getName());
        assertTrue(org.getActive());
    }

    @Test
    void testOrganizationBuilder_withContactNameOnly() {
        Organization org = FHIRResourceBuilder.organization()
            .withName("Clinic")
            .withContact("Nurse Amina", null)
            .build();

        assertEquals(1, org.getContact().size());
        assertEquals("Nurse Amina", org.getContact().get(0).getName().getText());
        assertTrue(org.getContact().get(0).getTelecom().isEmpty());
    }

    // ========== Location Builder Tests ==========

    @Test
    void testLocationBuilder_withAllFields() {
        Location loc = FHIRResourceBuilder.location()
            .withId("loc-001")
            .withIdentifier("http://moh.go.tz/identifier/cht-place-uuid", "cht-clinic-001")
            .withName("Kariakoo Clinic")
            .withPosition(-6.8235, 39.2695)
            .withManagingOrganization("Organization/org-001")
            .withStatus(Location.LocationStatus.ACTIVE)
            .build();

        assertNotNull(loc);
        assertEquals("loc-001", loc.getId());
        assertEquals("Kariakoo Clinic", loc.getName());
        assertEquals(Location.LocationStatus.ACTIVE, loc.getStatus());
        assertEquals(1, loc.getIdentifier().size());
        assertNotNull(loc.getPosition());
        assertEquals(-6.8235, loc.getPosition().getLatitude().doubleValue(), 0.0001);
        assertEquals(39.2695, loc.getPosition().getLongitude().doubleValue(), 0.0001);
        assertEquals("Organization/org-001", loc.getManagingOrganization().getReference());
    }

    @Test
    void testLocationBuilder_minimalFields() {
        Location loc = FHIRResourceBuilder.location()
            .withName("Test Location")
            .build();

        assertNotNull(loc);
        assertEquals("Test Location", loc.getName());
        assertEquals(Location.LocationStatus.ACTIVE, loc.getStatus());
    }
}
