package com.smartbridge.core.model.fhir;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.junit.jupiter.api.Test;

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
}
