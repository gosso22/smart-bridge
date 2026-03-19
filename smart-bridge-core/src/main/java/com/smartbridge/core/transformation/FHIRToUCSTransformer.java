package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.UCSClientValidator;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Transformation service for converting FHIR R4 Patient resources back to
 * the flat OpenSRP Client format used by UCS.
 */
@Service
public class FHIRToUCSTransformer {

    private static final Logger logger = LoggerFactory.getLogger(FHIRToUCSTransformer.class);

    private static final String OPENSRP_ID_SYSTEM = "http://moh.go.tz/identifier/opensrp-id";
    private static final String NATIONAL_ID_SYSTEM = "http://moh.go.tz/identifier/national-id";
    private static final String BASE_ENTITY_ID_SYSTEM = "http://moh.go.tz/identifier/base-entity-id";
    private static final String TARGET_SYSTEM = "UCS";

    private final UCSClientValidator ucsValidator;

    public FHIRToUCSTransformer(UCSClientValidator ucsValidator) {
        this.ucsValidator = ucsValidator;
    }

    public UCSClient transformFHIRToUCS(FHIRResourceWrapper<? extends Resource> fhirWrapper)
            throws TransformationException {

        if (fhirWrapper == null) {
            throw new TransformationException("FHIR resource wrapper cannot be null");
        }

        Resource resource = fhirWrapper.getResource();
        if (resource == null) {
            throw new TransformationException("FHIR resource cannot be null");
        }

        if (!(resource instanceof Patient)) {
            throw new TransformationException(
                "Unsupported FHIR resource type: " + resource.getResourceType() +
                ". Only Patient resources are supported for FHIR to UCS transformation.",
                "FHIR", TARGET_SYSTEM, "UNSUPPORTED_RESOURCE_TYPE"
            );
        }

        Patient patient = (Patient) resource;
        logger.info("Starting FHIR to UCS transformation for Patient resource: {}", patient.getId());

        try {
            UCSClient ucsClient = new UCSClient();

            // Map identifiers
            mapIdentifiers(patient, ucsClient);

            // Map demographics (flat fields)
            mapDemographics(patient, ucsClient);

            // Map addresses
            mapAddresses(patient, ucsClient);

            // Map metadata fields
            mapMetadata(patient, fhirWrapper, ucsClient);

            // Validate the resulting UCS Client
            UCSClientValidator.ValidationResult validationResult = ucsValidator.validate(ucsClient);
            if (!validationResult.isValid()) {
                throw new TransformationException(
                    "Transformed UCS Client validation failed: " + validationResult.getErrorMessage(),
                    "FHIR", TARGET_SYSTEM, "VALIDATION_FAILED"
                );
            }

            logger.info("Successfully transformed FHIR Patient to UCS Client");
            return ucsClient;

        } catch (TransformationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during FHIR to UCS transformation", e);
            throw new TransformationException(
                "Transformation failed: " + e.getMessage(),
                e, "FHIR", TARGET_SYSTEM, "TRANSFORMATION_ERROR"
            );
        }
    }

    private void mapIdentifiers(Patient patient, UCSClient ucsClient) throws TransformationException {
        if (!patient.hasIdentifier() || patient.getIdentifier().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one identifier",
                "FHIR", TARGET_SYSTEM, "MISSING_IDENTIFIER"
            );
        }

        Map<String, String> identifiers = new HashMap<>();
        String baseEntityId = null;
        String nationalId = null;

        for (Identifier identifier : patient.getIdentifier()) {
            if (identifier.hasSystem() && identifier.hasValue()) {
                String system = identifier.getSystem();
                String value = identifier.getValue();

                if (OPENSRP_ID_SYSTEM.equals(system)) {
                    identifiers.put("opensrp_id", value);
                    logger.debug("Mapped OpenSRP ID: {}", value);
                } else if (NATIONAL_ID_SYSTEM.equals(system)) {
                    nationalId = value;
                    logger.debug("Mapped National ID: {}", value);
                } else if (BASE_ENTITY_ID_SYSTEM.equals(system)) {
                    baseEntityId = value;
                    logger.debug("Mapped baseEntityId: {}", value);
                }
            }
        }

        // opensrp_id or baseEntityId is required
        String opensrpId = identifiers.get("opensrp_id");
        if ((opensrpId == null || opensrpId.isEmpty()) && (baseEntityId == null || baseEntityId.isEmpty())) {
            throw new TransformationException(
                "FHIR Patient must have an identifier with system: " + OPENSRP_ID_SYSTEM +
                " or " + BASE_ENTITY_ID_SYSTEM,
                "FHIR", TARGET_SYSTEM, "MISSING_OPENSRP_ID"
            );
        }

        ucsClient.setIdentifiers(identifiers);
        if (baseEntityId != null) {
            ucsClient.setBaseEntityId(baseEntityId);
        } else if (opensrpId != null) {
            // Use opensrp_id as baseEntityId if no explicit one
            ucsClient.setBaseEntityId(opensrpId);
        }

        // national_id goes into attributes
        if (nationalId != null) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("national_id", nationalId);
            ucsClient.setAttributes(attributes);
        }
    }

    private void mapDemographics(Patient patient, UCSClient ucsClient) throws TransformationException {
        // Map name
        if (!patient.hasName() || patient.getName().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one name",
                "FHIR", TARGET_SYSTEM, "MISSING_NAME"
            );
        }

        HumanName name = patient.getName().get(0);

        String firstName = null;
        if (name.hasGiven() && !name.getGiven().isEmpty()) {
            firstName = name.getGiven().get(0).getValue();
        }
        if (firstName == null || firstName.isEmpty()) {
            throw new TransformationException(
                "FHIR Patient name must have a given name",
                "FHIR", TARGET_SYSTEM, "MISSING_GIVEN_NAME"
            );
        }
        ucsClient.setFirstName(firstName);

        // Map middle name (second given name if present)
        if (name.hasGiven() && name.getGiven().size() > 1) {
            ucsClient.setMiddleName(name.getGiven().get(1).getValue());
        }

        String lastName = null;
        if (name.hasFamily()) {
            lastName = name.getFamily();
        }
        if (lastName == null || lastName.isEmpty()) {
            throw new TransformationException(
                "FHIR Patient name must have a family name",
                "FHIR", TARGET_SYSTEM, "MISSING_FAMILY_NAME"
            );
        }
        ucsClient.setLastName(lastName);

        // Map gender (denormalize to OpenSRP format)
        if (!patient.hasGender()) {
            throw new TransformationException(
                "FHIR Patient must have a gender",
                "FHIR", TARGET_SYSTEM, "MISSING_GENDER"
            );
        }
        String gender = denormalizeGender(patient.getGender());
        ucsClient.setGender(gender);

        // Map birth date
        if (patient.hasBirthDate()) {
            LocalDate birthDate = convertDateToLocalDate(patient.getBirthDate());
            if (birthDate != null) {
                ucsClient.setBirthdate(birthDate.toString());
            }
        }
    }

    private void mapAddresses(Patient patient, UCSClient ucsClient) {
        if (patient.hasAddress() && !patient.getAddress().isEmpty()) {
            List<UCSClient.OpenSRPAddress> addresses = new ArrayList<>();
            for (Address fhirAddress : patient.getAddress()) {
                UCSClient.OpenSRPAddress addr = new UCSClient.OpenSRPAddress();
                if (fhirAddress.hasCountry()) {
                    addr.setCountry(fhirAddress.getCountry());
                }
                if (fhirAddress.hasState()) {
                    addr.setStateProvince(fhirAddress.getState());
                }
                if (fhirAddress.hasDistrict()) {
                    addr.setCountyDistrict(fhirAddress.getDistrict());
                }
                if (fhirAddress.hasCity()) {
                    addr.setCityVillage(fhirAddress.getCity());
                }
                if (fhirAddress.hasText()) {
                    addr.setTown(fhirAddress.getText());
                }
                // Only add if at least one field was set
                if (addr.getCountry() != null || addr.getStateProvince() != null ||
                    addr.getCountyDistrict() != null || addr.getCityVillage() != null ||
                    addr.getTown() != null) {
                    addresses.add(addr);
                }
            }
            if (!addresses.isEmpty()) {
                ucsClient.setAddresses(addresses);
            }
        }
    }

    private void mapMetadata(Patient patient, FHIRResourceWrapper<? extends Resource> fhirWrapper,
                             UCSClient ucsClient) {
        // Set type
        ucsClient.setType("Client");

        // Use FHIR meta.lastUpdated for dateEdited if available
        if (patient.hasMeta() && patient.getMeta().hasLastUpdated()) {
            ucsClient.setDateEdited(patient.getMeta().getLastUpdated().toInstant().toString());
        }

        // Store FHIR ID in identifiers for bidirectional reference
        if (patient.hasId()) {
            if (ucsClient.getAttributes() == null) {
                ucsClient.setAttributes(new HashMap<>());
            }
            ucsClient.getAttributes().put("fhir_id", patient.getId());
        }
    }

    /**
     * Denormalize FHIR gender to OpenSRP format.
     * Returns full word ("Male", "Female") for OpenSRP compatibility.
     */
    private String denormalizeGender(Enumerations.AdministrativeGender fhirGender) {
        if (fhirGender == null) {
            return null;
        }
        switch (fhirGender) {
            case MALE:
                return "Male";
            case FEMALE:
                return "Female";
            case OTHER:
                return "Other";
            case UNKNOWN:
            case NULL:
            default:
                return null;
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
