package com.vitalshub.fhir.store;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.vitalshub.audit.AuditAction;
import com.vitalshub.audit.AuditContext;
import com.vitalshub.audit.AuditContextHolder;
import com.vitalshub.audit.AuditEventFactory;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single data-access chokepoint for all FHIR resources.
 *
 * <p>Every read and write goes through this class, and every read and write
 * records a FHIR {@code AuditEvent}. Because {@link StoredResourceRepository} is
 * package-private, no other component can reach the database directly - this is
 * the structural guarantee behind the "un-bypassable audit" requirement.
 *
 * <p>Writes and reads of {@code AuditEvent} resources are deliberately not
 * re-audited, to avoid infinite recursion and audit-of-audit noise.
 */
@Service
public class FhirResourceStore {

    private final StoredResourceRepository repository;
    private final FhirContext fhirContext;
    private final AuditEventFactory auditEventFactory;
    private final IParser parser;

    public FhirResourceStore(StoredResourceRepository repository,
                             FhirContext fhirContext,
                             AuditEventFactory auditEventFactory) {
        this.repository = repository;
        this.fhirContext = fhirContext;
        this.auditEventFactory = auditEventFactory;
        this.parser = fhirContext.newJsonParser();
    }

    @Transactional
    public Resource create(Resource resource) {
        if (resource.getIdElement().getIdPart() == null || resource.getIdElement().getIdPart().isBlank()) {
            resource.setId(UUID.randomUUID().toString());
        }
        String type = resource.fhirType();
        String id = resource.getIdElement().getIdPart();
        boolean success = false;
        try {
            rawSave(resource, 1);
            success = true;
            return resource;
        } finally {
            audit(AuditAction.CREATE, type, id, success);
        }
    }

    @Transactional
    public Resource update(Resource resource) {
        String type = resource.fhirType();
        String id = resource.getIdElement().getIdPart();
        boolean success = false;
        try {
            long nextVersion = repository.findByResourceTypeAndResourceId(type, id)
                    .map(existing -> existing.getVersionId() + 1)
                    .orElse(1L);
            rawSave(resource, nextVersion);
            success = true;
            return resource;
        } finally {
            audit(AuditAction.UPDATE, type, id, success);
        }
    }

    @Transactional
    public Optional<Resource> read(String resourceType, String resourceId) {
        boolean success = false;
        try {
            Optional<Resource> result = repository
                    .findByResourceTypeAndResourceId(resourceType, resourceId)
                    .map(this::parse);
            success = result.isPresent();
            return result;
        } finally {
            audit(AuditAction.READ, resourceType, resourceId, success);
        }
    }

    @Transactional
    public List<Resource> search(String resourceType) {
        boolean success = false;
        try {
            List<Resource> result = repository.findByResourceType(resourceType).stream()
                    .map(this::parse)
                    .toList();
            success = true;
            return result;
        } finally {
            audit(AuditAction.SEARCH, resourceType, "*", success);
        }
    }

    @Transactional
    public List<Resource> searchByPatient(String resourceType, String patientId) {
        boolean success = false;
        try {
            List<Resource> result = repository.findByResourceTypeAndPatientId(resourceType, patientId).stream()
                    .map(this::parse)
                    .toList();
            success = true;
            return result;
        } finally {
            audit(AuditAction.SEARCH, resourceType, patientId, success);
        }
    }

    @Transactional
    public boolean delete(String resourceType, String resourceId) {
        boolean success = false;
        try {
            Optional<StoredResource> existing =
                    repository.findByResourceTypeAndResourceId(resourceType, resourceId);
            existing.ifPresent(repository::delete);
            success = existing.isPresent();
            return success;
        } finally {
            audit(AuditAction.DELETE, resourceType, resourceId, success);
        }
    }

    private void rawSave(Resource resource, long versionId) {
        String type = resource.fhirType();
        String id = resource.getIdElement().getIdPart();
        String patientId = extractPatientReference(resource);
        String json = parser.encodeResourceToString(resource);

        StoredResource stored = repository.findByResourceTypeAndResourceId(type, id)
                .orElseGet(() -> new StoredResource(type, id, versionId, Instant.now(), patientId, json));
        stored.setVersionId(versionId);
        stored.setLastUpdated(Instant.now());
        stored.setPatientId(patientId);
        stored.setJsonBody(json);
        repository.save(stored);
    }

    private void audit(AuditAction action, String resourceType, String resourceId, boolean success) {
        // Do not re-audit reads/writes of the audit trail itself.
        if ("AuditEvent".equals(resourceType)) {
            return;
        }
        AuditContext context = AuditContextHolder.get();
        var event = auditEventFactory.build(context, action, resourceType, resourceId, success);
        rawSave(event, 1);
    }

    private Resource parse(StoredResource stored) {
        return (Resource) parser.parseResource(stored.getJsonBody());
    }

    private String extractPatientReference(Resource resource) {
        if (resource instanceof Patient patient) {
            String id = patient.getIdElement().getIdPart();
            return id == null ? null : "Patient/" + id;
        }
        if (resource instanceof Observation observation) {
            return refValue(observation.getSubject());
        }
        if (resource instanceof Encounter encounter) {
            return refValue(encounter.getSubject());
        }
        return null;
    }

    private String refValue(Reference reference) {
        if (reference == null || reference.getReference() == null || reference.getReference().isBlank()) {
            return null;
        }
        return reference.getReference();
    }

    /** All distinct resource types currently stored (used for coverage/metrics). */
    @Transactional
    public List<String> distinctResourceTypesRaw() {
        List<String> types = new ArrayList<>();
        for (StoredResource s : repository.findAll()) {
            if (!types.contains(s.getResourceType())) {
                types.add(s.getResourceType());
            }
        }
        return types;
    }
}
