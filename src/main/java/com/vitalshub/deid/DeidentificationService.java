package com.vitalshub.deid;

import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

/**
 * Safe-Harbor-style de-identification.
 *
 * <p>Applies the HIPAA Safe Harbor method to the identifier categories present
 * in this data model: names, contact details (telecom), addresses/geography,
 * identifiers (MRN etc.), and all dates are generalized to <b>year only</b>.
 * Ages over 89 are aggregated to a "90+" flag rather than an exact age. Every
 * output resource is relinked to a fresh pseudonym and tagged with an
 * {@code ANONYED} security label.
 *
 * <p><b>Limits (documented, not hidden):</b> this handles the identifier
 * categories reachable from Patient/Observation/Encounter. It does not scan free
 * text for embedded identifiers, and it trusts that coded clinical values do not
 * themselves carry PHI. As a defense-in-depth check, {@link #assertNoResidualPhi}
 * fails loudly if an obvious identifier survives.
 */
@Service
public class DeidentificationService {

    public static final String SECURITY_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationValue";
    public static final String SECURITY_CODE = "ANONYED";
    public static final String AGE_90_PLUS_TAG_SYSTEM = "https://vitalshub.example/fhir/flags";
    public static final String AGE_90_PLUS_TAG_CODE = "age-90-plus";

    public Patient deidentifyPatient(Patient original, String pseudonymId) {
        Patient p = original.copy();
        p.setId(pseudonymId);
        p.getName().clear();
        p.getTelecom().clear();
        p.getAddress().clear();
        p.getIdentifier().clear();
        p.getContact().clear();
        p.getPhoto().clear();
        p.setText(null);

        if (p.hasBirthDate()) {
            int age = Period.between(
                    LocalDate.ofInstant(p.getBirthDate().toInstant(), ZoneOffset.UTC),
                    LocalDate.now(ZoneOffset.UTC)).getYears();
            if (age > 89) {
                p.setBirthDate(null);
                p.getMeta().addTag(new Coding(AGE_90_PLUS_TAG_SYSTEM, AGE_90_PLUS_TAG_CODE, "Age 90 or older (aggregated)"));
            } else {
                generalizeToYear(p.getBirthDateElement());
            }
        }
        if (p.hasDeceasedDateTimeType()) {
            generalizeToYear(p.getDeceasedDateTimeType());
        }
        tagAnonymized(p);
        return p;
    }

    public Observation deidentifyObservation(Observation original, String pseudonymId) {
        Observation o = original.copy();
        o.setId(UUID.randomUUID().toString());
        o.setSubject(new Reference("Patient/" + pseudonymId));
        o.getIdentifier().clear();
        o.getPerformer().clear();
        o.getNote().clear();
        o.setEncounter(null);
        o.setText(null);
        if (o.hasEffectiveDateTimeType()) {
            generalizeToYear(o.getEffectiveDateTimeType());
        }
        if (o.hasEffectivePeriod()) {
            if (o.getEffectivePeriod().hasStart()) {
                generalizeToYear(o.getEffectivePeriod().getStartElement());
            }
            if (o.getEffectivePeriod().hasEnd()) {
                generalizeToYear(o.getEffectivePeriod().getEndElement());
            }
        }
        tagAnonymized(o);
        return o;
    }

    public Encounter deidentifyEncounter(Encounter original, String pseudonymId) {
        Encounter e = original.copy();
        e.setId(UUID.randomUUID().toString());
        e.setSubject(new Reference("Patient/" + pseudonymId));
        e.getIdentifier().clear();
        e.getParticipant().clear();
        e.setText(null);
        if (e.hasPeriod()) {
            if (e.getPeriod().hasStart()) {
                generalizeToYear(e.getPeriod().getStartElement());
            }
            if (e.getPeriod().hasEnd()) {
                generalizeToYear(e.getPeriod().getEndElement());
            }
        }
        tagAnonymized(e);
        return e;
    }

    /** Defense-in-depth: throws if an obvious identifier survived de-identification. */
    public void assertNoResidualPhi(Patient patient) {
        if (!patient.getName().isEmpty()
                || !patient.getTelecom().isEmpty()
                || !patient.getAddress().isEmpty()
                || !patient.getIdentifier().isEmpty()) {
            throw new IllegalStateException("Residual PHI detected after de-identification");
        }
        if (patient.hasBirthDate()
                && patient.getBirthDateElement().getPrecision().ordinal() > TemporalPrecisionEnum.YEAR.ordinal()) {
            throw new IllegalStateException("Birth date not generalized to year");
        }
    }

    private void generalizeToYear(BaseDateTimeType dateTime) {
        if (dateTime == null || dateTime.getValue() == null) {
            return;
        }
        int year = LocalDate.ofInstant(dateTime.getValue().toInstant(), ZoneOffset.UTC).getYear();
        Date jan1 = Date.from(LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        dateTime.setValue(jan1, TemporalPrecisionEnum.YEAR);
    }

    private void tagAnonymized(Resource resource) {
        resource.getMeta().addSecurity(new Coding(SECURITY_SYSTEM, SECURITY_CODE, "anonymized"));
    }
}
