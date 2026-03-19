package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.interfaces.TransformationService;
import com.smartbridge.core.model.fhir.FHIRResourceBuilder;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.validation.FHIRValidator;
import com.smartbridge.core.validation.UCSClientValidator;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Transformation service for converting OpenSRP Client data to FHIR R4 Patient resources.
 * Handles the flat OpenSRP structure with identifier maps, top-level demographics,
 * and address arrays.
 */
@Service
public class UCSToFHIRTransformer implements TransformationService {

    private static final Logger logger = LoggerFactory.getLogger(UCSToFHIRTransformer.class);

    // FHIR identifier system URIs
    private static final String OPENSRP_ID_SYSTEM = "http://moh.go.tz/identifier/opensrp-id";
    private static final String NATIONAL_ID_SYSTEM = "http://moh.go.tz/identifier/national-id";
    private static final String BASE_ENTITY_ID_SYSTEM = "http://moh.go.tz/identifier/base-entity-id";
    private static final String SOURCE_SYSTEM = "UCS";

    private final UCSClientValidator ucsValidator;
    private final FHIRValidator fhirValidator;
    private final FHIRToUCSTransformer fhirToUCSTransformer;

    public UCSToFHIRTransformer(UCSClientValidator ucsValidator, FHIRValidator fhirValidator,
                                FHIRToUCSTransformer fhirToUCSTransformer) {
        this.ucsValidator = ucsValidator;
        this.fhirValidator = fhirValidator;
        this.fhirToUCSTransformer = fhirToUCSTransformer;
    }

    @Override
    public FHIRResourceWrapper<? extends Resource> transformUCSToFHIR(UCSClient ucsClient)
            throws TransformationException {

        if (ucsClient == null) {
            throw new TransformationException("UCS Client cannot be null");
        }

        logger.info("Starting UCS to FHIR transformation for client");

        validateRequiredFields(ucsClient);

        try {
            FHIRResourceBuilder.PatientBuilder patientBuilder = FHIRResourceBuilder.patient();

            mapIdentifiers(ucsClient, patientBuilder);
            mapDemographics(ucsClient, patientBuilder);

            Patient patient = patientBuilder.build();

            boolean fhirValid = validateFHIRResource(patient);
            if (!fhirValid) {
                FHIRValidator.FHIRValidationResult result = fhirValidator.validate(patient);
                throw new TransformationException("FHIR Patient validation failed: " + result.getErrorMessage());
            }

            // Use opensrp_id from identifiers map, or fall back to baseEntityId
            String originalId = ucsClient.getOpensrpId();
            if (originalId == null || originalId.isEmpty()) {
                originalId = ucsClient.getBaseEntityId();
            }

            FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
                patient, SOURCE_SYSTEM, originalId
            );

            logger.info("Successfully transformed UCS Client to FHIR Patient");
            return wrapper;

        } catch (TransformationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during UCS to FHIR transformation", e);
            throw new TransformationException("Transformation failed: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(UCSClient ucsClient) throws TransformationException {
        // Must have either baseEntityId or opensrp_id in identifiers
        String opensrpId = ucsClient.getOpensrpId();
        String baseEntityId = ucsClient.getBaseEntityId();
        if ((opensrpId == null || opensrpId.isEmpty()) && (baseEntityId == null || baseEntityId.isEmpty())) {
            throw new TransformationException("Either opensrp_id in identifiers or baseEntityId is required");
        }
        if (ucsClient.getFirstName() == null || ucsClient.getFirstName().isEmpty()) {
            throw new TransformationException("First name is required");
        }
        if (ucsClient.getLastName() == null || ucsClient.getLastName().isEmpty()) {
            throw new TransformationException("Last name is required");
        }
        if (ucsClient.getGender() == null || ucsClient.getGender().isEmpty()) {
            throw new TransformationException("Gender is required");
        }
    }

    private void mapIdentifiers(UCSClient ucsClient, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        // Map baseEntityId
        if (ucsClient.getBaseEntityId() != null && !ucsClient.getBaseEntityId().isEmpty()) {
            patientBuilder.withIdentifier(BASE_ENTITY_ID_SYSTEM, ucsClient.getBaseEntityId());
            logger.debug("Mapped baseEntityId: {}", ucsClient.getBaseEntityId());
        }

        // Map opensrp_id from identifiers map
        String opensrpId = ucsClient.getOpensrpId();
        if (opensrpId != null && !opensrpId.isEmpty()) {
            patientBuilder.withIdentifier(OPENSRP_ID_SYSTEM, opensrpId);
            logger.debug("Mapped OpenSRP ID: {}", opensrpId);
        }

        // Map national_id (from attributes or identifiers)
        String nationalId = ucsClient.getNationalId();
        if (nationalId != null && !nationalId.isEmpty()) {
            patientBuilder.withIdentifier(NATIONAL_ID_SYSTEM, nationalId);
            logger.debug("Mapped National ID: {}", nationalId);
        }
    }

    private void mapDemographics(UCSClient ucsClient, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        // Map name
        patientBuilder.withName(ucsClient.getFirstName(), ucsClient.getLastName());
        logger.debug("Mapped name: {} {}", ucsClient.getFirstName(), ucsClient.getLastName());

        // Map middle name as additional given name
        if (ucsClient.getMiddleName() != null && !ucsClient.getMiddleName().isEmpty()) {
            patientBuilder.withMiddleName(ucsClient.getMiddleName());
            logger.debug("Mapped middle name: {}", ucsClient.getMiddleName());
        }

        // Map gender with normalization (handles both "Male"/"Female" and "M"/"F")
        String gender = normalizeGender(ucsClient.getGender());
        patientBuilder.withGender(gender);
        logger.debug("Mapped gender: {} -> {}", ucsClient.getGender(), gender);

        // Map birth date (optional — parse ISO datetime string)
        if (ucsClient.getBirthdate() != null && !ucsClient.getBirthdate().isEmpty()) {
            LocalDate birthDate = parseBirthdate(ucsClient.getBirthdate());
            if (birthDate != null) {
                patientBuilder.withBirthDate(birthDate);
                logger.debug("Mapped birth date: {}", birthDate);
            }
        }

        // Map addresses (optional)
        if (ucsClient.getAddresses() != null && !ucsClient.getAddresses().isEmpty()) {
            mapAddress(ucsClient.getAddresses().get(0), patientBuilder);
        }
    }

    private void mapAddress(UCSClient.OpenSRPAddress address, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        patientBuilder.withFullAddress(
            address.getCountry(),
            address.getStateProvince(),
            address.getCountyDistrict(),
            address.getCityVillage(),
            address.getTown()
        );
        logger.debug("Mapped address - country: {}, state: {}, district: {}, city: {}, town: {}",
            address.getCountry(), address.getStateProvince(), address.getCountyDistrict(),
            address.getCityVillage(), address.getTown());
    }

    /**
     * Normalize OpenSRP gender values to FHIR codes.
     * Handles both full words (Male, Female) and single letters (M, F, O).
     */
    private String normalizeGender(String ucsGender) {
        if (ucsGender == null || ucsGender.isEmpty()) {
            return "unknown";
        }
        switch (ucsGender.toUpperCase()) {
            case "M":
            case "MALE":
                return "male";
            case "F":
            case "FEMALE":
                return "female";
            case "O":
            case "OTHER":
                return "other";
            default:
                logger.warn("Unknown gender code: {}, defaulting to unknown", ucsGender);
                return "unknown";
        }
    }

    /**
     * Parse an OpenSRP birthdate string to LocalDate.
     * Handles ISO datetime (e.g. "1990-01-15T00:00:00.000+0300") and plain date ("1990-01-15").
     */
    static LocalDate parseBirthdate(String birthdateStr) {
        if (birthdateStr == null || birthdateStr.isEmpty()) {
            return null;
        }
        try {
            // Try plain date first (YYYY-MM-DD)
            if (birthdateStr.length() == 10) {
                return LocalDate.parse(birthdateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            }
            // ISO datetime — extract date part
            String datePart = birthdateStr.substring(0, 10);
            return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            LoggerFactory.getLogger(UCSToFHIRTransformer.class)
                .warn("Could not parse birthdate: {}", birthdateStr, e);
            return null;
        }
    }

    @Override
    public UCSClient transformFHIRToUCS(FHIRResourceWrapper<? extends Resource> fhirWrapper)
            throws TransformationException {
        return fhirToUCSTransformer.transformFHIRToUCS(fhirWrapper);
    }

    @Override
    public boolean validateUCSClient(UCSClient ucsClient) {
        if (ucsClient == null) {
            return false;
        }
        UCSClientValidator.ValidationResult result = ucsValidator.validate(ucsClient);
        return result.isValid();
    }

    @Override
    public boolean validateFHIRResource(Resource resource) {
        if (resource == null) {
            return false;
        }
        FHIRValidator.FHIRValidationResult result = fhirValidator.validate(resource);
        return result.isValid();
    }
}
