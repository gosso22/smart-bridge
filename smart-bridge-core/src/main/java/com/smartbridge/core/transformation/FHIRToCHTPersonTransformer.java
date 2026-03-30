package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTContact;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Transforms FHIR R4 Patient resources back to CHT Contact (person) format.
 * Used for reverse sync: FHIR → CHT.
 */
@Service
public class FHIRToCHTPersonTransformer {

    private static final Logger logger = LoggerFactory.getLogger(FHIRToCHTPersonTransformer.class);

    static final String CHT_UUID_SYSTEM = "http://moh.go.tz/identifier/cht-uuid";
    static final String CHT_PATIENT_ID_SYSTEM = "http://moh.go.tz/identifier/cht-patient-id";
    private static final String SOURCE_SYSTEM = "FHIR";
    private static final String TARGET_SYSTEM = "CHT";

    /**
     * Transform a FHIR Patient (wrapped) into a CHTContact.
     *
     * @param wrapper The FHIR resource wrapper containing a Patient
     * @return The CHTContact populated from the Patient
     * @throws TransformationException if transformation fails
     */
    public CHTContact transform(FHIRResourceWrapper<? extends Resource> wrapper) throws TransformationException {
        if (wrapper == null) {
            throw new TransformationException("FHIR resource wrapper cannot be null",
                SOURCE_SYSTEM, TARGET_SYSTEM, "NULL_WRAPPER");
        }

        Resource resource = wrapper.getResource();
        if (resource == null) {
            throw new TransformationException("FHIR resource cannot be null",
                SOURCE_SYSTEM, TARGET_SYSTEM, "NULL_RESOURCE");
        }

        if (!(resource instanceof Patient)) {
            throw new TransformationException(
                "Unsupported FHIR resource type: " + resource.getResourceType() +
                ". Only Patient resources are supported for FHIR to CHT transformation.",
                SOURCE_SYSTEM, TARGET_SYSTEM, "UNSUPPORTED_RESOURCE_TYPE");
        }

        Patient patient = (Patient) resource;
        logger.info("Starting FHIR to CHT Person transformation for Patient: {}", patient.getId());

        try {
            CHTContact contact = new CHTContact();

            mapIdentifiers(patient, contact);
            mapName(patient, contact);
            mapGender(patient, contact);
            mapBirthDate(patient, contact);
            mapPhone(patient, contact);
            mapParent(patient, contact);

            // Set CHT-specific type fields
            contact.setType("contact");
            contact.setContactType("person");

            logger.info("Successfully transformed FHIR Patient to CHT Contact");
            return contact;

        } catch (TransformationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during FHIR to CHT Person transformation", e);
            throw new TransformationException("Transformation failed: " + e.getMessage(),
                e, SOURCE_SYSTEM, TARGET_SYSTEM, "TRANSFORMATION_ERROR");
        }
    }

    private void mapIdentifiers(Patient patient, CHTContact contact) throws TransformationException {
        if (!patient.hasIdentifier() || patient.getIdentifier().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one identifier",
                SOURCE_SYSTEM, TARGET_SYSTEM, "MISSING_IDENTIFIER");
        }

        String chtUuid = null;
        String chtPatientId = null;

        for (Identifier identifier : patient.getIdentifier()) {
            if (identifier.hasSystem() && identifier.hasValue()) {
                if (CHT_UUID_SYSTEM.equals(identifier.getSystem())) {
                    chtUuid = identifier.getValue();
                } else if (CHT_PATIENT_ID_SYSTEM.equals(identifier.getSystem())) {
                    chtPatientId = identifier.getValue();
                }
            }
        }

        if (chtUuid == null || chtUuid.isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have a CHT UUID identifier (system: " + CHT_UUID_SYSTEM + ")",
                SOURCE_SYSTEM, TARGET_SYSTEM, "MISSING_CHT_UUID");
        }

        contact.setId(chtUuid);
        if (chtPatientId != null) {
            contact.setPatientId(chtPatientId);
        }

        logger.debug("Mapped identifiers: _id={}, patient_id={}", chtUuid, chtPatientId);
    }

    private void mapName(Patient patient, CHTContact contact) throws TransformationException {
        if (!patient.hasName() || patient.getName().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one name",
                SOURCE_SYSTEM, TARGET_SYSTEM, "MISSING_NAME");
        }

        HumanName name = patient.getName().get(0);
        StringBuilder fullName = new StringBuilder();

        if (name.hasGiven() && !name.getGiven().isEmpty()) {
            fullName.append(name.getGiven().get(0).getValue());
        }

        if (name.hasFamily()) {
            if (fullName.length() > 0) {
                fullName.append(" ");
            }
            fullName.append(name.getFamily());
        }

        if (fullName.length() == 0) {
            throw new TransformationException(
                "FHIR Patient name must have given or family name",
                SOURCE_SYSTEM, TARGET_SYSTEM, "EMPTY_NAME");
        }

        contact.setName(fullName.toString());
        logger.debug("Mapped name: {}", contact.getName());
    }

    private void mapGender(Patient patient, CHTContact contact) {
        if (patient.hasGender()) {
            switch (patient.getGender()) {
                case MALE:
                    contact.setSex("male");
                    break;
                case FEMALE:
                    contact.setSex("female");
                    break;
                default:
                    // CHT only uses male/female; leave null for other/unknown
                    break;
            }
            logger.debug("Mapped gender: {} -> {}", patient.getGender(), contact.getSex());
        }
    }

    private void mapBirthDate(Patient patient, CHTContact contact) {
        if (patient.hasBirthDate()) {
            LocalDate birthDate = convertDateToLocalDate(patient.getBirthDate());
            if (birthDate != null) {
                contact.setDateOfBirth(birthDate.toString());
                logger.debug("Mapped birth date: {}", contact.getDateOfBirth());
            }
        }
    }

    private void mapPhone(Patient patient, CHTContact contact) {
        if (patient.hasTelecom()) {
            for (ContactPoint telecom : patient.getTelecom()) {
                if (telecom.hasSystem() && telecom.getSystem() == ContactPoint.ContactPointSystem.PHONE
                        && telecom.hasValue()) {
                    contact.setPhone(telecom.getValue());
                    logger.debug("Mapped phone: {}", contact.getPhone());
                    break;
                }
            }
        }
    }

    private void mapParent(Patient patient, CHTContact contact) {
        if (patient.hasManagingOrganization()) {
            String ref = patient.getManagingOrganization().getReference();
            if (ref != null) {
                // Extract ID from "Organization/xxx"
                String orgId = ref.startsWith("Organization/") ? ref.substring("Organization/".length()) : ref;
                CHTContact.CHTParentRef parent = new CHTContact.CHTParentRef();
                parent.setId(orgId);
                contact.setParent(parent);
                logger.debug("Mapped managingOrganization to parent._id: {}", orgId);
            }
        }
    }

    private LocalDate convertDateToLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }
}
