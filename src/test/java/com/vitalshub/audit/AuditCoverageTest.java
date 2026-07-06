package com.vitalshub.audit;

import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "100% audit coverage" and "un-bypassable" requirements:
 * <ol>
 *   <li>every data-access operation on the store produces exactly one AuditEvent
 *       (no access path is un-logged), and</li>
 *   <li>the only door to the database ({@code StoredResourceRepository}) is
 *       package-private, so nothing outside the store can bypass auditing.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditCoverageTest {

    @Autowired
    FhirResourceStore store;

    private long countAuditEvents() {
        return store.search("AuditEvent").size();
    }

    private long countFor(String reference, String actionCode) {
        return store.search("AuditEvent").stream()
                .filter(AuditEvent.class::isInstance)
                .map(AuditEvent.class::cast)
                .filter(ae -> ae.getAction() != null && actionCode.equals(ae.getAction().toCode()))
                .filter(ae -> reference.equals(ae.getEntityFirstRep().getWhat().getReference()))
                .count();
    }

    @Test
    void everyDataAccessOperationIsAuditedExactlyOnce() {
        AuditContextHolder.set(AuditContext.of("coverage-tester", "TEST"));
        try {
            long before = countAuditEvents();

            Patient patient = new Patient();
            patient.setId("cov-1");
            patient.addName().setFamily("Coverage");

            store.create(patient);                              // C
            store.read("Patient", "cov-1");                     // R
            store.update(patient);                              // U
            store.searchByPatient("Observation", "Patient/cov-1"); // E (search)
            store.delete("Patient", "cov-1");                   // D

            long after = countAuditEvents();

            // Exactly five new audit events for five data-access operations.
            assertThat(after - before).isEqualTo(5);
            assertThat(countFor("Patient/cov-1", "C")).isEqualTo(1);
            assertThat(countFor("Patient/cov-1", "R")).isEqualTo(1);
            assertThat(countFor("Patient/cov-1", "U")).isEqualTo(1);
            assertThat(countFor("Patient/cov-1", "D")).isEqualTo(1);
        } finally {
            AuditContextHolder.clear();
        }
    }

    @Test
    void auditEventsAreQueryablePerPatient() {
        AuditContextHolder.set(AuditContext.of("nurse", "TREATMENT"));
        try {
            Patient patient = new Patient();
            patient.setId("cov-2");
            store.create(patient);
            store.read("Patient", "cov-2");

            List<?> forPatient = store.searchByPatient("AuditEvent", "Patient/cov-2");
            assertThat(forPatient).hasSizeGreaterThanOrEqualTo(2);
        } finally {
            AuditContextHolder.clear();
        }
    }

    @Test
    void repositoryIsPackagePrivateSoAuditCannotBeBypassed() throws ClassNotFoundException {
        Class<?> repo = Class.forName("com.vitalshub.fhir.store.StoredResourceRepository");
        assertThat(Modifier.isPublic(repo.getModifiers()))
                .as("StoredResourceRepository must not be public - the store is the only data-access path")
                .isFalse();
    }
}
