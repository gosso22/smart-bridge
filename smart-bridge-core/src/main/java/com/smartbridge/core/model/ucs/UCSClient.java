package com.smartbridge.core.model.ucs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * UCS Client data model aligned with the actual OpenSRP Client structure.
 * Fields are flat (top-level) to match the JSON returned by OpenSRP's
 * {@code /rest/client/getAll} endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UCSClient {

    @JsonProperty("baseEntityId")
    private String baseEntityId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("identifiers")
    private Map<String, String> identifiers;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("middleName")
    private String middleName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("birthdate")
    private String birthdate;

    @JsonProperty("birthdateApprox")
    private Boolean birthdateApprox;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("addresses")
    private List<OpenSRPAddress> addresses;

    @JsonProperty("attributes")
    private Map<String, String> attributes;

    @JsonProperty("relationships")
    private Map<String, List<String>> relationships;

    @JsonProperty("clientType")
    private String clientType;

    @JsonProperty("teamId")
    private String teamId;

    @JsonProperty("locationId")
    private String locationId;

    @JsonProperty("serverVersion")
    private Long serverVersion;

    @JsonProperty("dateCreated")
    private String dateCreated;

    @JsonProperty("dateEdited")
    private String dateEdited;

    @JsonProperty("voided")
    private Boolean voided;

    // Constructors
    public UCSClient() {}

    // Getters and Setters

    public String getBaseEntityId() {
        return baseEntityId;
    }

    public void setBaseEntityId(String baseEntityId) {
        this.baseEntityId = baseEntityId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(Map<String, String> identifiers) {
        this.identifiers = identifiers;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    public Boolean getBirthdateApprox() {
        return birthdateApprox;
    }

    public void setBirthdateApprox(Boolean birthdateApprox) {
        this.birthdateApprox = birthdateApprox;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public List<OpenSRPAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<OpenSRPAddress> addresses) {
        this.addresses = addresses;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, List<String>> getRelationships() {
        return relationships;
    }

    public void setRelationships(Map<String, List<String>> relationships) {
        this.relationships = relationships;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getDateEdited() {
        return dateEdited;
    }

    public void setDateEdited(String dateEdited) {
        this.dateEdited = dateEdited;
    }

    public Boolean getVoided() {
        return voided;
    }

    public void setVoided(Boolean voided) {
        this.voided = voided;
    }

    // --- Convenience accessors for common identifier/attribute lookups ---

    public String getOpensrpId() {
        if (identifiers != null) {
            return identifiers.get("opensrp_id");
        }
        return null;
    }

    public String getNationalId() {
        if (attributes != null && attributes.containsKey("national_id")) {
            return attributes.get("national_id");
        }
        if (identifiers != null && identifiers.containsKey("national_id")) {
            return identifiers.get("national_id");
        }
        return null;
    }

    /**
     * OpenSRP Address model matching the actual JSON structure.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenSRPAddress {

        @JsonProperty("addressType")
        private String addressType;

        @JsonProperty("country")
        private String country;

        @JsonProperty("stateProvince")
        private String stateProvince;

        @JsonProperty("cityVillage")
        private String cityVillage;

        @JsonProperty("countyDistrict")
        private String countyDistrict;

        @JsonProperty("subDistrict")
        private String subDistrict;

        @JsonProperty("town")
        private String town;

        @JsonProperty("subTown")
        private String subTown;

        @JsonProperty("addressFields")
        private Map<String, String> addressFields;

        public OpenSRPAddress() {}

        public String getAddressType() {
            return addressType;
        }

        public void setAddressType(String addressType) {
            this.addressType = addressType;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getStateProvince() {
            return stateProvince;
        }

        public void setStateProvince(String stateProvince) {
            this.stateProvince = stateProvince;
        }

        public String getCityVillage() {
            return cityVillage;
        }

        public void setCityVillage(String cityVillage) {
            this.cityVillage = cityVillage;
        }

        public String getCountyDistrict() {
            return countyDistrict;
        }

        public void setCountyDistrict(String countyDistrict) {
            this.countyDistrict = countyDistrict;
        }

        public String getSubDistrict() {
            return subDistrict;
        }

        public void setSubDistrict(String subDistrict) {
            this.subDistrict = subDistrict;
        }

        public String getTown() {
            return town;
        }

        public void setTown(String town) {
            this.town = town;
        }

        public String getSubTown() {
            return subTown;
        }

        public void setSubTown(String subTown) {
            this.subTown = subTown;
        }

        public Map<String, String> getAddressFields() {
            return addressFields;
        }

        public void setAddressFields(Map<String, String> addressFields) {
            this.addressFields = addressFields;
        }
    }
}
