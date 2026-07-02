package com.vitalshub.adapters;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of ingesting one source payload: which resources were stored, which
 * were quarantined (and why), and which source codes could not be mapped to a
 * standard terminology. These feed the data-quality metrics.
 */
public class IngestionResult {

    public record Quarantined(String resourceType, String resourceId, String reason) {
    }

    private final String sourceType;
    private final List<String> storedIds = new ArrayList<>();
    private final List<Quarantined> quarantined = new ArrayList<>();
    private final List<String> unmappedCodes = new ArrayList<>();

    public IngestionResult(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void addStored(String id) {
        storedIds.add(id);
    }

    public void addQuarantined(String resourceType, String resourceId, String reason) {
        quarantined.add(new Quarantined(resourceType, resourceId, reason));
    }

    public void addUnmappedCode(String code) {
        if (!unmappedCodes.contains(code)) {
            unmappedCodes.add(code);
        }
    }

    public List<String> getStoredIds() {
        return storedIds;
    }

    public List<Quarantined> getQuarantined() {
        return quarantined;
    }

    public List<String> getUnmappedCodes() {
        return unmappedCodes;
    }

    public int totalProcessed() {
        return storedIds.size() + quarantined.size();
    }

    public double validationPassRate() {
        int total = totalProcessed();
        return total == 0 ? 1.0 : (double) storedIds.size() / total;
    }
}
