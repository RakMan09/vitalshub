package com.vitalshub.quarantine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A resource that failed FHIR validation and was quarantined (not stored as a
 * live resource) together with the reason, so data-quality issues are visible
 * and never silently dropped.
 */
@Entity
@Table(name = "quarantine_record")
public class QuarantineRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "json_body", nullable = false, columnDefinition = "text")
    private String jsonBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuarantineRecord() {
    }

    public QuarantineRecord(String sourceType, String resourceType, String resourceId,
                            String reason, String jsonBody) {
        this.sourceType = sourceType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.reason = reason;
        this.jsonBody = jsonBody;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getReason() {
        return reason;
    }

    public String getJsonBody() {
        return jsonBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
