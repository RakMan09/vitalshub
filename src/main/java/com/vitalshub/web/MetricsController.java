package com.vitalshub.web;

import com.vitalshub.metrics.DataQualityMetrics;
import com.vitalshub.quarantine.QuarantineRecord;
import com.vitalshub.quarantine.QuarantineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Data-quality reporting: validation pass rate, quarantine rate, terminology
 * mapping coverage, throughput, and the quarantine queue itself.
 */
@RestController
@RequestMapping("/api")
public class MetricsController {

    private final DataQualityMetrics metrics;
    private final QuarantineService quarantineService;

    public MetricsController(DataQualityMetrics metrics, QuarantineService quarantineService) {
        this.metrics = metrics;
        this.quarantineService = quarantineService;
    }

    @GetMapping("/metrics/data-quality")
    public Map<String, Object> dataQuality() {
        return metrics.snapshot();
    }

    @GetMapping("/quarantine")
    public List<Map<String, Object>> quarantine() {
        return quarantineService.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    private Map<String, Object> toSummary(QuarantineRecord record) {
        return Map.of(
                "id", record.getId(),
                "sourceType", record.getSourceType(),
                "resourceType", record.getResourceType(),
                "resourceId", record.getResourceId() == null ? "" : record.getResourceId(),
                "reason", record.getReason(),
                "createdAt", record.getCreatedAt().toString());
    }
}
