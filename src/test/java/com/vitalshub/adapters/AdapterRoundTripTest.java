package com.vitalshub.adapters;

import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdapterRoundTripTest {

    private static final IngestionContext CTX = new IngestionContext("p1", "tester");

    private String sample(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/samples/" + name)) {
            assertThat(in).as("sample %s exists", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Optional<Observation> observationWithSourceCode(List<Resource> resources, String code) {
        return resources.stream()
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .filter(o -> o.getCode().getCoding().stream().anyMatch(c -> code.equals(c.getCode())))
                .findFirst();
    }

    @Test
    void wearableJsonProducesHeartRateAndSteps() throws IOException {
        List<Resource> resources = new WearableJsonAdapter().toFhir(sample("wearable.json"), CTX);

        assertThat(resources).hasSize(4);
        Observation resting = observationWithSourceCode(resources, "resting_heart_rate").orElseThrow();
        assertThat(resting.getValueQuantity().getValue().doubleValue()).isEqualTo(62.0);
        assertThat(resting.getSubject().getReference()).isEqualTo("Patient/p1");
        assertThat(resting.getCode().getCodingFirstRep().getSystem()).isEqualTo(SourceCodeSystems.WEARABLE);

        Observation steps = observationWithSourceCode(resources, "steps").orElseThrow();
        assertThat(steps.getValueQuantity().getValue().doubleValue()).isEqualTo(8432.0);

        long hrSamples = resources.stream()
                .filter(Observation.class::isInstance).map(Observation.class::cast)
                .filter(o -> o.getCode().getCoding().stream().anyMatch(c -> "heart_rate".equals(c.getCode())))
                .count();
        assertThat(hrSamples).isEqualTo(2);
    }

    @Test
    void scaleCsvProducesWeightBodyFatAndBmiPerRow() throws IOException {
        List<Resource> resources = new ScaleCsvAdapter().toFhir(sample("scale.csv"), CTX);

        // 2 rows x 3 metrics
        assertThat(resources).hasSize(6);
        Observation weight = observationWithSourceCode(resources, "weight_kg").orElseThrow();
        assertThat(weight.getValueQuantity().getValue().doubleValue()).isEqualTo(82.5);
        assertThat(weight.getValueQuantity().getCode()).isEqualTo("kg");
        assertThat(weight.getSubject().getReference()).isEqualTo("Patient/p1");
    }

    @Test
    void appleHealthXmlProducesObservations() throws IOException {
        List<Resource> resources = new AppleHealthXmlAdapter().toFhir(sample("apple-health.xml"), CTX);

        assertThat(resources).hasSize(3);
        Observation mass = observationWithSourceCode(resources, "HKQuantityTypeIdentifierBodyMass").orElseThrow();
        assertThat(mass.getValueQuantity().getValue().doubleValue()).isEqualTo(82.1);
        assertThat(mass.getCode().getText()).isEqualTo("BodyMass");
    }

    @Test
    void hl7v2ProducesEncounterAndObservations() throws IOException {
        List<Resource> resources = new Hl7v2Adapter().toFhir(sample("clinic-oru.hl7"), CTX);

        Encounter encounter = resources.stream()
                .filter(Encounter.class::isInstance).map(Encounter.class::cast)
                .findFirst().orElseThrow();
        assertThat(encounter.getClass_().getCode()).isEqualTo("AMB");
        assertThat(encounter.getSubject().getReference()).isEqualTo("Patient/p1");

        // Glucose came in as a local code "GLU"; heart rate as LOINC directly.
        Observation glucose = observationWithSourceCode(resources, "GLU").orElseThrow();
        assertThat(glucose.getValueQuantity().getValue().doubleValue()).isEqualTo(95.0);
        assertThat(glucose.getCode().getCodingFirstRep().getSystem()).isEqualTo(SourceCodeSystems.HL7V2_LOCAL);

        Observation hr = observationWithSourceCode(resources, "8867-4").orElseThrow();
        assertThat(hr.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
        assertThat(hr.getValueQuantity().getValue().doubleValue()).isEqualTo(72.0);
    }
}
