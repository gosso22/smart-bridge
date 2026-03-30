package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTReport;
import com.smartbridge.core.model.fhir.FHIRResourceBuilder;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Transforms CHT Report (form submission) documents into FHIR R4 Encounter + Observation resources.
 * Always produces one Encounter. Produces one Observation per clinical field in the report.
 */
@Service
public class CHTToFHIRReportTransformer {

    private static final Logger logger = LoggerFactory.getLogger(CHTToFHIRReportTransformer.class);

    static final String CHT_REPORT_UUID_SYSTEM = "http://moh.go.tz/identifier/cht-report-uuid";
    static final String CHT_FORM_TYPE_SYSTEM = "http://moh.go.tz/identifier/cht-form-type";
    static final String CHT_OBSERVATION_SYSTEM = "http://moh.go.tz/identifier/cht-observation";
    private static final String ENCOUNTER_CLASS_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String SOURCE_SYSTEM = "CHT";

    // Known clinical fields that get typed Observation values
    private static final Map<String, String[]> KNOWN_CLINICAL_FIELDS = Map.of(
        "weight", new String[]{"29463-7", "Body weight", "kg", "http://unitsofmeasure.org", "kg"},
        "temperature", new String[]{"8310-5", "Body temperature", "Cel", "http://unitsofmeasure.org", "Cel"},
        "blood_pressure_systolic", new String[]{"8480-6", "Systolic blood pressure", "mmHg", "http://unitsofmeasure.org", "mm[Hg]"},
        "blood_pressure_diastolic", new String[]{"8462-4", "Diastolic blood pressure", "mmHg", "http://unitsofmeasure.org", "mm[Hg]"},
        "heart_rate", new String[]{"8867-4", "Heart rate", "beats/min", "http://unitsofmeasure.org", "/min"},
        "height", new String[]{"8302-2", "Body height", "cm", "http://unitsofmeasure.org", "cm"}
    );

    /**
     * Transform a CHTReport into a list of FHIR resource wrappers.
     * Returns one Encounter + N Observations (one per clinical field).
     *
     * @param report The CHT report to transform
     * @return List of FHIRResourceWrapper (Encounter first, then Observations)
     * @throws TransformationException if transformation fails
     */
    public List<FHIRResourceWrapper<? extends Resource>> transform(CHTReport report) throws TransformationException {
        if (report == null) {
            throw new TransformationException("CHT Report cannot be null",
                SOURCE_SYSTEM, "FHIR", "NULL_INPUT");
        }

        logger.info("Starting CHT to FHIR Report transformation for report: {}", report.getId());

        validateRequiredFields(report);

        try {
            List<FHIRResourceWrapper<? extends Resource>> wrappers = new ArrayList<>();

            // Build Encounter
            Encounter encounter = buildEncounter(report);
            wrappers.add(FHIRResourceWrapper.forEncounter(encounter, SOURCE_SYSTEM, report.getId()));

            // Build Observations from clinical fields
            if (report.getFields() != null && !report.getFields().isEmpty()) {
                String patientRef = resolvePatientReference(report);
                List<Observation> observations = buildObservations(report, patientRef);
                for (Observation obs : observations) {
                    wrappers.add(FHIRResourceWrapper.forObservation(obs, SOURCE_SYSTEM, report.getId()));
                }
            }

            logger.info("Successfully transformed CHT Report to {} FHIR resource(s): 1 Encounter + {} Observations",
                wrappers.size(), wrappers.size() - 1);
            return wrappers;

        } catch (Exception e) {
            logger.error("Unexpected error during CHT to FHIR Report transformation", e);
            throw new TransformationException("Transformation failed: " + e.getMessage(),
                e, SOURCE_SYSTEM, "FHIR", "TRANSFORMATION_ERROR");
        }
    }

    private void validateRequiredFields(CHTReport report) throws TransformationException {
        if (report.getId() == null || report.getId().isEmpty()) {
            throw new TransformationException("CHT Report _id is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_ID");
        }
        if (report.getForm() == null || report.getForm().isEmpty()) {
            throw new TransformationException("CHT Report form is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_FORM");
        }
        if (report.getReportedDate() == null) {
            throw new TransformationException("CHT Report reported_date is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_REPORTED_DATE");
        }
    }

    private Encounter buildEncounter(CHTReport report) {
        FHIRResourceBuilder.EncounterBuilder builder = FHIRResourceBuilder.encounter()
            .withIdentifier(CHT_REPORT_UUID_SYSTEM, report.getId())
            .withStatus(Encounter.EncounterStatus.FINISHED)
            .withClass(ENCOUNTER_CLASS_SYSTEM, "AMB", "ambulatory")
            .withType(CHT_FORM_TYPE_SYSTEM, report.getForm(), report.getForm())
            .withPeriodStart(new Date(report.getReportedDate()));

        // Map patient reference
        String patientRef = resolvePatientReference(report);
        if (patientRef != null) {
            builder.withSubject(patientRef);
        }

        // Map submitting CHW
        if (report.getContact() != null && report.getContact().getId() != null
                && !report.getContact().getId().isEmpty()) {
            builder.withParticipant("Practitioner/" + report.getContact().getId());
        }

        return builder.build();
    }

    private List<Observation> buildObservations(CHTReport report, String patientRef) {
        List<Observation> observations = new ArrayList<>();

        for (Map.Entry<String, Object> entry : report.getFields().entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();

            if (fieldValue == null) {
                continue;
            }

            // Skip non-clinical metadata fields
            if (isMetadataField(fieldName)) {
                continue;
            }

            Observation obs = buildObservation(report, fieldName, fieldValue, patientRef);
            if (obs != null) {
                observations.add(obs);
            }
        }

        return observations;
    }

    private Observation buildObservation(CHTReport report, String fieldName, Object fieldValue, String patientRef) {
        FHIRResourceBuilder.ObservationBuilder builder = FHIRResourceBuilder.observation()
            .withStatus(Observation.ObservationStatus.FINAL);

        // Set effective date from report
        if (report.getReportedDate() != null) {
            builder.withEffectiveDateTime(new Date(report.getReportedDate()));
        }

        // Set patient reference
        if (patientRef != null) {
            builder.withSubject(patientRef);
        }

        // Check if this is a known clinical field with LOINC code
        String[] clinicalInfo = KNOWN_CLINICAL_FIELDS.get(fieldName);
        if (clinicalInfo != null) {
            builder.withCode("http://loinc.org", clinicalInfo[0], clinicalInfo[1]);

            // Try to parse as numeric quantity
            Double numericValue = parseNumericValue(fieldValue);
            if (numericValue != null) {
                builder.withValueQuantity(numericValue, clinicalInfo[2], clinicalInfo[3], clinicalInfo[4]);
            } else {
                builder.withValueString(fieldValue.toString());
            }
        } else {
            // Unknown field — use CHT observation system and string value
            builder.withCode(CHT_OBSERVATION_SYSTEM, fieldName, fieldName);
            builder.withValueString(fieldValue.toString());
        }

        return builder.build();
    }

    private String resolvePatientReference(CHTReport report) {
        if (report.getPatientUuid() != null && !report.getPatientUuid().isEmpty()) {
            return "Patient/" + report.getPatientUuid();
        }
        if (report.getPatientId() != null && !report.getPatientId().isEmpty()) {
            return "Patient/" + report.getPatientId();
        }
        return null;
    }

    private boolean isMetadataField(String fieldName) {
        // Common non-clinical fields that should not become Observations
        return fieldName.startsWith("meta_") || fieldName.startsWith("_")
            || "patient_id".equals(fieldName) || "patient_uuid".equals(fieldName)
            || "patient_name".equals(fieldName) || "inputs".equals(fieldName)
            || "source".equals(fieldName) || "source_id".equals(fieldName);
    }

    private Double parseNumericValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
