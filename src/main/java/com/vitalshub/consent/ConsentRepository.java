package com.vitalshub.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findFirstByPatientIdAndRecipient(String patientId, String recipient);
}
