package com.vitalshub.normalize;

import java.util.List;

/**
 * Result of terminology mapping for a single resource.
 *
 * @param mappedCodes   source codes that were mapped to a standard terminology
 * @param unmappedCodes source codes with no known mapping (flagged, not dropped)
 */
public record TerminologyResult(List<String> mappedCodes, List<String> unmappedCodes) {

    public boolean hasUnmapped() {
        return !unmappedCodes.isEmpty();
    }
}
