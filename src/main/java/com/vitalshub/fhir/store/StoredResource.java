package com.vitalshub.fhir.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A persisted FHIR resource. The resource itself is stored as its JSON
 * serialization ({@code jsonBody}) so any FHIR resource type can be stored in a
 * single table without a bespoke schema per type. On PostgreSQL this column can
 * be promoted to {@code jsonb}; {@code text} keeps the schema portable to H2.
 */
@Entity
@Table(
        name = "stored_resource",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resource_type", "resource_id"}),
        indexes = {
                @Index(name = "idx_resource_type", columnList = "resource_type"),
                @Index(name = "idx_patient_id", columnList = "patient_id")
        }
)
public class StoredResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pk;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "version_id", nullable = false)
    private long versionId;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    /** Denormalized patient reference (e.g. {@code Patient/123}) for fast timeline queries. */
    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "json_body", nullable = false, columnDefinition = "text")
    private String jsonBody;

    protected StoredResource() {
        // for JPA
    }

    public StoredResource(String resourceType, String resourceId, long versionId,
                          Instant lastUpdated, String patientId, String jsonBody) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.versionId = versionId;
        this.lastUpdated = lastUpdated;
        this.patientId = patientId;
        this.jsonBody = jsonBody;
    }

    public Long getPk() {
        return pk;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public long getVersionId() {
        return versionId;
    }

    public void setVersionId(long versionId) {
        this.versionId = versionId;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getJsonBody() {
        return jsonBody;
    }

    public void setJsonBody(String jsonBody) {
        this.jsonBody = jsonBody;
    }
}
