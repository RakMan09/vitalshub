package com.vitalshub.web;

import com.vitalshub.audit.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * "Who accessed my data?" - exposes the audit trail per patient and overall.
 */
@RestController
@RequestMapping("/api")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/patients/{patientId}/access-log")
    public List<Map<String, Object>> accessLog(@PathVariable String patientId) {
        return auditService.accessLogForPatient(patientId);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> all() {
        return auditService.allEvents();
    }
}
