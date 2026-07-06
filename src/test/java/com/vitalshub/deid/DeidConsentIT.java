package com.vitalshub.deid;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.vitalshub.adapters.IngestionContext;
import com.vitalshub.adapters.IngestionService;
import com.vitalshub.consent.ConsentService;
import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeidConsentIT {

    @Autowired IngestionService ingestionService;
    @Autowired FhirResourceStore store;
    @Autowired DeidentificationService deidentificationService;
    @Autowired ConsentService consentService;
    @Autowired ShareService shareService;

    private String sample(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/samples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void seed() throws IOException {
        Patient p = new Patient();
        p.setId("share-patient");
        p.addName().setFamily("Secret").addGiven("Patient");
        p.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("555-1234");
        p.addAddress().setCity("Springfield").setState("IL").setPostalCode("62704");
        p.addIdentifier().setSystem("urn:mrn").setValue("MRN-999");
        p.setBirthDateElement(new DateType("1985-03-12"));
        store.create(p);

        IngestionContext ctx = new IngestionContext("share-patient", "seed");
        ingestionService.ingest("scale-csv", sample("scale.csv"), ctx);      // body-composition
        ingestionService.ingest("wearable-json", sample("wearable.json"), ctx); // vitals + activity
    }

    @Test
    void deidentificationRemovesPhiAndGeneralizesDates() {
        Patient original = (Patient) store.read("Patient", "share-patient").orElseThrow();
        Patient deid = deidentificationService.deidentifyPatient(original, "pseudo-1");

        assertThat(deid.getName()).isEmpty();
        assertThat(deid.getTelecom()).isEmpty();
        assertThat(deid.getAddress()).isEmpty();
        assertThat(deid.getIdentifier()).isEmpty();
        assertThat(deid.getBirthDateElement().getPrecision()).isEqualTo(TemporalPrecisionEnum.YEAR);
        assertThat(deid.getMeta().getSecurity())
                .anySatisfy(s -> assertThat(s.getCode()).isEqualTo(DeidentificationService.SECURITY_CODE));

        Observation anyObs = (Observation) store.searchByPatient("Observation", "Patient/share-patient").get(0);
        Observation deidObs = deidentificationService.deidentifyObservation(anyObs, "pseudo-1");
        assertThat(deidObs.getSubject().getReference()).isEqualTo("Patient/pseudo-1");
        assertThat(deidObs.getEffectiveDateTimeType().getPrecision()).isEqualTo(TemporalPrecisionEnum.YEAR);
    }

    @Test
    void shareWithoutConsentIsDenied() {
        assertThatThrownBy(() -> shareService.share("share-patient", "stranger"))
                .isInstanceOf(ConsentService.ConsentDeniedException.class);
    }

    @Test
    void shareHonoursConsentedCategoriesAndDeidentifies() {
        consentService.grant("share-patient", "coach", Set.of("body-composition"), true);

        Bundle bundle = shareService.share("share-patient", "coach");

        List<Observation> observations = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .toList();

        // Only body-composition (weight/bmi/body-fat) observations; no vitals/activity.
        assertThat(observations).isNotEmpty();
        assertThat(observations).allSatisfy(o -> {
            boolean bodyComposition = o.getCode().getCoding().stream()
                    .anyMatch(c -> Set.of("29463-7", "39156-5", "41982-0").contains(c.getCode()));
            assertThat(bodyComposition).as("observation is body-composition").isTrue();
        });
        assertThat(observations).allSatisfy(o ->
                assertThat(o.getSubject().getReference()).doesNotContain("share-patient"));

        Patient shared = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Patient.class::isInstance)
                .map(Patient.class::cast)
                .findFirst().orElseThrow();
        assertThat(shared.getName()).isEmpty();
    }
}
