package com.gadaaistar.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.MDC;

/**
 * Attaches a correlation ID to every request. Priority chain:
 *   1. If the caller sends {@code X-Correlation-Id}, use it verbatim (max 64 chars).
 *   2. Otherwise generate a UUID.
 *
 * <p>The ID is written to SLF4J MDC as {@code correlationId} (so it appears in
 * every log line via the configured pattern), stored on the request context as
 * property {@link #REQUEST_PROPERTY}, and echoed back in the response header.
 */
@Provider
@ApplicationScoped
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public  static final String HEADER            = "X-Correlation-Id";
    public  static final String REQUEST_PROPERTY  = "gadaa.correlationId";
    private static final String MDC_KEY           = "correlationId";
    private static final int    MAX_LEN           = 64;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        var inbound = ctx.getHeaderString(HEADER);
        var id = (inbound == null || inbound.isBlank())
                ? UUID.randomUUID().toString()
                : inbound.substring(0, Math.min(inbound.length(), MAX_LEN));
        ctx.setProperty(REQUEST_PROPERTY, id);
        MDC.put(MDC_KEY, id);
    }

    @Override
    public void filter(ContainerRequestContext requestCtx, ContainerResponseContext responseCtx) throws IOException {
        var id = requestCtx.getProperty(REQUEST_PROPERTY);
        if (id != null) {
            responseCtx.getHeaders().putSingle(HEADER, id.toString());
        }
        MDC.remove(MDC_KEY);
    }
}
