package com.vitalshub.consent;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Records and enforces consent. A share request must resolve to a consent for
 * the (patient, recipient) pair; only categories listed in that consent may be
 * disclosed, and de-identification may be mandated.
 */
@Service
public class ConsentService {

    private final ConsentRepository repository;

    public ConsentService(ConsentRepository repository) {
        this.repository = repository;
    }

    public ConsentRecord grant(String patientId, String recipient, Set<String> categories, boolean requireDeidentified) {
        return repository.save(new ConsentRecord(patientId, recipient, categories, requireDeidentified));
    }

    public Optional<ConsentRecord> find(String patientId, String recipient) {
        return repository.findFirstByPatientIdAndRecipient(patientId, recipient);
    }

    public ConsentRecord require(String patientId, String recipient) {
        return find(patientId, recipient)
                .orElseThrow(() -> new ConsentDeniedException(
                        "No consent on file for recipient '" + recipient + "' to access patient '" + patientId + "'"));
    }

    public List<ConsentRecord> findAll() {
        return repository.findAll();
    }

    public static class ConsentDeniedException extends RuntimeException {
        public ConsentDeniedException(String message) {
            super(message);
        }
    }
}
