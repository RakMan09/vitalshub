package com.vitalshub.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates the {@link AuditContextHolder} for the duration of each HTTP request
 * from the {@code X-Actor} and {@code X-Purpose} headers, so audit records
 * capture "who" and "why". Applies to every request (FHIR and plain REST) and
 * always clears the thread-local afterwards.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditContextFilter extends OncePerRequestFilter {

    public static final String ACTOR_HEADER = "X-Actor";
    public static final String PURPOSE_HEADER = "X-Purpose";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String actor = valueOrDefault(request.getHeader(ACTOR_HEADER), "anonymous");
        String purpose = valueOrDefault(request.getHeader(PURPOSE_HEADER), "TREATMENT");
        AuditContextHolder.set(AuditContext.of(actor, purpose));
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditContextHolder.clear();
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
