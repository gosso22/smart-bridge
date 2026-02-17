package com.smartbridge.core.model.fhir;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Builder utility for creating FHIR R4 resources with fluent API.
 * Provides convenient methods for constructing Patient, Observation, Task, and MedicationRequest resources.
 */
public class FHIRResourceBuilder {

    /**
     * Builder for FHIR R4 Patient resources
     */
    public static class PatientBuilder {
        private final Patient patient;

        public PatientBuilder() {
            this.patient = new Patient();
        }

        public PatientBuilder withIdentifier(String system, String value) {
            Identifier identifier = new Identifier();
            identifier.setSystem(system);
            identifier.setValue(value);
            patient.addIdentifier(identifier);
            return this;
        }

        public PatientBuilder withName(String given, String family) {
            HumanName name = new HumanName();
            name.addGiven(given);
            name.setFamily(family);
            patient.addName(name);
            return this;
        }

        public PatientBuilder withGender(String genderCode) {
            AdministrativeGender gender = normalizeGender(genderCode);
            patient.setGender(gender);
            return this;
        }

        public PatientBuilder withBirthDate(LocalDate birthDate) {
            if (birthDate != null) {
                Date date = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                patient.setBirthDate(date);
            }
            return this;
        }

        public PatientBuilder withAddress(String district, String city, String text) {
            Address address = new Address();
            if (district != null) {
                address.setDistrict(district);
            }
            if (city != null) {
                address.setCity(city);
            }
            if (text != null) {
                address.setText(text);
            }
            patient.addAddress(address);
            return this;
        }

        public PatientBuilder withId(String id) {
            patient.setId(id);
            return this;
        }

        public Patient build() {
            return patient;
        }

        private AdministrativeGender normalizeGender(String genderCode) {
            if (genderCode == null || genderCode.isEmpty()) {
                return AdministrativeGender.UNKNOWN;
            }
            switch (genderCode.toUpperCase()) {
                case "M":
                case "MALE":
                    return AdministrativeGender.MALE;
                case "F":
                case "FEMALE":
                    return AdministrativeGender.FEMALE;
                case "O":
                case "OTHER":
                    return AdministrativeGender.OTHER;
                default:
                    return AdministrativeGender.UNKNOWN;
            }
        }
    }

    /**
     * Builder for FHIR R4 Observation resources
     */
    public static class ObservationBuilder {
        private final Observation observation;

        public ObservationBuilder() {
            this.observation = new Observation();
            this.observation.setStatus(Observation.ObservationStatus.FINAL);
        }

        public ObservationBuilder withId(String id) {
            observation.setId(id);
            return this;
        }

        public ObservationBuilder withSubject(String patientReference) {
            Reference subject = new Reference(patientReference);
            observation.setSubject(subject);
            return this;
        }

        public ObservationBuilder withCode(String system, String code, String display) {
            CodeableConcept codeableConcept = new CodeableConcept();
            Coding coding = new Coding();
            coding.setSystem(system);
            coding.setCode(code);
            coding.setDisplay(display);
            codeableConcept.addCoding(coding);
            observation.setCode(codeableConcept);
            return this;
        }

        public ObservationBuilder withValueQuantity(double value, String unit, String system, String code) {
            Quantity quantity = new Quantity();
            quantity.setValue(value);
            quantity.setUnit(unit);
            quantity.setSystem(system);
            quantity.setCode(code);
            observation.setValue(quantity);
            return this;
        }

        public ObservationBuilder withValueString(String value) {
            observation.setValue(new StringType(value));
            return this;
        }

        public ObservationBuilder withEffectiveDateTime(Date dateTime) {
            observation.setEffective(new DateTimeType(dateTime));
            return this;
        }

        public ObservationBuilder withStatus(Observation.ObservationStatus status) {
            observation.setStatus(status);
            return this;
        }

        public Observation build() {
            return observation;
        }
    }

    /**
     * Builder for FHIR R4 Task resources
     */
    public static class TaskBuilder {
        private final Task task;

        public TaskBuilder() {
            this.task = new Task();
            this.task.setStatus(Task.TaskStatus.REQUESTED);
            this.task.setIntent(Task.TaskIntent.ORDER);
        }

        public TaskBuilder withId(String id) {
            task.setId(id);
            return this;
        }

        public TaskBuilder withStatus(Task.TaskStatus status) {
            task.setStatus(status);
            return this;
        }

        public TaskBuilder withIntent(Task.TaskIntent intent) {
            task.setIntent(intent);
            return this;
        }

        public TaskBuilder withFor(String patientReference) {
            Reference forReference = new Reference(patientReference);
            task.setFor(forReference);
            return this;
        }

        public TaskBuilder withDescription(String description) {
            task.setDescription(description);
            return this;
        }

        public TaskBuilder withCode(String system, String code, String display) {
            CodeableConcept codeableConcept = new CodeableConcept();
            Coding coding = new Coding();
            coding.setSystem(system);
            coding.setCode(code);
            coding.setDisplay(display);
            codeableConcept.addCoding(coding);
            task.setCode(codeableConcept);
            return this;
        }

        public TaskBuilder withAuthoredOn(Date date) {
            task.setAuthoredOn(date);
            return this;
        }

        public Task build() {
            return task;
        }
    }

    /**
     * Builder for FHIR R4 MedicationRequest resources
     */
    public static class MedicationRequestBuilder {
        private final MedicationRequest medicationRequest;

        public MedicationRequestBuilder() {
            this.medicationRequest = new MedicationRequest();
            this.medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
            this.medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        }

        public MedicationRequestBuilder withId(String id) {
            medicationRequest.setId(id);
            return this;
        }

        public MedicationRequestBuilder withStatus(MedicationRequest.MedicationRequestStatus status) {
            medicationRequest.setStatus(status);
            return this;
        }

        public MedicationRequestBuilder withIntent(MedicationRequest.MedicationRequestIntent intent) {
            medicationRequest.setIntent(intent);
            return this;
        }

        public MedicationRequestBuilder withSubject(String patientReference) {
            Reference subject = new Reference(patientReference);
            medicationRequest.setSubject(subject);
            return this;
        }

        public MedicationRequestBuilder withMedicationCodeableConcept(String system, String code, String display) {
            CodeableConcept medication = new CodeableConcept();
            Coding coding = new Coding();
            coding.setSystem(system);
            coding.setCode(code);
            coding.setDisplay(display);
            medication.addCoding(coding);
            medicationRequest.setMedication(medication);
            return this;
        }

        public MedicationRequestBuilder withAuthoredOn(Date date) {
            medicationRequest.setAuthoredOn(date);
            return this;
        }

        public MedicationRequestBuilder withDosageInstruction(String text) {
            Dosage dosage = new Dosage();
            dosage.setText(text);
            medicationRequest.addDosageInstruction(dosage);
            return this;
        }

        public MedicationRequest build() {
            return medicationRequest;
        }
    }

    // Static factory methods for builders
    public static PatientBuilder patient() {
        return new PatientBuilder();
    }

    public static ObservationBuilder observation() {
        return new ObservationBuilder();
    }

    public static TaskBuilder task() {
        return new TaskBuilder();
    }

    public static MedicationRequestBuilder medicationRequest() {
        return new MedicationRequestBuilder();
    }
}
