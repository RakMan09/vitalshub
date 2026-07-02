package com.vitalshub.adapters;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.vitalshub.normalize.ObservationFactory;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Adapter for a clinic's HL7 v2 ORU^R01 result message. HL7 v2 is pipe-delimited
 * segments (MSH, PID, PV1, OBX) - a very different shape from FHIR JSON. This
 * adapter parses it with the HAPI HL7v2 library and emits an {@code Encounter}
 * (from PV1) plus one {@code Observation} per OBX segment.
 */
@Component
public class Hl7v2Adapter implements SourceAdapter {

    @Override
    public String sourceType() {
        return "hl7v2";
    }

    @Override
    public List<Resource> toFhir(String rawContent, IngestionContext context) {
        String normalized = rawContent.replace("\r\n", "\r").replace("\n", "\r").trim();
        List<Resource> resources = new ArrayList<>();
        try (HapiContext hapiContext = new DefaultHapiContext()) {
            hapiContext.setValidationContext(ValidationContextFactory.noValidation());
            PipeParser parser = hapiContext.getPipeParser();
            Message message = parser.parse(normalized);
            if (!(message instanceof ORU_R01 oru)) {
                throw new IllegalArgumentException("Unsupported HL7 message type: " + message.getName());
            }

            var patientResult = oru.getPATIENT_RESULT();
            Encounter encounter = toEncounter(patientResult.getPATIENT().getVISIT().getPV1(), context);
            if (encounter != null) {
                resources.add(encounter);
            }

            var orderObservation = patientResult.getORDER_OBSERVATION();
            int obxCount = orderObservation.getOBSERVATIONReps();
            for (int i = 0; i < obxCount; i++) {
                OBX obx = orderObservation.getOBSERVATION(i).getOBX();
                Resource observation = toObservation(obx, context);
                if (observation != null) {
                    resources.add(observation);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid HL7 v2 message: " + e.getMessage(), e);
        }
        return resources;
    }

    private Resource toObservation(OBX obx, IngestionContext context) {
        String code = obx.getObservationIdentifier().getIdentifier().getValue();
        if (code == null || code.isBlank()) {
            return null;
        }
        String display = valueOr(obx.getObservationIdentifier().getText().getValue(), code);
        String codingSystem = obx.getObservationIdentifier().getNameOfCodingSystem().getValue();
        String system = "LN".equalsIgnoreCase(codingSystem)
                ? "http://loinc.org"
                : SourceCodeSystems.HL7V2_LOCAL;

        String valueType = obx.getValueType().getValue();
        String rawValue = encodeFirstValue(obx);
        Instant effective = parseHl7Time(obx.getDateTimeOfTheObservation().getTime().getValue());
        String unit = obx.getUnits().getIdentifier().getValue();

        if ("NM".equalsIgnoreCase(valueType) && rawValue != null && !rawValue.isBlank()) {
            var observation = ObservationFactory.quantity(context, system, code, display,
                    Double.parseDouble(rawValue), valueOr(unit, "1"), valueOr(unit, "1"), effective);
            return observation;
        }
        // Non-numeric OBX -> keep as a coded observation with a string value.
        org.hl7.fhir.r4.model.Observation obs = new org.hl7.fhir.r4.model.Observation();
        obs.setId((context.patientId() + "-" + code + "-" + effective.toEpochMilli()).replaceAll("[^A-Za-z0-9-]", "-"));
        obs.setStatus(org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL);
        obs.setCode(new CodeableConcept().addCoding(new Coding(system, code, display)).setText(display));
        obs.setSubject(new Reference(context.patientReference()));
        obs.setEffective(new org.hl7.fhir.r4.model.DateTimeType(Date.from(effective)));
        obs.setValue(new org.hl7.fhir.r4.model.StringType(rawValue));
        return obs;
    }

    private Encounter toEncounter(PV1 pv1, IngestionContext context) {
        String patientClass = pv1.getPatientClass().getValue();
        if (patientClass == null || patientClass.isBlank()) {
            return null;
        }
        Encounter encounter = new Encounter();
        encounter.setId((context.patientId() + "-encounter-" + patientClass).replaceAll("[^A-Za-z0-9-]", "-"));
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(mapEncounterClass(patientClass));
        encounter.setSubject(new Reference(context.patientReference()));
        String admit = pv1.getAdmitDateTime().getTime().getValue();
        if (admit != null && !admit.isBlank()) {
            encounter.setPeriod(new Period().setStart(Date.from(parseHl7Time(admit))));
        }
        return encounter;
    }

    private Coding mapEncounterClass(String hl7Class) {
        String system = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
        return switch (hl7Class.toUpperCase()) {
            case "I" -> new Coding(system, "IMP", "inpatient encounter");
            case "E" -> new Coding(system, "EMER", "emergency");
            case "O" -> new Coding(system, "AMB", "ambulatory");
            default -> new Coding(system, "AMB", "ambulatory");
        };
    }

    private String encodeFirstValue(OBX obx) {
        try {
            var values = obx.getObservationValue();
            if (values.length == 0) {
                return null;
            }
            return values[0].encode();
        } catch (Exception e) {
            return null;
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Instant parseHl7Time(String ts) {
        if (ts == null || ts.isBlank()) {
            return Instant.EPOCH;
        }
        String digits = ts.length() > 14 ? ts.substring(0, 14) : ts;
        if (digits.length() >= 14) {
            LocalDateTime dt = LocalDateTime.parse(digits.substring(0, 14),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            return dt.toInstant(ZoneOffset.UTC);
        }
        return LocalDate.parse(digits.substring(0, 8),
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
