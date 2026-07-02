package com.vitalshub.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FhirFoundationSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    FhirContext fhirContext;

    IGenericClient client;

    @BeforeEach
    void setUp() {
        client = fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    }

    @Test
    void createAndReadPatient() {
        Patient patient = new Patient();
        patient.addName().setFamily("Doe").addGiven("Jane");

        String id = client.create().resource(patient).execute().getId().getIdPart();
        assertThat(id).isNotBlank();

        Patient read = client.read().resource(Patient.class).withId(id).execute();
        assertThat(read.getNameFirstRep().getFamily()).isEqualTo("Doe");
    }

    @Test
    void createObservationThenSearchUnifiedTimeline() {
        String patientId = client.create().resource(new Patient().addName(
                new org.hl7.fhir.r4.model.HumanName().setFamily("Scale"))).execute().getId().getIdPart();

        Observation weight = weightObservation("Patient/" + patientId, 82.5);
        client.create().resource(weight).execute();

        Bundle bundle = client.search().forResource(Observation.class)
                .where(Observation.PATIENT.hasId("Patient/" + patientId))
                .and(Observation.CODE.exactly().code("29463-7"))
                .returnBundle(Bundle.class)
                .execute();

        assertThat(bundle.getEntry()).hasSize(1);
        Observation found = (Observation) bundle.getEntryFirstRep().getResource();
        assertThat(found.getValueQuantity().getValue().doubleValue()).isEqualTo(82.5);
    }

    @Test
    void validationRejectsInvalidObservation() {
        Observation invalid = new Observation();
        // Missing required status and code -> must be rejected with 422.
        invalid.setSubject(new Reference("Patient/x"));

        assertThatThrownBy(() -> client.create().resource(invalid).execute())
                .isInstanceOf(UnprocessableEntityException.class);
    }

    private Observation weightObservation(String patientRef, double kg) {
        Observation obs = new Observation();
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.getCode().addCoding(new Coding("http://loinc.org", "29463-7", "Body weight"));
        obs.addCategory(new CodeableConcept().addCoding(new Coding(
                "http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")));
        obs.setSubject(new Reference(patientRef));
        obs.setEffective(new DateTimeType(new Date()));
        obs.setValue(new Quantity().setValue(kg).setUnit("kg").setSystem("http://unitsofmeasure.org").setCode("kg"));
        return obs;
    }
}
