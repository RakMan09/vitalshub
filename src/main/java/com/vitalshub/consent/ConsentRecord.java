package com.vitalshub.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A consent granted by a patient to a recipient: which data categories may be
 * shared and whether the shared snapshot must be de-identified.
 */
@Entity
@Table(name = "consent_record")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    /** Comma-separated allowed categories, e.g. {@code "vitals,body-composition"}. */
    @Column(name = "categories", nullable = false)
    private String categories;

    @Column(name = "require_deidentified", nullable = false)
    private boolean requireDeidentified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsentRecord() {
    }

    public ConsentRecord(String patientId, String recipient, Set<String> categories, boolean requireDeidentified) {
        this.patientId = patientId;
        this.recipient = recipient;
        this.categories = String.join(",", categories);
        this.requireDeidentified = requireDeidentified;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getRecipient() {
        return recipient;
    }

    public Set<String> getCategories() {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }

    public boolean isRequireDeidentified() {
        return requireDeidentified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean allows(String category) {
        return getCategories().contains(category);
    }
}
