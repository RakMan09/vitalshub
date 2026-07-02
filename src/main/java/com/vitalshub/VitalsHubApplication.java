package com.vitalshub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * VitalsHub - a personal health-data passport.
 *
 * Ingests heterogeneous health sources (wearable JSON, smart-scale CSV, HL7 v2,
 * Apple-Health XML), normalizes them into validated FHIR R4 resources, maps
 * terminology to a LOINC/SNOMED subset, de-identifies snapshots for sharing,
 * enforces consent, and audits every data-access operation.
 */
@SpringBootApplication
public class VitalsHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(VitalsHubApplication.class, args);
    }
}
