package com.vitalshub.adapters;

import com.vitalshub.normalize.ObservationFactory;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for an Apple-Health-style XML export ({@code <Record type="HK..."/>}).
 * The HealthKit type identifier is preserved as the source code so the
 * terminology mapper can map it to LOINC downstream.
 */
@Component
public class AppleHealthXmlAdapter implements SourceAdapter {

    private static final DateTimeFormatter APPLE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final String HK_PREFIX = "HKQuantityTypeIdentifier";

    @Override
    public String sourceType() {
        return "apple-health-xml";
    }

    @Override
    public List<Resource> toFhir(String rawContent, IngestionContext context) {
        List<Resource> resources = new ArrayList<>();
        Document document = parse(rawContent);
        NodeList records = document.getElementsByTagName("Record");
        for (int i = 0; i < records.getLength(); i++) {
            Element record = (Element) records.item(i);
            String type = record.getAttribute("type");
            String valueText = record.getAttribute("value");
            if (type.isBlank() || valueText.isBlank()) {
                continue;
            }
            String unit = record.getAttribute("unit");
            resources.add(ObservationFactory.quantity(context,
                    SourceCodeSystems.APPLE_HEALTH,
                    type,
                    displayFor(type),
                    Double.parseDouble(valueText),
                    unit,
                    ucumFor(unit),
                    parseDate(record.getAttribute("startDate"))));
        }
        return resources;
    }

    private Document parse(String rawContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(rawContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Apple Health XML: " + e.getMessage(), e);
        }
    }

    private String displayFor(String type) {
        return type.startsWith(HK_PREFIX) ? type.substring(HK_PREFIX.length()) : type;
    }

    private String ucumFor(String unit) {
        return switch (unit) {
            case "count/min" -> "/min";
            case "count" -> "{count}";
            default -> unit;
        };
    }

    private Instant parseDate(String value) {
        return OffsetDateTime.parse(value, APPLE_DATE).toInstant();
    }
}
