package com.vitalshub.web;

import com.vitalshub.adapters.IngestionContext;
import com.vitalshub.adapters.IngestionResult;
import com.vitalshub.adapters.IngestionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ingest raw heterogeneous source payloads. Example:
 * <pre>curl -X POST "localhost:8080/api/ingest/scale-csv?patientId=1" \
 *   -H "X-Actor: importer" --data-binary @scale.csv</pre>
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/sources")
    public List<String> sources() {
        return ingestionService.supportedSourceTypes();
    }

    @PostMapping(value = "/{sourceType}", consumes = MediaType.ALL_VALUE)
    public IngestionResult ingest(@PathVariable String sourceType,
                                  @RequestParam String patientId,
                                  @RequestHeader(value = "X-Actor", required = false) String actor,
                                  @RequestBody String body) {
        IngestionContext context = new IngestionContext(patientId, actor == null ? "api-user" : actor);
        return ingestionService.ingest(sourceType, body, context);
    }
}
