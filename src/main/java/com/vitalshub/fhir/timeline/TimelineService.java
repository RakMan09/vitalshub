package com.vitalshub.fhir.timeline;

import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Builds a patient's unified timeline across all ingested sources. This is the
 * interoperability payoff: observations that arrived as wearable JSON, scale
 * CSV, HL7 v2, or Apple-Health XML are all queried as one clean FHIR stream.
 */
@Service
public class TimelineService {

    private final FhirResourceStore store;

    public TimelineService(FhirResourceStore store) {
        this.store = store;
    }

    public List<Observation> observations(String patientReference, String code, boolean sortByDate) {
        List<Resource> raw = patientReference == null || patientReference.isBlank()
                ? store.search("Observation")
                : store.searchByPatient("Observation", normalizePatientReference(patientReference));

        var stream = raw.stream()
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast);

        if (code != null && !code.isBlank()) {
            stream = stream.filter(obs -> hasCode(obs, code));
        }

        List<Observation> result = stream.toList();
        if (sortByDate) {
            result = result.stream()
                    .sorted(Comparator.comparing(this::effectiveInstant,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        return result;
    }

    private String normalizePatientReference(String reference) {
        return reference.contains("/") ? reference : "Patient/" + reference;
    }

    private boolean hasCode(Observation obs, String code) {
        if (!obs.hasCode()) {
            return false;
        }
        return obs.getCode().getCoding().stream()
                .anyMatch(c -> code.equals(c.getCode()));
    }

    private Date effectiveInstant(Observation obs) {
        if (obs.hasEffectiveDateTimeType()) {
            return obs.getEffectiveDateTimeType().getValue();
        }
        if (obs.hasEffectivePeriod()) {
            return Optional.ofNullable(obs.getEffectivePeriod().getStart()).orElse(null);
        }
        return null;
    }
}
