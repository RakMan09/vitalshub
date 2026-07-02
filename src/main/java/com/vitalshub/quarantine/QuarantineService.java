package com.vitalshub.quarantine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stores resources that failed validation, with the failure reason. This is the
 * "quarantine" side of the data-quality story: bad data is captured and
 * reportable, never silently discarded.
 */
@Service
public class QuarantineService {

    private final QuarantineRepository repository;
    private final IParser parser;

    public QuarantineService(QuarantineRepository repository, FhirContext fhirContext) {
        this.repository = repository;
        this.parser = fhirContext.newJsonParser();
    }

    public QuarantineRecord quarantine(String sourceType, Resource resource, String reason) {
        QuarantineRecord record = new QuarantineRecord(
                sourceType,
                resource.fhirType(),
                resource.getIdElement().getIdPart(),
                reason,
                parser.encodeResourceToString(resource));
        return repository.save(record);
    }

    public List<QuarantineRecord> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
