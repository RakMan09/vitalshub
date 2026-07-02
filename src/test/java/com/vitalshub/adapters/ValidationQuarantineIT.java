package com.vitalshub.adapters;

import com.vitalshub.metrics.DataQualityMetrics;
import com.vitalshub.quarantine.QuarantineService;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ValidationQuarantineIT {

    @TestConfiguration
    static class BadAdapterConfig {
        @Bean
        SourceAdapter badAdapter() {
            return new SourceAdapter() {
                @Override
                public String sourceType() {
                    return "bad-source";
                }

                @Override
                public List<Resource> toFhir(String rawContent, IngestionContext context) {
                    // Missing required status and code -> must fail validation.
                    Observation invalid = new Observation();
                    invalid.setId("invalid-1");
                    invalid.setSubject(new Reference(context.patientReference()));
                    return List.of(invalid);
                }
            };
        }
    }

    @Autowired
    IngestionService ingestionService;

    @Autowired
    QuarantineService quarantineService;

    @Autowired
    DataQualityMetrics metrics;

    @Test
    void invalidResourceIsQuarantinedWithReasonNotDropped() {
        long before = quarantineService.count();
        IngestionResult result = ingestionService.ingest(
                "bad-source", "", new IngestionContext("qtest", "tester"));

        assertThat(result.getStoredIds()).isEmpty();
        assertThat(result.getQuarantined()).hasSize(1);
        assertThat(result.getQuarantined().get(0).reason()).isNotBlank();
        assertThat(quarantineService.count()).isEqualTo(before + 1);

        assertThat((double) metrics.snapshot().get("quarantineRate")).isGreaterThan(0.0);
    }
}
