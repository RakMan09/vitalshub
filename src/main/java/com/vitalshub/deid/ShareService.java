package com.vitalshub.deid;

import com.vitalshub.audit.AuditContext;
import com.vitalshub.audit.AuditContextHolder;
import com.vitalshub.consent.CategoryResolver;
import com.vitalshub.consent.ConsentRecord;
import com.vitalshub.consent.ConsentService;
import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Produces a consent-gated, optionally de-identified snapshot to share with a
 * recipient (doctor/coach). Enforces that (a) a consent exists for the recipient,
 * (b) only allowed categories are disclosed, and (c) de-identification is applied
 * when the consent requires it. Every access is performed under the recipient's
 * audit identity so the trail shows who saw what.
 */
@Service
public class ShareService {

    private final ConsentService consentService;
    private final DeidentificationService deidentificationService;
    private final CategoryResolver categoryResolver;
    private final FhirResourceStore store;

    public ShareService(ConsentService consentService,
                        DeidentificationService deidentificationService,
                        CategoryResolver categoryResolver,
                        FhirResourceStore store) {
        this.consentService = consentService;
        this.deidentificationService = deidentificationService;
        this.categoryResolver = categoryResolver;
        this.store = store;
    }

    public Bundle share(String patientId, String recipient) {
        ConsentRecord consent = consentService.require(patientId, recipient);

        AuditContext previous = AuditContextHolder.get();
        AuditContextHolder.set(AuditContext.of(recipient, "SHARE"));
        try {
            Patient patient = store.read("Patient", patientId)
                    .map(Patient.class::cast)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown patient: " + patientId));

            List<Observation> allowed = store.searchByPatient("Observation", "Patient/" + patientId).stream()
                    .filter(Observation.class::isInstance)
                    .map(Observation.class::cast)
                    .filter(o -> consent.allows(categoryResolver.categoryOf(o)))
                    .toList();

            String pseudonym = UUID.randomUUID().toString();
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.COLLECTION);

            if (consent.isRequireDeidentified()) {
                Patient deid = deidentificationService.deidentifyPatient(patient, pseudonym);
                deidentificationService.assertNoResidualPhi(deid);
                addEntry(bundle, deid);
                allowed.forEach(o -> addEntry(bundle, deidentificationService.deidentifyObservation(o, pseudonym)));
            } else {
                addEntry(bundle, patient);
                allowed.forEach(o -> addEntry(bundle, o));
            }
            return bundle;
        } finally {
            AuditContextHolder.set(previous);
        }
    }

    private void addEntry(Bundle bundle, Resource resource) {
        bundle.addEntry().setResource(resource);
    }
}
