package com.vitalshub.web;

import com.vitalshub.consent.ConsentRecord;
import com.vitalshub.consent.ConsentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manage patient consent records that gate sharing.
 */
@RestController
@RequestMapping("/api/consent")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    public record GrantRequest(String patientId, String recipient, Set<String> categories, boolean requireDeidentified) {
    }

    @PostMapping
    public Map<String, Object> grant(@RequestBody GrantRequest request) {
        ConsentRecord record = consentService.grant(
                request.patientId(), request.recipient(),
                request.categories() == null ? Set.of() : request.categories(),
                request.requireDeidentified());
        return toSummary(record);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return consentService.findAll().stream().map(this::toSummary).toList();
    }

    private Map<String, Object> toSummary(ConsentRecord record) {
        return Map.of(
                "id", record.getId(),
                "patientId", record.getPatientId(),
                "recipient", record.getRecipient(),
                "categories", record.getCategories(),
                "requireDeidentified", record.isRequireDeidentified(),
                "createdAt", record.getCreatedAt().toString());
    }
}
