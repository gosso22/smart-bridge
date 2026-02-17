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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Transformation service for converting FHIR R4 Patient resources back to UCS Client format.
 * Implements reverse mapping with handling of FHIR-specific elements not present in UCS format.
 * Provides data validation and error handling for incompatible data.
 */
@Service
public class FHIRToUCSTransformer {

    private static final Logger logger = LoggerFactory.getLogger(FHIRToUCSTransformer.class);
    
    // FHIR identifier system URIs
    private static final String OPENSRP_ID_SYSTEM = "http://moh.go.tz/identifier/opensrp-id";
    private static final String NATIONAL_ID_SYSTEM = "http://moh.go.tz/identifier/national-id";
    private static final String TARGET_SYSTEM = "UCS";

    private final UCSClientValidator ucsValidator;

    public FHIRToUCSTransformer(UCSClientValidator ucsValidator) {
        this.ucsValidator = ucsValidator;
    }

    /**
     * Transform FHIR R4 Patient resource back to UCS Client format.
     * Performs validation and handles FHIR-specific elements gracefully.
     * 
     * @param fhirWrapper The FHIR resource wrapper containing Patient resource
     * @return UCSClient object with transformed data
     * @throws TransformationException if transformation or validation fails
     */
    public UCSClient transformFHIRToUCS(FHIRResourceWrapper<? extends Resource> fhirWrapper) 
            throws TransformationException {
        
        if (fhirWrapper == null) {
            throw new TransformationException("FHIR resource wrapper cannot be null");
        }

        Resource resource = fhirWrapper.getResource();
        if (resource == null) {
            throw new TransformationException("FHIR resource cannot be null");
        }

        // Only support Patient resources for now
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
            // Create UCS Client object
            UCSClient ucsClient = new UCSClient();

            // Map identifiers
            UCSClient.UCSIdentifiers identifiers = mapIdentifiers(patient);
            ucsClient.setIdentifiers(identifiers);

            // Map demographics
            UCSClient.UCSDemographics demographics = mapDemographics(patient);
            ucsClient.setDemographics(demographics);

            // Initialize clinical data (empty for now)
            UCSClient.UCSClinicalData clinicalData = new UCSClient.UCSClinicalData();
            clinicalData.setObservations(new java.util.ArrayList<>());
            clinicalData.setMedications(new java.util.ArrayList<>());
            clinicalData.setProcedures(new java.util.ArrayList<>());
            ucsClient.setClinicalData(clinicalData);

            // Map metadata
            UCSClient.UCSMetadata metadata = mapMetadata(patient, fhirWrapper);
            ucsClient.setMetadata(metadata);

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
                e, 
                "FHIR", 
                TARGET_SYSTEM, 
                "TRANSFORMATION_ERROR"
            );
        }
    }

    /**
     * Map FHIR Patient identifiers to UCS identifiers.
     * Extracts opensrp_id and national_id from FHIR identifier list.
     */
    private UCSClient.UCSIdentifiers mapIdentifiers(Patient patient) throws TransformationException {
        UCSClient.UCSIdentifiers identifiers = new UCSClient.UCSIdentifiers();

        if (!patient.hasIdentifier() || patient.getIdentifier().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one identifier",
                "FHIR", TARGET_SYSTEM, "MISSING_IDENTIFIER"
            );
        }

        String opensrpId = null;
        String nationalId = null;

        // Extract identifiers based on system URIs
        for (Identifier identifier : patient.getIdentifier()) {
            if (identifier.hasSystem() && identifier.hasValue()) {
                String system = identifier.getSystem();
                String value = identifier.getValue();

                if (OPENSRP_ID_SYSTEM.equals(system)) {
                    opensrpId = value;
                    logger.debug("Mapped OpenSRP ID: {}", value);
                } else if (NATIONAL_ID_SYSTEM.equals(system)) {
                    nationalId = value;
                    logger.debug("Mapped National ID: {}", value);
                }
            }
        }

        // OpenSRP ID is required
        if (opensrpId == null || opensrpId.isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have an identifier with system: " + OPENSRP_ID_SYSTEM,
                "FHIR", TARGET_SYSTEM, "MISSING_OPENSRP_ID"
            );
        }

        identifiers.setOpensrpId(opensrpId);
        identifiers.setNationalId(nationalId); // Can be null

        return identifiers;
    }

    /**
     * Map FHIR Patient demographics to UCS demographics.
     * Handles name, gender denormalization, birth date, and address mapping.
     */
    private UCSClient.UCSDemographics mapDemographics(Patient patient) throws TransformationException {
        UCSClient.UCSDemographics demographics = new UCSClient.UCSDemographics();

        // Map name
        if (!patient.hasName() || patient.getName().isEmpty()) {
            throw new TransformationException(
                "FHIR Patient must have at least one name",
                "FHIR", TARGET_SYSTEM, "MISSING_NAME"
            );
        }

        HumanName name = patient.getName().get(0); // Use first name
        
        // Extract first name (given name)
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
        demographics.setFirstName(firstName);
        logger.debug("Mapped first name: {}", firstName);

        // Extract last name (family name)
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
        demographics.setLastName(lastName);
        logger.debug("Mapped last name: {}", lastName);

        // Map gender with denormalization
        if (!patient.hasGender()) {
            throw new TransformationException(
                "FHIR Patient must have a gender",
                "FHIR", TARGET_SYSTEM, "MISSING_GENDER"
            );
        }
        String gender = denormalizeGender(patient.getGender());
        demographics.setGender(gender);
        logger.debug("Mapped gender: {} -> {}", patient.getGender(), gender);

        // Map birth date (optional)
        if (patient.hasBirthDate()) {
            LocalDate birthDate = convertDateToLocalDate(patient.getBirthDate());
            demographics.setBirthDate(birthDate);
            logger.debug("Mapped birth date: {}", birthDate);
        }

        // Map address (optional)
        if (patient.hasAddress() && !patient.getAddress().isEmpty()) {
            UCSClient.UCSAddress ucsAddress = mapAddress(patient.getAddress().get(0));
            demographics.setAddress(ucsAddress);
        }

        return demographics;
    }

    /**
     * Map FHIR Address to UCS Address.
     * Reverse mapping: district <- Address.district, ward <- Address.city, village <- Address.text
     */
    private UCSClient.UCSAddress mapAddress(Address fhirAddress) {
        UCSClient.UCSAddress ucsAddress = new UCSClient.UCSAddress();

        // Map according to reverse specification:
        // Address.district -> district
        // Address.city -> ward
        // Address.text -> village
        if (fhirAddress.hasDistrict()) {
            ucsAddress.setDistrict(fhirAddress.getDistrict());
            logger.debug("Mapped district: {}", fhirAddress.getDistrict());
        }

        if (fhirAddress.hasCity()) {
            ucsAddress.setWard(fhirAddress.getCity());
            logger.debug("Mapped ward: {}", fhirAddress.getCity());
        }

        if (fhirAddress.hasText()) {
            ucsAddress.setVillage(fhirAddress.getText());
            logger.debug("Mapped village: {}", fhirAddress.getText());
        }

        return ucsAddress;
    }

    /**
     * Denormalize FHIR AdministrativeGender codes to UCS gender codes.
     * male -> M, female -> F, other -> O, unknown -> null
     */
    private String denormalizeGender(Enumerations.AdministrativeGender fhirGender) {
        if (fhirGender == null) {
            return null;
        }

        switch (fhirGender) {
            case MALE:
                return "M";
            case FEMALE:
                return "F";
            case OTHER:
                return "O";
            case UNKNOWN:
                return null;
            case NULL:
                return null;
            default:
                logger.warn("Unknown FHIR gender: {}, defaulting to null", fhirGender);
                return null;
        }
    }

    /**
     * Map FHIR Patient metadata to UCS metadata.
     * Includes FHIR ID for bidirectional reference.
     */
    private UCSClient.UCSMetadata mapMetadata(Patient patient, FHIRResourceWrapper<? extends Resource> fhirWrapper) {
        UCSClient.UCSMetadata metadata = new UCSClient.UCSMetadata();

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        metadata.setUpdatedAt(now);
        
        // Use FHIR meta.lastUpdated if available
        if (patient.hasMeta() && patient.getMeta().hasLastUpdated()) {
            LocalDateTime lastUpdated = convertDateToLocalDateTime(patient.getMeta().getLastUpdated());
            metadata.setUpdatedAt(lastUpdated);
        }

        // Set source system from wrapper or default to FHIR
        String sourceSystem = fhirWrapper.getSourceSystem();
        if (sourceSystem == null || sourceSystem.isEmpty()) {
            sourceSystem = "FHIR";
        }
        metadata.setSource(sourceSystem);

        // Store FHIR ID for bidirectional reference
        if (patient.hasId()) {
            metadata.setFhirId(patient.getId());
            logger.debug("Mapped FHIR ID: {}", patient.getId());
        }

        // If we don't have updatedAt, use createdAt
        if (metadata.getCreatedAt() == null) {
            metadata.setCreatedAt(now);
        }

        return metadata;
    }

    /**
     * Convert java.util.Date to LocalDate.
     * Handles both java.util.Date and java.sql.Date.
     */
    private LocalDate convertDateToLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        // Handle java.sql.Date which doesn't support toInstant()
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
    }

    /**
     * Convert java.util.Date to LocalDateTime.
     * Handles both java.util.Date and java.sql.Date.
     */
    private LocalDateTime convertDateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        // Handle java.sql.Date which doesn't support toInstant()
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate().atStartOfDay();
        }
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
