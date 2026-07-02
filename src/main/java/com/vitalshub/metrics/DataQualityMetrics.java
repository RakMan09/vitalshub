package com.vitalshub.metrics;

import com.vitalshub.adapters.IngestionResult;
import com.vitalshub.normalize.TerminologyMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cumulative data-quality and throughput metrics (spec section 9): validation
 * pass rate, quarantine rate, terminology mapping coverage, and records
 * normalized per second.
 */
@Component
public class DataQualityMetrics {

    private final TerminologyMapper terminologyMapper;

    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong stored = new AtomicLong();
    private final AtomicLong quarantined = new AtomicLong();
    private final AtomicLong elapsedNanos = new AtomicLong();
    private final Set<String> unmappedCodes = ConcurrentHashMap.newKeySet();

    public DataQualityMetrics(TerminologyMapper terminologyMapper) {
        this.terminologyMapper = terminologyMapper;
    }

    public void record(IngestionResult result, long nanos) {
        processed.addAndGet(result.totalProcessed());
        stored.addAndGet(result.getStoredIds().size());
        quarantined.addAndGet(result.getQuarantined().size());
        elapsedNanos.addAndGet(nanos);
        unmappedCodes.addAll(result.getUnmappedCodes());
    }

    public Map<String, Object> snapshot() {
        long total = processed.get();
        long storedCount = stored.get();
        long quarantinedCount = quarantined.get();
        double seconds = elapsedNanos.get() / 1_000_000_000.0;

        return Map.of(
                "recordsProcessed", total,
                "recordsStored", storedCount,
                "recordsQuarantined", quarantinedCount,
                "validationPassRate", total == 0 ? 1.0 : (double) storedCount / total,
                "quarantineRate", total == 0 ? 0.0 : (double) quarantinedCount / total,
                "recordsPerSecond", seconds == 0.0 ? 0.0 : total / seconds,
                "terminologyMappingsLoaded", terminologyMapper.mappingCount(),
                "unmappedCodeCount", unmappedCodes.size(),
                "unmappedCodes", unmappedCodes.stream().sorted().toList());
    }
}
