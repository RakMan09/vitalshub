package com.vitalshub.adapters;

import com.vitalshub.fhir.store.FhirResourceStore;
import com.vitalshub.fhir.timeline.TimelineService;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IngestionPipelineIT {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    FhirResourceStore store;

    @Autowired
    TimelineService timelineService;

    private String sample(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/samples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    void seedPatient() {
        Patient patient = new Patient();
        patient.setId("pipeline-patient");
        patient.addName().setFamily("Pipeline");
        store.create(patient);
    }

    @Test
    void wearableIngestionStoresAndMapsToLoinc() throws IOException {
        IngestionContext ctx = new IngestionContext("pipeline-patient", "tester");
        IngestionResult result = ingestionService.ingest("wearable-json", sample("wearable.json"), ctx);

        assertThat(result.getStoredIds()).hasSize(4);
        assertThat(result.getQuarantined()).isEmpty();

        // heart_rate source code was mapped to LOINC 8867-4, so a LOINC search finds it.
        var byLoinc = timelineService.observations("Patient/pipeline-patient", "8867-4", true);
        assertThat(byLoinc).hasSize(2);
        Observation first = byLoinc.get(0);
        assertThat(first.getCode().getCoding())
                .anySatisfy(c -> {
                    assertThat(c.getSystem()).isEqualTo("http://loinc.org");
                    assertThat(c.getCode()).isEqualTo("8867-4");
                });
    }

    @Test
    void hl7IngestionMapsLocalCodeAndFlagsUnmapped() throws IOException {
        IngestionContext ctx = new IngestionContext("pipeline-patient", "tester");
        IngestionResult result = ingestionService.ingest("hl7v2", sample("clinic-oru.hl7"), ctx);

        assertThat(result.getStoredIds()).isNotEmpty();
        // GLU (local) maps to LOINC 2339-0; XYZ (local) has no mapping and is flagged.
        assertThat(result.getUnmappedCodes())
                .anySatisfy(c -> assertThat(c).contains("XYZ"));

        var glucose = timelineService.observations("Patient/pipeline-patient", "2339-0", false);
        assertThat(glucose).hasSize(1);
    }
}
