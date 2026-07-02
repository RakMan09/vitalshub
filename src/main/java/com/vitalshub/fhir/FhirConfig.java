package com.vitalshub.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Central FHIR configuration. A {@link FhirContext} is expensive to create and
 * thread-safe once built, so it is shared application-wide as a singleton bean.
 * The HAPI {@link RestfulServer} is mounted as a servlet at {@code /fhir/*} with
 * all discovered resource providers registered.
 */
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServletRegistration(
            FhirContext fhirContext, List<IResourceProvider> resourceProviders) {
        RestfulServer server = new RestfulServer(fhirContext);
        server.setResourceProviders(resourceProviders);
        server.setDefaultPrettyPrint(true);
        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
