package com.vitalshub.adapters;

import com.vitalshub.normalize.ObservationFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a smart-scale CSV export with columns such as
 * {@code date,weight_kg,body_fat_pct,bmi}. Each numeric column becomes an
 * {@code Observation}; blank cells are skipped.
 */
@Component
public class ScaleCsvAdapter implements SourceAdapter {

    @Override
    public String sourceType() {
        return "scale-csv";
    }

    @Override
    public List<Resource> toFhir(String rawContent, IngestionContext context) {
        List<Resource> resources = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(rawContent), format)) {
            for (CSVRecord record : parser) {
                Instant effective = startOfDay(record.get("date"));
                addIfPresent(resources, context, record, "weight_kg",
                        "weight_kg", "Body weight", "kg", "kg", effective);
                addIfPresent(resources, context, record, "body_fat_pct",
                        "body_fat_pct", "Body fat percentage", "%", "%", effective);
                addIfPresent(resources, context, record, "bmi",
                        "bmi", "Body mass index", "kg/m2", "kg/m2", effective);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid scale CSV: " + e.getMessage(), e);
        }
        return resources;
    }

    private void addIfPresent(List<Resource> resources, IngestionContext context, CSVRecord record,
                              String column, String sourceCode, String display,
                              String unit, String ucum, Instant effective) {
        if (!record.isMapped(column)) {
            return;
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            return;
        }
        resources.add(ObservationFactory.quantity(context, SourceCodeSystems.SCALE,
                sourceCode, display, Double.parseDouble(value), unit, ucum, effective));
    }

    private Instant startOfDay(String isoDate) {
        return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
