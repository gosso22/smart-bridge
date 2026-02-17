package com.smartbridge.core.model.fhir;

import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.MedicationRequest;

import java.time.LocalDateTime;

/**
 * Wrapper class for FHIR R4 resources with additional metadata for Smart Bridge operations.
 * Provides common functionality for all FHIR resource types used in the system.
 */
public class FHIRResourceWrapper<T extends Resource> {

    private T resource;
    private String sourceSystem;
    private LocalDateTime transformedAt;
    private String originalId;
    private FHIRResourceType resourceType;

    public enum FHIRResourceType {
        PATIENT,
        OBSERVATION,
        TASK,
        MEDICATION_REQUEST
    }

    // Constructors
    public FHIRResourceWrapper() {}

    public FHIRResourceWrapper(T resource, String sourceSystem, String originalId) {
        this.resource = resource;
        this.sourceSystem = sourceSystem;
        this.originalId = originalId;
        this.transformedAt = LocalDateTime.now();
        this.resourceType = determineResourceType(resource);
    }

    // Static factory methods for common resource types
    public static FHIRResourceWrapper<Patient> forPatient(Patient patient, String sourceSystem, String originalId) {
        return new FHIRResourceWrapper<>(patient, sourceSystem, originalId);
    }

    public static FHIRResourceWrapper<Observation> forObservation(Observation observation, String sourceSystem, String originalId) {
        return new FHIRResourceWrapper<>(observation, sourceSystem, originalId);
    }

    public static FHIRResourceWrapper<Task> forTask(Task task, String sourceSystem, String originalId) {
        return new FHIRResourceWrapper<>(task, sourceSystem, originalId);
    }

    public static FHIRResourceWrapper<MedicationRequest> forMedicationRequest(MedicationRequest medicationRequest, String sourceSystem, String originalId) {
        return new FHIRResourceWrapper<>(medicationRequest, sourceSystem, originalId);
    }

    // Helper method to determine resource type
    private FHIRResourceType determineResourceType(T resource) {
        if (resource instanceof Patient) {
            return FHIRResourceType.PATIENT;
        } else if (resource instanceof Observation) {
            return FHIRResourceType.OBSERVATION;
        } else if (resource instanceof Task) {
            return FHIRResourceType.TASK;
        } else if (resource instanceof MedicationRequest) {
            return FHIRResourceType.MEDICATION_REQUEST;
        }
        throw new IllegalArgumentException("Unsupported FHIR resource type: " + resource.getClass().getSimpleName());
    }

    // Getters and Setters
    public T getResource() {
        return resource;
    }

    public void setResource(T resource) {
        this.resource = resource;
        this.resourceType = determineResourceType(resource);
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public LocalDateTime getTransformedAt() {
        return transformedAt;
    }

    public void setTransformedAt(LocalDateTime transformedAt) {
        this.transformedAt = transformedAt;
    }

    public String getOriginalId() {
        return originalId;
    }

    public void setOriginalId(String originalId) {
        this.originalId = originalId;
    }

    public FHIRResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(FHIRResourceType resourceType) {
        this.resourceType = resourceType;
    }

    // Utility methods
    public String getResourceId() {
        return resource != null ? resource.getId() : null;
    }

    public boolean isValid() {
        return resource != null && resourceType != null;
    }

    @Override
    public String toString() {
        return "FHIRResourceWrapper{" +
                "resourceType=" + resourceType +
                ", resourceId=" + getResourceId() +
                ", sourceSystem='" + sourceSystem + '\'' +
                ", originalId='" + originalId + '\'' +
                ", transformedAt=" + transformedAt +
                '}';
    }
}