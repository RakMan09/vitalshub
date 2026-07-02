package com.vitalshub.fhir.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates FHIR resources against the base R4 profiles using HAPI's
 * {@link FhirValidator}. Resources that fail (any ERROR/FATAL issue) are
 * quarantined by callers with the returned issue text as the reason.
 */
@Service
public class FhirValidationService {

    private final FhirValidator validator;

    public FhirValidationService(FhirContext fhirContext) {
        ValidationSupportChain support = new ValidationSupportChain(
                new ca.uhn.fhir.context.support.DefaultProfileValidationSupport(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext),
                new SnapshotGeneratingValidationSupport(fhirContext)
        );
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(support);
        this.validator = fhirContext.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    public ValidationOutcome validate(IBaseResource resource) {
        ValidationResult result = validator.validateWithResult(resource);
        List<String> issues = result.getMessages().stream()
                .filter(m -> m.getSeverity() == ResultSeverityEnum.ERROR
                        || m.getSeverity() == ResultSeverityEnum.FATAL)
                .map(this::describe)
                .toList();
        return new ValidationOutcome(issues.isEmpty(), issues);
    }

    private String describe(SingleValidationMessage message) {
        return message.getSeverity() + " @ " + message.getLocationString() + ": " + message.getMessage();
    }
}
