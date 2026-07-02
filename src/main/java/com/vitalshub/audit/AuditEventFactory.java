package com.vitalshub.audit;

import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventOutcome;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Pure builder for FHIR {@code AuditEvent} resources. Kept free of any
 * persistence so it can be depended on by the data-access chokepoint
 * ({@code FhirResourceStore}) without creating a dependency cycle.
 */
@Component
public class AuditEventFactory {

    private static final String AUDIT_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/audit-event-type";
    private static final String INTERACTION_SYSTEM = "http://hl7.org/fhir/restful-interaction";
    private static final String PURPOSE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActReason";

    public AuditEvent build(AuditContext context,
                            AuditAction action,
                            String resourceType,
                            String resourceId,
                            boolean success) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());

        event.setType(new Coding().setSystem(AUDIT_TYPE_SYSTEM).setCode("rest").setDisplay("RESTful Operation"));
        event.addSubtype(new Coding().setSystem(INTERACTION_SYSTEM).setCode(action.interactionCode()));
        event.setAction(action.fhirAction());
        event.setRecordedElement(new InstantType(new Date()));
        event.setOutcome(success ? AuditEventOutcome._0 : AuditEventOutcome._8);

        AuditEvent.AuditEventAgentComponent agent = event.addAgent();
        agent.setWho(new Reference().setDisplay(context.who()));
        agent.setRequestor(true);
        agent.addPurposeOfUse(new org.hl7.fhir.r4.model.CodeableConcept()
                .addCoding(new Coding().setSystem(PURPOSE_SYSTEM).setCode(context.purpose())));

        AuditEvent.AuditEventEntityComponent entity = event.addEntity();
        entity.setWhat(new Reference(resourceType + "/" + resourceId));

        return event;
    }
}
