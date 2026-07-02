package com.vitalshub.adapters;

import com.vitalshub.audit.AuditContext;
import com.vitalshub.audit.AuditContextHolder;
import com.vitalshub.fhir.store.FhirResourceStore;
import com.vitalshub.fhir.validation.FhirValidationService;
import com.vitalshub.fhir.validation.ValidationOutcome;
import com.vitalshub.metrics.DataQualityMetrics;
import com.vitalshub.normalize.TerminologyMapper;
import com.vitalshub.normalize.TerminologyResult;
import com.vitalshub.quarantine.QuarantineService;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the ingestion pipeline for every source:
 * <pre>adapter (parse to FHIR) -&gt; terminology map -&gt; validate -&gt; store</pre>
 * Resources that fail validation are quarantined with a reason rather than
 * silently dropped, and unmapped source codes are flagged.
 */
@Service
public class IngestionService {

    private final Map<String, SourceAdapter> adapters;
    private final TerminologyMapper terminologyMapper;
    private final FhirValidationService validationService;
    private final FhirResourceStore store;
    private final QuarantineService quarantineService;
    private final DataQualityMetrics metrics;

    public IngestionService(List<SourceAdapter> adapters,
                            TerminologyMapper terminologyMapper,
                            FhirValidationService validationService,
                            FhirResourceStore store,
                            QuarantineService quarantineService,
                            DataQualityMetrics metrics) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(SourceAdapter::sourceType, Function.identity()));
        this.terminologyMapper = terminologyMapper;
        this.validationService = validationService;
        this.store = store;
        this.quarantineService = quarantineService;
        this.metrics = metrics;
    }

    public IngestionResult ingest(String sourceType, String rawContent, IngestionContext context) {
        SourceAdapter adapter = adapters.get(sourceType);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for source type: " + sourceType);
        }

        IngestionResult result = new IngestionResult(sourceType);
        AuditContext previous = AuditContextHolder.get();
        AuditContextHolder.set(AuditContext.of(context.actor(), "INGESTION"));
        long start = System.nanoTime();
        try {
            List<Resource> resources = adapter.toFhir(rawContent, context);
            for (Resource resource : resources) {
                TerminologyResult mapping = terminologyMapper.map(resource);
                mapping.unmappedCodes().forEach(result::addUnmappedCode);

                ValidationOutcome outcome = validationService.validate(resource);
                if (outcome.valid()) {
                    Resource stored = store.create(resource);
                    result.addStored(stored.getIdElement().getIdPart());
                } else {
                    String reason = outcome.summary();
                    quarantineService.quarantine(sourceType, resource, reason);
                    result.addQuarantined(resource.fhirType(),
                            resource.getIdElement().getIdPart(), reason);
                }
            }
        } finally {
            metrics.record(result, System.nanoTime() - start);
            AuditContextHolder.set(previous);
        }
        return result;
    }

    public List<String> supportedSourceTypes() {
        return adapters.keySet().stream().sorted().toList();
    }
}
