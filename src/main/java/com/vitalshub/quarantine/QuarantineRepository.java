package com.vitalshub.quarantine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarantineRepository extends JpaRepository<QuarantineRecord, Long> {
}
