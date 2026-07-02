package com.vitalshub.adapters;

import org.hl7.fhir.r4.model.Resource;

import java.util.List;

/**
 * Isolates the messiness of one heterogeneous source format. Each adapter parses
 * its quirky input (nested JSON, pipe-delimited HL7 v2, CSV, Apple-Health XML)
 * and emits normalized FHIR resources so the rest of the system only ever sees
 * clean FHIR. Adapters record the <i>source</i> code; the terminology mapper
 * maps it to LOINC/SNOMED downstream.
 */
public interface SourceAdapter {

    /** A stable identifier for this source format, e.g. {@code "wearable-json"}. */
    String sourceType();

    /** Parse raw source content into normalized FHIR resources. */
    List<Resource> toFhir(String rawContent, IngestionContext context);
}
