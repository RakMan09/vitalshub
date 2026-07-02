package com.vitalshub.adapters;

/**
 * Local code systems used by adapters to record each source's <i>original</i>
 * code before terminology mapping. Keeping the source code as provenance (rather
 * than overwriting it) is what makes the terminology mapping auditable and lets
 * unmapped codes be flagged instead of silently dropped.
 */
public final class SourceCodeSystems {

    public static final String WEARABLE = "https://vitalshub.example/fhir/CodeSystem/wearable";
    public static final String SCALE = "https://vitalshub.example/fhir/CodeSystem/scale";
    public static final String APPLE_HEALTH = "https://vitalshub.example/fhir/CodeSystem/apple-health";
    public static final String HL7V2_LOCAL = "https://vitalshub.example/fhir/CodeSystem/hl7v2-local";

    private SourceCodeSystems() {
    }
}
