package com.vitalshub.fhir.server;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.vitalshub.fhir.store.FhirResourceStore;
import com.vitalshub.fhir.validation.FhirValidationService;
import com.vitalshub.fhir.validation.ValidationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PatientResourceProvider implements IResourceProvider {

    private final FhirResourceStore store;
    private final FhirValidationService validationService;

    public PatientResourceProvider(FhirResourceStore store, FhirValidationService validationService) {
        this.store = store;
        this.validationService = validationService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id) {
        return store.read("Patient", id.getIdPart())
                .map(r -> (Patient) r)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient patient) {
        validate(patient);
        Patient saved = (Patient) store.create(patient);
        return new MethodOutcome().setCreated(true).setResource(saved).setId(saved.getIdElement());
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
        patient.setId(id.getIdPart());
        validate(patient);
        Patient saved = (Patient) store.update(patient);
        return new MethodOutcome().setResource(saved).setId(saved.getIdElement());
    }

    @Search
    @SuppressWarnings("unchecked")
    public List<Patient> searchAll() {
        return (List<Patient>) (List<?>) store.search("Patient");
    }

    private void validate(Patient patient) {
        ValidationOutcome outcome = validationService.validate(patient);
        if (!outcome.valid()) {
            throw new UnprocessableEntityException("Patient failed validation: " + outcome.summary());
        }
    }
}
