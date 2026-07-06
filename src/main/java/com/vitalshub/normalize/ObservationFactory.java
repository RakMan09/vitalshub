package com.vitalshub.normalize;

import com.vitalshub.adapters.IngestionContext;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Builds normalized {@code Observation} resources from source measurements. Ids
 * are derived deterministically from the input so adapter output is stable and
 * comparable against golden files.
 */
public final class ObservationFactory {

    private static final String CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";

    private ObservationFactory() {
    }

    public static Observation quantity(IngestionContext context,
                                       String sourceSystem,
                                       String sourceCode,
                                       String display,
                                       double value,
                                       String unit,
                                       String ucumCode,
                                       Instant effective) {
        Observation obs = new Observation();
        obs.setId(deterministicId(context, sourceCode, effective));
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.addCategory(new CodeableConcept().addCoding(
                new Coding(CATEGORY_SYSTEM, "vital-signs", "Vital Signs")));
        obs.setCode(new CodeableConcept()
                .addCoding(new Coding(sourceSystem, sourceCode, display))
                .setText(display));
        obs.setSubject(new Reference(context.patientReference()));
        obs.setEffective(new DateTimeType(Date.from(effective)));
        obs.setValue(new Quantity()
                .setValue(value)
                .setUnit(unit)
                .setSystem("http://unitsofmeasure.org")
                .setCode(ucumCode));
        return obs;
    }

    private static String deterministicId(IngestionContext context, String sourceCode, Instant effective) {
        return stableId(context.patientId(), sourceCode, String.valueOf(effective.toEpochMilli()));
    }

    /**
     * Produces a deterministic, FHIR-compliant id (<= 64 chars) from the given
     * parts. Deterministic so re-ingesting identical data updates rather than
     * duplicates, and so adapter output is stable for golden-file tests.
     */
    public static String stableId(String... parts) {
        String raw = String.join("|", parts);
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
