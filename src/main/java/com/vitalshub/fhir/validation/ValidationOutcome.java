package com.vitalshub.fhir.validation;

import java.util.List;

/**
 * Result of validating a resource against FHIR profiles.
 *
 * @param valid  true when no ERROR/FATAL issues were found
 * @param issues human-readable issue descriptions (severity + location + message)
 */
public record ValidationOutcome(boolean valid, List<String> issues) {

    public String summary() {
        return issues.isEmpty() ? "OK" : String.join("; ", issues);
    }
}
