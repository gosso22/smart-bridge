package com.smartbridge.core.model.ucs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * UCS Client data model representing patient/client information in UCS format.
 * This model supports JSON schema validation and bidirectional transformation with FHIR.
 */
public class UCSClient {

    @JsonProperty("identifiers")
    private UCSIdentifiers identifiers;

    @JsonProperty("demographics")
    private UCSDemographics demographics;

    @JsonProperty("clinicalData")
    private UCSClinicalData clinicalData;

    @JsonProperty("metadata")
    private UCSMetadata metadata;

    // Constructors
    public UCSClient() {}

    public UCSClient(UCSIdentifiers identifiers, UCSDemographics demographics, 
                     UCSClinicalData clinicalData, UCSMetadata metadata) {
        this.identifiers = identifiers;
        this.demographics = demographics;
        this.clinicalData = clinicalData;
        this.metadata = metadata;
    }

    // Getters and Setters
    public UCSIdentifiers getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(UCSIdentifiers identifiers) {
        this.identifiers = identifiers;
    }

    public UCSDemographics getDemographics() {
        return demographics;
    }

    public void setDemographics(UCSDemographics demographics) {
        this.demographics = demographics;
    }

    public UCSClinicalData getClinicalData() {
        return clinicalData;
    }

    public void setClinicalData(UCSClinicalData clinicalData) {
        this.clinicalData = clinicalData;
    }

    public UCSMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(UCSMetadata metadata) {
        this.metadata = metadata;
    }

    // Nested classes for UCS data structure
    public static class UCSIdentifiers {
        @JsonProperty("opensrp_id")
        private String opensrpId;

        @JsonProperty("national_id")
        private String nationalId;

        public UCSIdentifiers() {}

        public UCSIdentifiers(String opensrpId, String nationalId) {
            this.opensrpId = opensrpId;
            this.nationalId = nationalId;
        }

        public String getOpensrpId() {
            return opensrpId;
        }

        public void setOpensrpId(String opensrpId) {
            this.opensrpId = opensrpId;
        }

        public String getNationalId() {
            return nationalId;
        }

        public void setNationalId(String nationalId) {
            this.nationalId = nationalId;
        }
    }

    public static class UCSDemographics {
        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("lastName")
        private String lastName;

        @JsonProperty("gender")
        private String gender; // M, F, O

        @JsonProperty("birthDate")
        @JsonSerialize(using = LocalDateSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        private LocalDate birthDate;

        @JsonProperty("address")
        private UCSAddress address;

        public UCSDemographics() {}

        public UCSDemographics(String firstName, String lastName, String gender, 
                              LocalDate birthDate, UCSAddress address) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.gender = gender;
            this.birthDate = birthDate;
            this.address = address;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public LocalDate getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
        }

        public UCSAddress getAddress() {
            return address;
        }

        public void setAddress(UCSAddress address) {
            this.address = address;
        }
    }

    public static class UCSAddress {
        @JsonProperty("district")
        private String district;

        @JsonProperty("ward")
        private String ward;

        @JsonProperty("village")
        private String village;

        public UCSAddress() {}

        public UCSAddress(String district, String ward, String village) {
            this.district = district;
            this.ward = ward;
            this.village = village;
        }

        public String getDistrict() {
            return district;
        }

        public void setDistrict(String district) {
            this.district = district;
        }

        public String getWard() {
            return ward;
        }

        public void setWard(String ward) {
            this.ward = ward;
        }

        public String getVillage() {
            return village;
        }

        public void setVillage(String village) {
            this.village = village;
        }
    }

    public static class UCSClinicalData {
        @JsonProperty("observations")
        private List<Object> observations;

        @JsonProperty("medications")
        private List<Object> medications;

        @JsonProperty("procedures")
        private List<Object> procedures;

        public UCSClinicalData() {}

        public UCSClinicalData(List<Object> observations, List<Object> medications, List<Object> procedures) {
            this.observations = observations;
            this.medications = medications;
            this.procedures = procedures;
        }

        public List<Object> getObservations() {
            return observations;
        }

        public void setObservations(List<Object> observations) {
            this.observations = observations;
        }

        public List<Object> getMedications() {
            return medications;
        }

        public void setMedications(List<Object> medications) {
            this.medications = medications;
        }

        public List<Object> getProcedures() {
            return procedures;
        }

        public void setProcedures(List<Object> procedures) {
            this.procedures = procedures;
        }
    }

    public static class UCSMetadata {
        @JsonProperty("createdAt")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime createdAt;

        @JsonProperty("updatedAt")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime updatedAt;

        @JsonProperty("source")
        private String source;

        @JsonProperty("fhir_id")
        private String fhirId; // For reverse mapping from FHIR

        public UCSMetadata() {}

        public UCSMetadata(LocalDateTime createdAt, LocalDateTime updatedAt, String source, String fhirId) {
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.source = source;
            this.fhirId = fhirId;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getFhirId() {
            return fhirId;
        }

        public void setFhirId(String fhirId) {
            this.fhirId = fhirId;
        }
    }
}