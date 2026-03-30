package com.smartbridge.core.model.cht;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CHT place/facility data model aligned with the CouchDB document structure.
 * Represents facilities in the CHT hierarchy: district hospitals, health centers, clinics.
 * Documents have {@code type: "contact"} and a place-type {@code contact_type}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CHTPlace {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    @JsonProperty("type")
    private String type;

    @JsonProperty("contact_type")
    private String contactType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("parent")
    private CHTContact.CHTParentRef parent;

    @JsonProperty("contact")
    private CHTContactRef contact;

    @JsonProperty("reported_date")
    private Long reportedDate;

    @JsonProperty("geolocation")
    private String geolocation;

    public CHTPlace() {}

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRev() {
        return rev;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContactType() {
        return contactType;
    }

    public void setContactType(String contactType) {
        this.contactType = contactType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public CHTContact.CHTParentRef getParent() {
        return parent;
    }

    public void setParent(CHTContact.CHTParentRef parent) {
        this.parent = parent;
    }

    public CHTContactRef getContact() {
        return contact;
    }

    public void setContact(CHTContactRef contact) {
        this.contact = contact;
    }

    public Long getReportedDate() {
        return reportedDate;
    }

    public void setReportedDate(Long reportedDate) {
        this.reportedDate = reportedDate;
    }

    public String getGeolocation() {
        return geolocation;
    }

    public void setGeolocation(String geolocation) {
        this.geolocation = geolocation;
    }

    /**
     * Reference to the primary contact person for this place.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CHTContactRef {

        @JsonProperty("_id")
        private String id;

        @JsonProperty("name")
        private String name;

        public CHTContactRef() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
