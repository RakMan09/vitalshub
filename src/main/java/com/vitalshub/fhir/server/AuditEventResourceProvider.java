package com.vitalshub.fhir.server;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.vitalshub.fhir.store.FhirResourceStore;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only FHIR access to the audit trail. Audit records are created only by
 * the store; they are never written through the REST API.
 */
@Component
public class AuditEventResourceProvider implements IResourceProvider {

    private final FhirResourceStore store;

    public AuditEventResourceProvider(FhirResourceStore store) {
        this.store = store;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return AuditEvent.class;
    }

    @Read
    public AuditEvent read(@IdParam IdType id) {
        return store.read("AuditEvent", id.getIdPart())
                .map(r -> (AuditEvent) r)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Search
    public List<AuditEvent> searchAll() {
        return store.search("AuditEvent").stream()
                .filter(AuditEvent.class::isInstance)
                .map(AuditEvent.class::cast)
                .toList();
    }
}
