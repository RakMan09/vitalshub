package com.vitalshub.fhir.server;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.vitalshub.fhir.store.FhirResourceStore;
import com.vitalshub.fhir.validation.FhirValidationService;
import com.vitalshub.fhir.validation.ValidationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncounterResourceProvider implements IResourceProvider {

    private final FhirResourceStore store;
    private final FhirValidationService validationService;

    public EncounterResourceProvider(FhirResourceStore store, FhirValidationService validationService) {
        this.store = store;
        this.validationService = validationService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Encounter.class;
    }

    @Read
    public Encounter read(@IdParam IdType id) {
        return store.read("Encounter", id.getIdPart())
                .map(r -> (Encounter) r)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Create
    public MethodOutcome create(@ResourceParam Encounter encounter) {
        validate(encounter);
        Encounter saved = (Encounter) store.create(encounter);
        return new MethodOutcome().setCreated(true).setResource(saved).setId(saved.getIdElement());
    }

    @Search
    public List<Encounter> search(@OptionalParam(name = "patient") ReferenceParam patient) {
        List<Resource> raw = patient == null
                ? store.search("Encounter")
                : store.searchByPatient("Encounter", normalize(patient.getValue()));
        return raw.stream().filter(Encounter.class::isInstance).map(Encounter.class::cast).toList();
    }

    private String normalize(String reference) {
        return reference.contains("/") ? reference : "Patient/" + reference;
    }

    private void validate(Encounter encounter) {
        ValidationOutcome outcome = validationService.validate(encounter);
        if (!outcome.valid()) {
            throw new UnprocessableEntityException("Encounter failed validation: " + outcome.summary());
        }
    }
}
