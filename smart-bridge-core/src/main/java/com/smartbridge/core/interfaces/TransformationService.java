package com.smartbridge.core.interfaces;

import com.smartbridge.core.model.ucs.UCSClient;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.Resource;

/**
 * Interface for transformation services that convert between UCS and FHIR formats.
 * Implementations should handle bidirectional transformation with proper validation.
 */
public interface TransformationService {

    /**
     * Transform UCS Client data to FHIR R4 Patient resource.
     * 
     * @param ucsClient The UCS client data to transform
     * @return FHIRResourceWrapper containing the transformed Patient resource
     * @throws TransformationException if transformation fails
     */
    FHIRResourceWrapper<? extends Resource> transformUCSToFHIR(UCSClient ucsClient) throws TransformationException;

    /**
     * Transform FHIR R4 resource back to UCS Client format.
     * 
     * @param fhirWrapper The FHIR resource wrapper to transform
     * @return UCSClient object with transformed data
     * @throws TransformationException if transformation fails
     */
    UCSClient transformFHIRToUCS(FHIRResourceWrapper<? extends Resource> fhirWrapper) throws TransformationException;

    /**
     * Validate UCS Client data against schema.
     * 
     * @param ucsClient The UCS client data to validate
     * @return true if valid, false otherwise
     */
    boolean validateUCSClient(UCSClient ucsClient);

    /**
     * Validate FHIR resource against R4 specification.
     * 
     * @param resource The FHIR resource to validate
     * @return true if valid, false otherwise
     */
    boolean validateFHIRResource(Resource resource);
}