package com.vitalshub.audit;

/**
 * The "who" and "why" of a data-access operation. Every audited operation
 * carries an {@code AuditContext} so the resulting {@code AuditEvent} answers
 * "who accessed my data, and for what purpose?".
 *
 * @param who     identity of the actor (user id, system name, recipient id)
 * @param purpose purpose of use (e.g. {@code TREATMENT}, {@code INGESTION}, {@code SHARE})
 */
public record AuditContext(String who, String purpose) {

    public static final AuditContext SYSTEM = new AuditContext("system", "OPERATIONS");

    public AuditContext {
        if (who == null || who.isBlank()) {
            who = "unknown";
        }
        if (purpose == null || purpose.isBlank()) {
            purpose = "UNKNOWN";
        }
    }

    public static AuditContext of(String who, String purpose) {
        return new AuditContext(who, purpose);
    }
}
