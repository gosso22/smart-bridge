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

/**
 * Transformation service for converting UCS Client data to FHIR R4 Patient resources.
 * Implements identifier mapping, gender normalization, and demographic field mapping
 * according to Smart Bridge specifications.
 */
@Service
public class UCSToFHIRTransformer implements TransformationService {

    private static final Logger logger = LoggerFactory.getLogger(UCSToFHIRTransformer.class);
    
    // FHIR identifier system URIs
    private static final String OPENSRP_ID_SYSTEM = "http://moh.go.tz/identifier/opensrp-id";
    private static final String NATIONAL_ID_SYSTEM = "http://moh.go.tz/identifier/national-id";
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

    /**
     * Transform UCS Client data to FHIR R4 Patient resource.
     * Performs validation before and after transformation.
     * 
     * @param ucsClient The UCS client data to transform
     * @return FHIRResourceWrapper containing the transformed Patient resource
     * @throws TransformationException if transformation or validation fails
     */
    @Override
    public FHIRResourceWrapper<? extends Resource> transformUCSToFHIR(UCSClient ucsClient) 
            throws TransformationException {
        
        if (ucsClient == null) {
            throw new TransformationException("UCS Client cannot be null");
        }

        logger.info("Starting UCS to FHIR transformation for client");

        // Basic null checks for required fields
        validateRequiredFields(ucsClient);

        try {
            // Build FHIR Patient resource using builder pattern
            FHIRResourceBuilder.PatientBuilder patientBuilder = FHIRResourceBuilder.patient();

            // Map identifiers
            mapIdentifiers(ucsClient, patientBuilder);

            // Map demographics
            mapDemographics(ucsClient, patientBuilder);

            // Build the patient resource
            Patient patient = patientBuilder.build();

            // Validate FHIR resource
            boolean fhirValid = validateFHIRResource(patient);
            if (!fhirValid) {
                FHIRValidator.FHIRValidationResult result = fhirValidator.validate(patient);
                throw new TransformationException("FHIR Patient validation failed: " + result.getErrorMessage());
            }

            // Wrap the resource with metadata
            String originalId = ucsClient.getIdentifiers() != null ? 
                ucsClient.getIdentifiers().getOpensrpId() : null;
            
            FHIRResourceWrapper<Patient> wrapper = FHIRResourceWrapper.forPatient(
                patient, 
                SOURCE_SYSTEM, 
                originalId
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

    /**
     * Validate required fields are present in UCS Client.
     * This is a basic validation for Java objects, separate from JSON schema validation.
     */
    private void validateRequiredFields(UCSClient ucsClient) throws TransformationException {
        if (ucsClient.getIdentifiers() == null) {
            throw new TransformationException("UCS Client identifiers cannot be null");
        }
        if (ucsClient.getIdentifiers().getOpensrpId() == null || 
            ucsClient.getIdentifiers().getOpensrpId().isEmpty()) {
            throw new TransformationException("OpenSRP ID is required");
        }
        if (ucsClient.getDemographics() == null) {
            throw new TransformationException("UCS Client demographics cannot be null");
        }
        if (ucsClient.getDemographics().getFirstName() == null || 
            ucsClient.getDemographics().getFirstName().isEmpty()) {
            throw new TransformationException("First name is required");
        }
        if (ucsClient.getDemographics().getLastName() == null || 
            ucsClient.getDemographics().getLastName().isEmpty()) {
            throw new TransformationException("Last name is required");
        }
        if (ucsClient.getDemographics().getGender() == null || 
            ucsClient.getDemographics().getGender().isEmpty()) {
            throw new TransformationException("Gender is required");
        }
    }

    /**
     * Map UCS identifiers to FHIR Patient identifiers.
     * Maps opensrp_id and national_id to appropriate FHIR identifier systems.
     */
    private void mapIdentifiers(UCSClient ucsClient, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        UCSClient.UCSIdentifiers identifiers = ucsClient.getIdentifiers();

        // Map OpenSRP ID (already validated as required)
        patientBuilder.withIdentifier(OPENSRP_ID_SYSTEM, identifiers.getOpensrpId());
        logger.debug("Mapped OpenSRP ID: {}", identifiers.getOpensrpId());

        // Map National ID (optional)
        if (identifiers.getNationalId() != null && !identifiers.getNationalId().isEmpty()) {
            patientBuilder.withIdentifier(NATIONAL_ID_SYSTEM, identifiers.getNationalId());
            logger.debug("Mapped National ID: {}", identifiers.getNationalId());
        }
    }

    /**
     * Map UCS demographics to FHIR Patient demographic fields.
     * Includes name, gender normalization, birth date, and address mapping.
     */
    private void mapDemographics(UCSClient ucsClient, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        UCSClient.UCSDemographics demographics = ucsClient.getDemographics();

        // Map name (already validated as required)
        patientBuilder.withName(demographics.getFirstName(), demographics.getLastName());
        logger.debug("Mapped name: {} {}", demographics.getFirstName(), demographics.getLastName());

        // Map gender with normalization (M/F/O to FHIR codes)
        String gender = normalizeGender(demographics.getGender());
        patientBuilder.withGender(gender);
        logger.debug("Mapped gender: {} -> {}", demographics.getGender(), gender);

        // Map birth date (optional)
        if (demographics.getBirthDate() != null) {
            patientBuilder.withBirthDate(demographics.getBirthDate());
            logger.debug("Mapped birth date: {}", demographics.getBirthDate());
        }

        // Map address (optional)
        if (demographics.getAddress() != null) {
            mapAddress(demographics.getAddress(), patientBuilder);
        }
    }

    /**
     * Map UCS address to FHIR Address.
     * Maps district, ward, and village to appropriate FHIR address fields.
     */
    private void mapAddress(UCSClient.UCSAddress ucsAddress, FHIRResourceBuilder.PatientBuilder patientBuilder) {
        String district = ucsAddress.getDistrict();
        String ward = ucsAddress.getWard();
        String village = ucsAddress.getVillage();

        // Map according to design specification:
        // district -> Address.district
        // ward -> Address.city
        // village -> Address.text
        patientBuilder.withAddress(district, ward, village);
        
        logger.debug("Mapped address - district: {}, ward: {}, village: {}", 
            district, ward, village);
    }

    /**
     * Normalize UCS gender codes to FHIR AdministrativeGender codes.
     * M -> male, F -> female, O -> other, null/empty -> unknown
     */
    private String normalizeGender(String ucsGender) {
        if (ucsGender == null || ucsGender.isEmpty()) {
            return "unknown";
        }

        switch (ucsGender.toUpperCase()) {
            case "M":
                return "male";
            case "F":
                return "female";
            case "O":
                return "other";
            default:
                logger.warn("Unknown gender code: {}, defaulting to unknown", ucsGender);
                return "unknown";
        }
    }

    /**
     * Transform FHIR R4 resource back to UCS Client format.
     * Delegates to FHIRToUCSTransformer for reverse transformation.
     */
    @Override
    public UCSClient transformFHIRToUCS(FHIRResourceWrapper<? extends Resource> fhirWrapper) 
            throws TransformationException {
        return fhirToUCSTransformer.transformFHIRToUCS(fhirWrapper);
    }

    /**
     * Validate UCS Client data against schema.
     */
    @Override
    public boolean validateUCSClient(UCSClient ucsClient) {
        if (ucsClient == null) {
            return false;
        }
        UCSClientValidator.ValidationResult result = ucsValidator.validate(ucsClient);
        return result.isValid();
    }

    /**
     * Validate FHIR resource against R4 specification.
     */
    @Override
    public boolean validateFHIRResource(Resource resource) {
        if (resource == null) {
            return false;
        }
        FHIRValidator.FHIRValidationResult result = fhirValidator.validate(resource);
        return result.isValid();
    }
}
