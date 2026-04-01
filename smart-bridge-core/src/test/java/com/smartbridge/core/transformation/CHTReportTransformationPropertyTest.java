package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import net.jqwik.api.*;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CHT Report to FHIR Encounter/Observation transformation.
 * Feature: cht-integration, Property 5: Report Multi-Resource Transformation
 *
 * Validates that for any CHTReport with N clinical fields, the transformer produces
 * exactly 1 Encounter + N Observations with correct references.
 */
class CHTReportTransformationPropertyTest {

    private final CHTToFHIRReportTransformer transformer = new CHTToFHIRReportTransformer();

    // Fields that the transformer treats as metadata and skips
    private static final Set<String> METADATA_PREFIXES = Set.of("meta_", "_");
    private static final Set<String> METADATA_EXACT = Set.of(
        "patient_id", "patient_uuid", "patient_name", "inputs", "source", "source_id"
    );

    /**
     * Property: Every valid report produces exactly 1 Encounter as the first resource.
     */
    @Property(tries = 50)
    void transformAlwaysProducesOneEncounter(@ForAll("validReport") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        assertFalse(result.isEmpty(), "Must produce at least one resource");
        assertTrue(result.get(0).getResource() instanceof Encounter,
            "First resource must be an Encounter");

        long encounterCount = result.stream()
            .filter(w -> w.getResource() instanceof Encounter)
            .count();
        assertEquals(1, encounterCount, "Must produce exactly one Encounter");
    }

    /**
     * Property: Number of Observations equals number of non-null, non-metadata clinical fields.
     */
    @Property(tries = 50)
    void observationCountMatchesClinicalFieldCount(
            @ForAll("reportWithClinicalFields") CHTReport report) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        long expectedClinicalFields = report.getFields().entrySet().stream()
            .filter(e -> e.getValue() != null)
            .filter(e -> !isMetadataField(e.getKey()))
            .count();

        long observationCount = result.stream()
            .filter(w -> w.getResource() instanceof Observation)
            .count();

        assertEquals(expectedClinicalFields, observationCount,
            "Observation count must match clinical field count (excluding metadata)");
    }

    /**
     * Property: Report with empty fields produces only Encounter (no Observations).
     */
    @Property(tries = 50)
    void emptyFieldsProducesEncounterOnly(@ForAll("reportWithEmptyFields") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        assertEquals(1, result.size(), "Empty fields should produce only 1 resource");
        assertTrue(result.get(0).getResource() instanceof Encounter);
    }

    /**
     * Property: All Observations share the same patient subject reference as the Encounter.
     */
    @Property(tries = 50)
    void allObservationsShareEncounterSubject(
            @ForAll("reportWithClinicalFields") CHTReport report) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        Encounter encounter = (Encounter) result.get(0).getResource();
        String encounterSubjectRef = encounter.hasSubject()
            ? encounter.getSubject().getReference() : null;

        result.stream()
            .filter(w -> w.getResource() instanceof Observation)
            .map(w -> (Observation) w.getResource())
            .forEach(obs -> {
                String obsSubjectRef = obs.hasSubject()
                    ? obs.getSubject().getReference() : null;
                assertEquals(encounterSubjectRef, obsSubjectRef,
                    "Observation subject must match Encounter subject");
            });
    }

    /**
     * Property: patient_uuid takes priority over patient_id for the subject reference.
     */
    @Property(tries = 50)
    void patientUuidTakesPriorityOverPatientId(
            @ForAll("reportWithBothPatientRefs") CHTReport report) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);
        Encounter encounter = (Encounter) result.get(0).getResource();

        assertTrue(encounter.hasSubject(), "Encounter must have subject");
        assertEquals("Patient/" + report.getPatientUuid(),
            encounter.getSubject().getReference(),
            "Subject must use patient_uuid when both are present");
    }

    /**
     * Property: Encounter identifier always matches the report _id.
     */
    @Property(tries = 50)
    void encounterIdentifierMatchesReportId(@ForAll("validReport") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);
        Encounter encounter = (Encounter) result.get(0).getResource();

        assertTrue(encounter.hasIdentifier(), "Encounter must have identifier");
        boolean hasMatchingId = encounter.getIdentifier().stream()
            .anyMatch(id ->
                CHTToFHIRReportTransformer.CHT_REPORT_UUID_SYSTEM.equals(id.getSystem())
                && report.getId().equals(id.getValue()));
        assertTrue(hasMatchingId, "Encounter identifier must match report _id");
    }

    /**
     * Property: Encounter type always matches the report form.
     */
    @Property(tries = 50)
    void encounterTypeMatchesReportForm(@ForAll("validReport") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);
        Encounter encounter = (Encounter) result.get(0).getResource();

        assertTrue(encounter.hasType(), "Encounter must have type");
        boolean hasFormType = encounter.getType().stream()
            .flatMap(t -> t.getCoding().stream())
            .anyMatch(c ->
                CHTToFHIRReportTransformer.CHT_FORM_TYPE_SYSTEM.equals(c.getSystem())
                && report.getForm().equals(c.getCode()));
        assertTrue(hasFormType, "Encounter type must include the report form");
    }

    /**
     * Property: Submitting contact maps to Encounter participant.
     */
    @Property(tries = 50)
    void contactMapsToParticipant(@ForAll("reportWithContact") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);
        Encounter encounter = (Encounter) result.get(0).getResource();

        assertTrue(encounter.hasParticipant(), "Encounter must have participant");
        String expectedRef = "Practitioner/" + report.getContact().getId();
        boolean hasParticipant = encounter.getParticipant().stream()
            .anyMatch(p -> expectedRef.equals(p.getIndividual().getReference()));
        assertTrue(hasParticipant,
            "Encounter participant must reference the submitting contact");
    }

    /**
     * Property: Source system is always "CHT" for all produced resources.
     */
    @Property(tries = 50)
    void sourceSystemAlwaysCHT(@ForAll("validReport") CHTReport report) throws TransformationException {
        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        for (FHIRResourceWrapper<? extends Resource> wrapper : result) {
            assertEquals("CHT", wrapper.getSourceSystem(),
                "Source system must be CHT for all resources");
        }
    }

    /**
     * Property: Metadata fields never produce Observations.
     */
    @Property(tries = 50)
    void metadataFieldsNeverBecomeObservations(
            @ForAll("reportWithMetadataFields") CHTReport report) throws TransformationException {

        List<FHIRResourceWrapper<? extends Resource>> result = transformer.transform(report);

        long observationCount = result.stream()
            .filter(w -> w.getResource() instanceof Observation)
            .count();
        assertEquals(0, observationCount,
            "Metadata-only fields must not produce Observations");
    }

    // ==================== Helpers ====================

    private boolean isMetadataField(String fieldName) {
        if (METADATA_EXACT.contains(fieldName)) return true;
        for (String prefix : METADATA_PREFIXES) {
            if (fieldName.startsWith(prefix)) return true;
        }
        return false;
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<CHTReport> validReport() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate(),
            Arbitraries.of(true, false) // has fields
        ).as((id, form, reportedDate, hasFields) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            if (hasFields) {
                report.setFields(randomClinicalFields());
            }
            return report;
        });
    }

    @Provide
    Arbitrary<CHTReport> reportWithClinicalFields() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate(),
            validClinicalFieldCount()
        ).as((id, form, reportedDate, fieldCount) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            report.setPatientUuid("patient-uuid-" + id);
            Map<String, Object> fields = new LinkedHashMap<>();
            String[] clinicalNames = {"weight", "temperature", "heart_rate",
                "height", "blood_pressure_systolic", "notes_field", "custom_obs"};
            for (int i = 0; i < fieldCount && i < clinicalNames.length; i++) {
                fields.put(clinicalNames[i], i < 4 ? (double)(60 + i * 5) : "value-" + i);
            }
            report.setFields(fields);
            return report;
        });
    }

    @Provide
    Arbitrary<CHTReport> reportWithEmptyFields() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate()
        ).as((id, form, reportedDate) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            report.setFields(null);
            return report;
        });
    }

    @Provide
    Arbitrary<CHTReport> reportWithBothPatientRefs() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate(),
            validId(), // patient_uuid
            validId()  // patient_id
        ).as((id, form, reportedDate, patientUuid, patientId) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            report.setPatientUuid(patientUuid);
            report.setPatientId(patientId);
            report.setFields(Map.of("weight", 70.0));
            return report;
        });
    }

    @Provide
    Arbitrary<CHTReport> reportWithContact() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate(),
            validId() // contact id
        ).as((id, form, reportedDate, contactId) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            CHTPlace.CHTContactRef contact = new CHTPlace.CHTContactRef();
            contact.setId(contactId);
            contact.setName("CHW " + contactId);
            report.setContact(contact);
            return report;
        });
    }

    @Provide
    Arbitrary<CHTReport> reportWithMetadataFields() {
        return Combinators.combine(
            validId(),
            validForm(),
            validReportedDate()
        ).as((id, form, reportedDate) -> {
            CHTReport report = buildBaseReport(id, form, reportedDate);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("meta_created", "2024-01-01");
            fields.put("_internal", "skip");
            fields.put("patient_id", "should-skip");
            fields.put("patient_uuid", "should-skip");
            fields.put("patient_name", "should-skip");
            fields.put("inputs", "should-skip");
            fields.put("source", "should-skip");
            fields.put("source_id", "should-skip");
            report.setFields(fields);
            return report;
        });
    }

    // ==================== Helpers ====================

    private CHTReport buildBaseReport(String id, String form, Long reportedDate) {
        CHTReport report = new CHTReport();
        report.setId(id);
        report.setForm(form);
        report.setReportedDate(reportedDate);
        report.setType("data_record");
        return report;
    }

    private Map<String, Object> randomClinicalFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("weight", 65.5);
        fields.put("temperature", 36.8);
        return fields;
    }

    private Arbitrary<String> validId() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(36);
    }

    private Arbitrary<String> validForm() {
        return Arbitraries.of(
            "pregnancy_visit", "immunization", "assessment",
            "postnatal_visit", "referral_followup", "death_report");
    }

    private Arbitrary<Long> validReportedDate() {
        return Arbitraries.longs().between(
            1577836800000L, // 2020-01-01
            1735689600000L  // 2025-01-01
        );
    }

    private Arbitrary<Integer> validClinicalFieldCount() {
        return Arbitraries.integers().between(1, 7);
    }
}