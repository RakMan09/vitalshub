package com.vitalshub.web;

import ca.uhn.fhir.context.FhirContext;
import com.vitalshub.consent.ConsentService;
import com.vitalshub.deid.ShareService;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Produces a share-safe snapshot for a recipient. Returns 403 when no consent
 * is on file. Example:
 * <pre>curl "localhost:8080/api/patients/1/share?recipient=dr-smith"</pre>
 */
@RestController
public class ShareController {

    private final ShareService shareService;
    private final FhirContext fhirContext;

    public ShareController(ShareService shareService, FhirContext fhirContext) {
        this.shareService = shareService;
        this.fhirContext = fhirContext;
    }

    @GetMapping(value = "/api/patients/{patientId}/share", produces = "application/fhir+json")
    public String share(@PathVariable String patientId, @RequestParam String recipient) {
        Bundle bundle = shareService.share(patientId, recipient);
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    @ExceptionHandler(ConsentService.ConsentDeniedException.class)
    public ResponseEntity<String> handleConsentDenied(ConsentService.ConsentDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
