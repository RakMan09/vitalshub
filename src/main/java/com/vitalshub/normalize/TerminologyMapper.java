package com.vitalshub.normalize;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Config-driven terminology mapper. Reads {@code terminology/mappings.csv} and
 * enriches each resource's coded elements with the corresponding LOINC/SNOMED
 * codes, while preserving the original source code as provenance.
 *
 * <p>Mapping is lossy: not every source code has a standard equivalent. Unmapped
 * codes are <b>flagged</b> (a {@code meta.tag} is added and the code is reported)
 * rather than silently dropped.
 */
@Component
public class TerminologyMapper {

    public static final String UNMAPPED_FLAG_SYSTEM = "https://vitalshub.example/fhir/flags";
    public static final String UNMAPPED_FLAG_CODE = "unmapped-terminology";

    private static final Set<String> STANDARD_SYSTEMS =
            Set.of("http://loinc.org", "http://snomed.info/sct");

    private final Map<String, List<Coding>> mappings = new LinkedHashMap<>();

    public TerminologyMapper() {
        this("terminology/mappings.csv");
    }

    public TerminologyMapper(String classpathLocation) {
        loadMappings(classpathLocation);
    }

    private void loadMappings(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).build();
        try (InputStream in = resource.getInputStream();
             CSVParser parser = CSVParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8), format)) {
            for (CSVRecord record : parser) {
                String key = key(record.get("sourceSystem"), record.get("sourceCode"));
                Coding target = new Coding(record.get("targetSystem"), record.get("targetCode"), record.get("display"));
                mappings.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load terminology mappings from " + classpathLocation, e);
        }
    }

    public TerminologyResult map(Resource resource) {
        List<String> mapped = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        if (resource instanceof Observation observation && observation.hasCode()) {
            List<Coding> existing = new ArrayList<>(observation.getCode().getCoding());
            for (Coding coding : existing) {
                String system = coding.getSystem();
                String code = coding.getCode();
                if (system == null || code == null) {
                    continue;
                }
                if (STANDARD_SYSTEMS.contains(system)) {
                    mapped.add(system + "|" + code);
                    continue;
                }
                List<Coding> targets = mappings.get(key(system, code));
                if (targets == null || targets.isEmpty()) {
                    unmapped.add(system + "|" + code);
                    flagUnmapped(observation);
                } else {
                    for (Coding target : targets) {
                        if (!hasCoding(observation, target)) {
                            observation.getCode().addCoding(target.copy());
                        }
                    }
                    mapped.add(system + "|" + code);
                }
            }
        }
        return new TerminologyResult(mapped, unmapped);
    }

    public int mappingCount() {
        return mappings.values().stream().mapToInt(List::size).sum();
    }

    private void flagUnmapped(Resource resource) {
        boolean alreadyFlagged = resource.getMeta().getTag().stream()
                .anyMatch(t -> UNMAPPED_FLAG_SYSTEM.equals(t.getSystem())
                        && UNMAPPED_FLAG_CODE.equals(t.getCode()));
        if (!alreadyFlagged) {
            resource.getMeta().addTag(new Coding(UNMAPPED_FLAG_SYSTEM, UNMAPPED_FLAG_CODE, "Unmapped source terminology"));
        }
    }

    private boolean hasCoding(Observation observation, Coding target) {
        return observation.getCode().getCoding().stream()
                .anyMatch(c -> target.getSystem().equals(c.getSystem())
                        && target.getCode().equals(c.getCode()));
    }

    private String key(String system, String code) {
        return system + "|" + code;
    }
}
