package com.vitalshub.audit;

import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;

/**
 * The kinds of data-access operations VitalsHub audits, mapped to FHIR
 * {@code AuditEvent.action} codes and RESTful-interaction subtypes.
 */
public enum AuditAction {
    CREATE(AuditEventAction.C, "create"),
    READ(AuditEventAction.R, "read"),
    UPDATE(AuditEventAction.U, "update"),
    DELETE(AuditEventAction.D, "delete"),
    SEARCH(AuditEventAction.E, "search-type");

    private final AuditEventAction fhirAction;
    private final String interactionCode;

    AuditAction(AuditEventAction fhirAction, String interactionCode) {
        this.fhirAction = fhirAction;
        this.interactionCode = interactionCode;
    }

    public AuditEventAction fhirAction() {
        return fhirAction;
    }

    public String interactionCode() {
        return interactionCode;
    }
}
