package com.vitalshub.audit;

/**
 * Thread-local carrier for the current {@link AuditContext}. The REST layer
 * populates this from request headers ({@code X-Actor}, {@code X-Purpose}) via
 * an interceptor; programmatic callers (ingestion, sharing) set it explicitly.
 * The {@code FhirResourceStore} reads it when an explicit context is not passed.
 */
public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CURRENT =
            ThreadLocal.withInitial(() -> AuditContext.SYSTEM);

    private AuditContextHolder() {
    }

    public static AuditContext get() {
        return CURRENT.get();
    }

    public static void set(AuditContext context) {
        CURRENT.set(context == null ? AuditContext.SYSTEM : context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
