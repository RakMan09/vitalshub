package com.vitalshub.fhir.server;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Sort;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.vitalshub.fhir.store.FhirResourceStore;
import com.vitalshub.fhir.timeline.TimelineService;
import com.vitalshub.fhir.validation.FhirValidationService;
import com.vitalshub.fhir.validation.ValidationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ObservationResourceProvider implements IResourceProvider {

    private final FhirResourceStore store;
    private final FhirValidationService validationService;
    private final TimelineService timelineService;

    public ObservationResourceProvider(FhirResourceStore store,
                                       FhirValidationService validationService,
                                       TimelineService timelineService) {
        this.store = store;
        this.validationService = validationService;
        this.timelineService = timelineService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Observation.class;
    }

    @Read
    public Observation read(@IdParam IdType id) {
        return store.read("Observation", id.getIdPart())
                .map(r -> (Observation) r)
                .orElseThrow(() -> new ResourceNotFoundException(id));
    }

    @Create
    public MethodOutcome create(@ResourceParam Observation observation) {
        validate(observation);
        Observation saved = (Observation) store.create(observation);
        return new MethodOutcome().setCreated(true).setResource(saved).setId(saved.getIdElement());
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Observation observation) {
        observation.setId(id.getIdPart());
        validate(observation);
        Observation saved = (Observation) store.update(observation);
        return new MethodOutcome().setResource(saved).setId(saved.getIdElement());
    }

    /**
     * Unified timeline search: returns a patient's observations across all
     * sources, optionally filtered by LOINC/SNOMED code and sorted by date.
     * Example: {@code GET /fhir/Observation?patient=Patient/1&code=29463-7&_sort=date}
     */
    @Search
    public List<Observation> search(
            @OptionalParam(name = "patient") ReferenceParam patient,
            @OptionalParam(name = "code") TokenParam code,
            @Sort SortSpec sort) {
        String patientRef = patient == null ? null : patient.getValue();
        String codeValue = code == null ? null : code.getValue();
        boolean sortByDate = sort != null && "date".equalsIgnoreCase(sort.getParamName());
        return timelineService.observations(patientRef, codeValue, sortByDate);
    }

    private void validate(Observation observation) {
        ValidationOutcome outcome = validationService.validate(observation);
        if (!outcome.valid()) {
            throw new UnprocessableEntityException("Observation failed validation: " + outcome.summary());
        }
    }
}
