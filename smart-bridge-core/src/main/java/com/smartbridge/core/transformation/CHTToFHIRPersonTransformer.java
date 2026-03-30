package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceBuilder;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Transforms CHT Contact (person) documents into FHIR R4 Patient resources.
 * Standalone service — does not implement TransformationService (which is UCS-typed).
 */
@Service
public class CHTToFHIRPersonTransformer {

    private static final Logger logger = LoggerFactory.getLogger(CHTToFHIRPersonTransformer.class);

    static final String CHT_UUID_SYSTEM = "http://moh.go.tz/identifier/cht-uuid";
    static final String CHT_PATIENT_ID_SYSTEM = "http://moh.go.tz/identifier/cht-patient-id";
    static final String SOURCE_SYSTEM = "CHT";

    /**
     * Transform a CHTContact into a FHIR Patient wrapped in FHIRResourceWrapper.
     *
     * @param contact The CHT contact to transform
     * @return FHIRResourceWrapper containing the Patient resource
     * @throws TransformationException if transformation fails
     */
    public FHIRResourceWrapper<Patient> transform(CHTContact contact) throws TransformationException {
        if (contact == null) {
            throw new TransformationException("CHT Contact cannot be null",
                SOURCE_SYSTEM, "FHIR", "NULL_INPUT");
        }

        logger.info("Starting CHT to FHIR Person transformation for contact: {}", contact.getId());

        validateRequiredFields(contact);

        try {
            FHIRResourceBuilder.PatientBuilder builder = FHIRResourceBuilder.patient();

            mapIdentifiers(contact, builder);
            mapName(contact, builder);
            mapGender(contact, builder);
            mapBirthDate(contact, builder);

            Patient patient = builder.build();

            // Map fields that need direct Patient access (not available via builder)
            mapPhone(contact, patient);
            mapParent(contact, patient);

            FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
                patient, SOURCE_SYSTEM, contact.getId()
            );

            logger.info("Successfully transformed CHT Contact to FHIR Patient: {}", contact.getId());
            return wrapper;

        } catch (Exception e) {
            logger.error("Unexpected error during CHT to FHIR Person transformation", e);
            throw new TransformationException("Transformation failed: " + e.getMessage(),
                e, SOURCE_SYSTEM, "FHIR", "TRANSFORMATION_ERROR");
        }
    }

    private void validateRequiredFields(CHTContact contact) throws TransformationException {
        if (contact.getId() == null || contact.getId().isEmpty()) {
            throw new TransformationException("CHT Contact _id is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_ID");
        }
        if (contact.getName() == null || contact.getName().isEmpty()) {
            throw new TransformationException("CHT Contact name is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_NAME");
        }
    }

    private void mapIdentifiers(CHTContact contact, FHIRResourceBuilder.PatientBuilder builder) {
        builder.withIdentifier(CHT_UUID_SYSTEM, contact.getId());
        logger.debug("Mapped CHT UUID: {}", contact.getId());

        if (contact.getPatientId() != null && !contact.getPatientId().isEmpty()) {
            builder.withIdentifier(CHT_PATIENT_ID_SYSTEM, contact.getPatientId());
            logger.debug("Mapped CHT patient_id: {}", contact.getPatientId());
        }
    }

    /**
     * Parse CHT single name field into FHIR given + family.
     * Strategy: split on first space — first token is given, remainder is family.
     * Single-word names use the same value for both.
     */
    private void mapName(CHTContact contact, FHIRResourceBuilder.PatientBuilder builder) {
        String fullName = contact.getName().trim();
        String given;
        String family;

        int spaceIndex = fullName.indexOf(' ');
        if (spaceIndex > 0) {
            given = fullName.substring(0, spaceIndex);
            family = fullName.substring(spaceIndex + 1).trim();
        } else {
            given = fullName;
            family = fullName;
        }

        builder.withName(given, family);
        logger.debug("Mapped name: '{}' -> given='{}', family='{}'", fullName, given, family);
    }

    private void mapGender(CHTContact contact, FHIRResourceBuilder.PatientBuilder builder) {
        String sex = contact.getSex();
        if (sex == null || sex.isEmpty()) {
            builder.withGender("unknown");
            return;
        }
        // CHT stores lowercase "male"/"female" — PatientBuilder normalizes via toUpperCase()
        builder.withGender(sex);
        logger.debug("Mapped gender: {}", sex);
    }

    private void mapBirthDate(CHTContact contact, FHIRResourceBuilder.PatientBuilder builder) {
        if (contact.getDateOfBirth() != null && !contact.getDateOfBirth().isEmpty()) {
            try {
                LocalDate birthDate = LocalDate.parse(contact.getDateOfBirth(), DateTimeFormatter.ISO_LOCAL_DATE);
                builder.withBirthDate(birthDate);
                logger.debug("Mapped birth date: {}", birthDate);
            } catch (DateTimeParseException e) {
                logger.warn("Could not parse CHT date_of_birth: {}", contact.getDateOfBirth(), e);
            }
        }
    }

    private void mapPhone(CHTContact contact, Patient patient) {
        if (contact.getPhone() != null && !contact.getPhone().isEmpty()) {
            ContactPoint telecom = new ContactPoint();
            telecom.setSystem(ContactPoint.ContactPointSystem.PHONE);
            telecom.setValue(contact.getPhone());
            telecom.setUse(ContactPoint.ContactPointUse.MOBILE);
            patient.addTelecom(telecom);
            logger.debug("Mapped phone: {}", contact.getPhone());
        }
    }

    private void mapParent(CHTContact contact, Patient patient) {
        if (contact.getParent() != null && contact.getParent().getId() != null
                && !contact.getParent().getId().isEmpty()) {
            patient.setManagingOrganization(
                new Reference("Organization/" + contact.getParent().getId())
            );
            logger.debug("Mapped parent._id to managingOrganization: {}", contact.getParent().getId());
        }
    }
}
