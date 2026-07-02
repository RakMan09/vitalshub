package com.vitalshub.adapters;

/**
 * Context supplied to an adapter for a single ingestion: the VitalsHub patient
 * that the produced resources should be linked to, and the actor performing the
 * ingestion (for audit).
 *
 * @param patientId the logical id of the target {@code Patient} (without the "Patient/" prefix)
 * @param actor     who is performing the ingestion (recorded in the audit trail)
 */
public record IngestionContext(String patientId, String actor) {

    public IngestionContext {
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("patientId is required for ingestion");
        }
        if (actor == null || actor.isBlank()) {
            actor = "ingestion-service";
        }
    }

    public String patientReference() {
        return "Patient/" + patientId;
    }
}
