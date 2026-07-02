package com.vitalshub.fhir.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA access to {@link StoredResource}.
 *
 * <p>This interface is intentionally <b>package-private</b>: it can only be
 * injected by classes in the {@code com.vitalshub.fhir.store} package, i.e. only
 * by {@link FhirResourceStore}. This is the structural guarantee that makes the
 * audit interceptor un-bypassable - every data-access path must go through the
 * store, which records an {@code AuditEvent}. See {@code AuditCoverageTest}.
 */
interface StoredResourceRepository extends JpaRepository<StoredResource, Long> {

    Optional<StoredResource> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    List<StoredResource> findByResourceType(String resourceType);

    List<StoredResource> findByResourceTypeAndPatientId(String resourceType, String patientId);
}
