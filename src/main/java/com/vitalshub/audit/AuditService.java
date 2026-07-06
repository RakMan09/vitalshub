package com.vitalshub.audit;

import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.r4.model.AuditEvent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query side of the audit trail: answers "who accessed my data?". Reads audit
 * records through the store (reads of {@code AuditEvent} are intentionally not
 * themselves re-audited, to avoid recursion).
 */
@Service
public class AuditService {

    private final FhirResourceStore store;

    public AuditService(FhirResourceStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> accessLogForPatient(String patientId) {
        return store.searchByPatient("AuditEvent", "Patient/" + patientId).stream()
                .filter(AuditEvent.class::isInstance)
                .map(AuditEvent.class::cast)
                .sorted(Comparator.comparing(AuditEvent::getRecorded, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSummary)
                .toList();
    }

    public List<Map<String, Object>> allEvents() {
        return store.search("AuditEvent").stream()
                .filter(AuditEvent.class::isInstance)
                .map(AuditEvent.class::cast)
                .map(this::toSummary)
                .toList();
    }

    private Map<String, Object> toSummary(AuditEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("who", event.getAgentFirstRep().getWho().getDisplay());
        summary.put("action", event.getAction() == null ? null : event.getAction().toCode());
        summary.put("resource", event.getEntityFirstRep().getWhat().getReference());
        summary.put("recorded", event.getRecorded() == null ? null : event.getRecorded().toInstant().toString());
        summary.put("purpose", purpose(event));
        summary.put("outcome", event.getOutcome() == null ? null : event.getOutcome().toCode());
        return summary;
    }

    private String purpose(AuditEvent event) {
        if (event.getAgent().isEmpty() || event.getAgentFirstRep().getPurposeOfUse().isEmpty()) {
            return null;
        }
        return event.getAgentFirstRep().getPurposeOfUse().get(0).getCodingFirstRep().getCode();
    }
}
