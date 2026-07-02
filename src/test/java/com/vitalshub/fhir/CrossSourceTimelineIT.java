package com.vitalshub.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.vitalshub.adapters.IngestionContext;
import com.vitalshub.adapters.IngestionService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the interoperability payoff: weight observations that arrived from
 * different sources (smart-scale CSV and Apple-Health XML) surface as one
 * unified, date-sorted FHIR timeline via a single search.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrossSourceTimelineIT {

    @LocalServerPort
    int port;

    @Autowired
    FhirContext fhirContext;

    @Autowired
    IngestionService ingestionService;

    IGenericClient client;

    @BeforeEach
    void setUp() {
        client = fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    }

    private String sample(String name) throws IOException {
        try (var in = getClass().getResourceAsStream("/samples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void weightTimelineUnifiesScaleAndAppleHealth() throws IOException {
        String patientId = client.create()
                .resource(new Patient().addName(new org.hl7.fhir.r4.model.HumanName().setFamily("Unified")))
                .execute().getId().getIdPart();
        IngestionContext ctx = new IngestionContext(patientId, "importer");

        ingestionService.ingest("scale-csv", sample("scale.csv"), ctx);           // 2024-05-01, 2024-05-08
        ingestionService.ingest("apple-health-xml", sample("apple-health.xml"), ctx); // 2024-05-02

        Bundle bundle = client.search().forResource(Observation.class)
                .where(Observation.PATIENT.hasId("Patient/" + patientId))
                .and(Observation.CODE.exactly().systemAndCode("http://loinc.org", "29463-7"))
                .sort(new SortSpec("date"))
                .returnBundle(Bundle.class)
                .execute();

        List<String> dates = bundle.getEntry().stream()
                .map(e -> (Observation) e.getResource())
                .map(o -> o.getEffectiveDateTimeType().getValueAsString().substring(0, 10))
                .toList();

        assertThat(dates).containsExactly("2024-05-01", "2024-05-02", "2024-05-08");
    }
}
