package com.smartbridge.core.transformation;

import com.smartbridge.core.interfaces.TransformationException;
import com.smartbridge.core.model.cht.CHTPlace;
import com.smartbridge.core.model.fhir.FHIRResourceBuilder;
import com.smartbridge.core.model.fhir.FHIRResourceWrapper;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transforms CHT Place documents into FHIR R4 Organization + optional Location resources.
 * Returns a list: always an Organization, plus a Location if geolocation is present.
 */
@Service
public class CHTToFHIRPlaceTransformer {

    private static final Logger logger = LoggerFactory.getLogger(CHTToFHIRPlaceTransformer.class);

    static final String CHT_PLACE_UUID_SYSTEM = "http://moh.go.tz/identifier/cht-place-uuid";
    private static final String ORG_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/organization-type";
    private static final String SOURCE_SYSTEM = "CHT";

    // CHT contact_type → FHIR Organization type code mapping
    private static final Map<String, String[]> CONTACT_TYPE_MAP = Map.of(
        "district_hospital", new String[]{"prov", "Healthcare Provider"},
        "health_center", new String[]{"dept", "Hospital Department"},
        "clinic", new String[]{"team", "Organizational team"}
    );

    /**
     * Transform a CHTPlace into a list of FHIR resource wrappers.
     * Always returns at least one (Organization). Returns two if geolocation is present (Organization + Location).
     *
     * @param place The CHT place to transform
     * @return List of FHIRResourceWrapper (Organization, and optionally Location)
     * @throws TransformationException if transformation fails
     */
    public List<FHIRResourceWrapper<? extends Resource>> transform(CHTPlace place) throws TransformationException {
        if (place == null) {
            throw new TransformationException("CHT Place cannot be null",
                SOURCE_SYSTEM, "FHIR", "NULL_INPUT");
        }

        logger.info("Starting CHT to FHIR Place transformation for place: {}", place.getId());

        validateRequiredFields(place);

        try {
            List<FHIRResourceWrapper<? extends Resource>> wrappers = new ArrayList<>();

            // Build Organization
            Organization organization = buildOrganization(place);
            wrappers.add(FHIRResourceWrapper.forOrganization(organization, SOURCE_SYSTEM, place.getId()));

            // Build Location if geolocation is present
            if (place.getGeolocation() != null && !place.getGeolocation().trim().isEmpty()) {
                Location location = buildLocation(place);
                if (location != null) {
                    wrappers.add(FHIRResourceWrapper.forLocation(location, SOURCE_SYSTEM, place.getId()));
                }
            }

            logger.info("Successfully transformed CHT Place to {} FHIR resource(s)", wrappers.size());
            return wrappers;

        } catch (Exception e) {
            logger.error("Unexpected error during CHT to FHIR Place transformation", e);
            throw new TransformationException("Transformation failed: " + e.getMessage(),
                e, SOURCE_SYSTEM, "FHIR", "TRANSFORMATION_ERROR");
        }
    }

    private void validateRequiredFields(CHTPlace place) throws TransformationException {
        if (place.getId() == null || place.getId().isEmpty()) {
            throw new TransformationException("CHT Place _id is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_ID");
        }
        if (place.getName() == null || place.getName().isEmpty()) {
            throw new TransformationException("CHT Place name is required",
                SOURCE_SYSTEM, "FHIR", "MISSING_NAME");
        }
    }

    private Organization buildOrganization(CHTPlace place) {
        FHIRResourceBuilder.OrganizationBuilder builder = FHIRResourceBuilder.organization()
            .withIdentifier(CHT_PLACE_UUID_SYSTEM, place.getId())
            .withName(place.getName());

        // Map contact_type to Organization type
        if (place.getContactType() != null) {
            String[] typeInfo = CONTACT_TYPE_MAP.get(place.getContactType());
            if (typeInfo != null) {
                builder.withType(ORG_TYPE_SYSTEM, typeInfo[0], typeInfo[1]);
            } else {
                // Unknown contact_type — use as-is
                builder.withType(ORG_TYPE_SYSTEM, place.getContactType(), place.getContactType());
            }
        }

        // Map parent hierarchy
        if (place.getParent() != null && place.getParent().getId() != null
                && !place.getParent().getId().isEmpty()) {
            builder.withPartOf("Organization/" + place.getParent().getId());
        }

        // Map primary contact person
        if (place.getContact() != null && place.getContact().getName() != null) {
            builder.withContact(place.getContact().getName(), null);
        }

        return builder.build();
    }

    private Location buildLocation(CHTPlace place) {
        String geo = place.getGeolocation().trim();
        String[] parts = geo.split("\\s+");
        if (parts.length < 2) {
            logger.warn("Cannot parse geolocation '{}' for place {}", geo, place.getId());
            return null;
        }

        try {
            double latitude = Double.parseDouble(parts[0]);
            double longitude = Double.parseDouble(parts[1]);

            return FHIRResourceBuilder.location()
                .withIdentifier(CHT_PLACE_UUID_SYSTEM, place.getId())
                .withName(place.getName())
                .withPosition(latitude, longitude)
                .withManagingOrganization("Organization/" + place.getId())
                .build();
        } catch (NumberFormatException e) {
            logger.warn("Cannot parse geolocation coordinates '{}' for place {}", geo, place.getId());
            return null;
        }
    }
}
