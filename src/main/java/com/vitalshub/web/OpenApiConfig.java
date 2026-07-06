package com.vitalshub.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata for the VitalsHub REST API (the {@code /api/*}
 * endpoints). Browse it at {@code /swagger-ui.html}. The FHIR endpoints under
 * {@code /fhir/*} are served by HAPI's own RestfulServer and expose a FHIR
 * CapabilityStatement at {@code /fhir/metadata}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vitalsHubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("VitalsHub API")
                .version("0.1.0")
                .description("Personal health-data passport: ingest heterogeneous sources, "
                        + "normalize to FHIR, map terminology, de-identify + share under consent, "
                        + "and audit every access. FHIR resources are served under /fhir.")
                .license(new License().name("Apache-2.0")));
    }
}
