package com.vitalshub.consent;

import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Maps an {@code Observation} to a coarse sharing category (by LOINC code) so
 * consent can be expressed as "this recipient may see these categories".
 */
@Component
public class CategoryResolver {

    public static final String OTHER = "other";

    private static final Map<String, String> LOINC_TO_CATEGORY = Map.of(
            "29463-7", "body-composition",
            "39156-5", "body-composition",
            "41982-0", "body-composition",
            "8867-4", "vitals",
            "40443-4", "vitals",
            "41950-7", "activity",
            "2339-0", "labs");

    public String categoryOf(Observation observation) {
        if (!observation.hasCode()) {
            return OTHER;
        }
        return observation.getCode().getCoding().stream()
                .map(c -> LOINC_TO_CATEGORY.get(c.getCode()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(OTHER);
    }

    public Set<String> knownCategories() {
        return Set.copyOf(LOINC_TO_CATEGORY.values());
    }
}
