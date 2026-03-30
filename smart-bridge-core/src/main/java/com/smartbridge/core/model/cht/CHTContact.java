package com.smartbridge.core.model.cht;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * CHT person/contact data model aligned with the CouchDB document structure.
 * Maps to CHT REST API {@code GET /api/v1/person} response.
 * Documents have {@code type: "contact"} and {@code contact_type: "person"}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CHTContact {

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

    @JsonProperty("short_name")
    private String shortName;

    @JsonProperty("date_of_birth")
    private String dateOfBirth;

    @JsonProperty("sex")
    private String sex;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("patient_id")
    private String patientId;

    @JsonProperty("parent")
    private CHTParentRef parent;

    @JsonProperty("reported_date")
    private Long reportedDate;

    @JsonProperty("linked_docs")
    private Map<String, String> linkedDocs;

    public CHTContact() {}

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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public CHTParentRef getParent() {
        return parent;
    }

    public void setParent(CHTParentRef parent) {
        this.parent = parent;
    }

    public Long getReportedDate() {
        return reportedDate;
    }

    public void setReportedDate(Long reportedDate) {
        this.reportedDate = reportedDate;
    }

    public Map<String, String> getLinkedDocs() {
        return linkedDocs;
    }

    public void setLinkedDocs(Map<String, String> linkedDocs) {
        this.linkedDocs = linkedDocs;
    }

    /**
     * Hierarchical parent reference in CHT.
     * Represents the place this contact belongs to (e.g., clinic, health center).
     * Parent references are recursive — each parent may itself have a parent.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CHTParentRef {

        @JsonProperty("_id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("contact_type")
        private String contactType;

        @JsonProperty("parent")
        private CHTParentRef parent;

        public CHTParentRef() {}

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

        public String getContactType() {
            return contactType;
        }

        public void setContactType(String contactType) {
            this.contactType = contactType;
        }

        public CHTParentRef getParent() {
            return parent;
        }

        public void setParent(CHTParentRef parent) {
            this.parent = parent;
        }
    }
}
