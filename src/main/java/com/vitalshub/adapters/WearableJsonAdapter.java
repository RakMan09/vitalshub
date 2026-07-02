package com.vitalshub.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalshub.normalize.ObservationFactory;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a Fitbit-style wearable JSON export. Wearable exports nest deeply
 * and mix daily summaries ({@code activities-heart}, {@code activities-steps})
 * with high-frequency samples ({@code heartRateSamples}); this adapter flattens
 * them into individual FHIR {@code Observation} resources.
 */
@Component
public class WearableJsonAdapter implements SourceAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sourceType() {
        return "wearable-json";
    }

    @Override
    public List<Resource> toFhir(String rawContent, IngestionContext context) {
        List<Resource> resources = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid wearable JSON: " + e.getMessage(), e);
        }

        for (JsonNode day : root.path("activities-heart")) {
            JsonNode resting = day.path("value").path("restingHeartRate");
            if (!resting.isMissingNode() && resting.isNumber()) {
                resources.add(ObservationFactory.quantity(context,
                        SourceCodeSystems.WEARABLE, "resting_heart_rate", "Resting heart rate",
                        resting.asDouble(), "beats/minute", "/min", startOfDay(day.path("dateTime").asText())));
            }
        }

        for (JsonNode day : root.path("activities-steps")) {
            JsonNode value = day.path("value");
            if (!value.isMissingNode()) {
                resources.add(ObservationFactory.quantity(context,
                        SourceCodeSystems.WEARABLE, "steps", "Step count",
                        value.asDouble(), "steps", "{steps}", startOfDay(day.path("dateTime").asText())));
            }
        }

        for (JsonNode sample : root.path("heartRateSamples")) {
            JsonNode bpm = sample.path("bpm");
            if (!bpm.isMissingNode() && bpm.isNumber()) {
                resources.add(ObservationFactory.quantity(context,
                        SourceCodeSystems.WEARABLE, "heart_rate", "Heart rate",
                        bpm.asDouble(), "beats/minute", "/min", Instant.parse(sample.path("time").asText())));
            }
        }

        return resources;
    }

    private Instant startOfDay(String isoDate) {
        return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
