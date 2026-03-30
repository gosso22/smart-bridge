package com.smartbridge.core.model.cht;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * CHT report/form submission data model aligned with the CouchDB document structure.
 * Represents form submissions (e.g., pregnancy visits, immunizations) created by CHWs.
 * Documents have {@code type: "data_record"}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CHTReport {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    @JsonProperty("type")
    private String type;

    @JsonProperty("form")
    private String form;

    @JsonProperty("fields")
    private Map<String, Object> fields;

    @JsonProperty("contact")
    private CHTPlace.CHTContactRef contact;

    @JsonProperty("patient_id")
    private String patientId;

    @JsonProperty("patient_uuid")
    private String patientUuid;

    @JsonProperty("reported_date")
    private Long reportedDate;

    @JsonProperty("geolocation")
    private String geolocation;

    @JsonProperty("errors")
    private List<Object> errors;

    public CHTReport() {}

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

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public CHTPlace.CHTContactRef getContact() {
        return contact;
    }

    public void setContact(CHTPlace.CHTContactRef contact) {
        this.contact = contact;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientUuid() {
        return patientUuid;
    }

    public void setPatientUuid(String patientUuid) {
        this.patientUuid = patientUuid;
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

    public List<Object> getErrors() {
        return errors;
    }

    public void setErrors(List<Object> errors) {
        this.errors = errors;
    }
}
