package com.gadaaistar.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.gadaaistar.common.ErrorResponse;
import com.gadaaistar.common.exceptions.ApiException;
import com.gadaaistar.common.exceptions.RateLimitExceededException;

import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.validation.ConstraintViolationException;

import org.jboss.logging.Logger;

/**
 * Maps every uncaught exception in the API to the standard error envelope.
 *
 * <p>Priorities:
 * <ol>
 *   <li>{@link ApiException} — respected as-is; status &amp; code come from the exception.</li>
 *   <li>{@link ConstraintViolationException} — turned into 400 with field-error map.</li>
 *   <li>{@link NotFoundException} (JAX-RS routing miss) — 404 {@code ROUTE_NOT_FOUND}.</li>
 *   <li>{@link WebApplicationException} — pass through with a generic envelope.</li>
 *   <li>Anything else — 500 {@code INTERNAL_ERROR}, logged with stack trace.</li>
 * </ol>
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Context ContainerRequestContext requestCtx;

    // Unused right now but injected so we can extend later without breaking sig.
    @Inject @SuppressWarnings("unused")
    ResourceContext resourceCtx;

    @Override
    public Response toResponse(Throwable ex) {
        var correlationId = correlationId();

        if (ex instanceof RateLimitExceededException rl) {
            // Same envelope as any ApiException + the standard Retry-After header
            // so well-behaved clients (browsers, curl --retry, mobile apps) back off.
            return build(rl.status(), rl.code(), rl.getMessage(), rl.details(), correlationId)
                    .toResponseBuilder()
                    .header("Retry-After", String.valueOf(rl.retryAfterSeconds()))
                    .build();
        }
        if (ex instanceof ApiException api) {
            return build(api.status(), api.code(), api.getMessage(), api.details(), correlationId).build();
        }
        if (ex instanceof ConstraintViolationException cve) {
            var details = new HashMap<String, Object>();
            for (var v : cve.getConstraintViolations()) {
                var path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
                var key  = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                if (key.isBlank()) key = "value";
                details.put(key, v.getMessage());
            }
            return build(400, "VALIDATION_FAILED", "One or more fields are invalid.", details, correlationId).build();
        }
        if (ex instanceof NotFoundException) {
            return build(404, "ROUTE_NOT_FOUND", "The requested resource does not exist.", Map.of(), correlationId).build();
        }
        if (ex instanceof WebApplicationException wae) {
            var status = wae.getResponse().getStatus();
            var code   = status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED";
            return build(status, code, safeMessage(wae, status), Map.of(), correlationId).build();
        }

        LOG.errorf(ex, "Unhandled exception [correlationId=%s]", correlationId);
        return build(500, "INTERNAL_ERROR",
                "Something went wrong on our end. Please try again.", Map.of(), correlationId).build();
    }

    // -------------------------------------------------------------------------

    private BuiltResponse build(int status, String code, String message,
                                Map<String, Object> details, String correlationId) {
        var body = new ErrorResponse(new ErrorResponse.ErrorBody(
                code, message, details == null ? Map.of() : details,
                correlationId, Instant.now()));
        return new BuiltResponse(Response.status(status).entity(body));
    }

    /**
     * Thin wrapper so {@link #toResponse(Throwable)} can either return the
     * response as-is <em>or</em> chain extra headers (e.g. Retry-After for 429).
     * Call sites finish with {@code .build()} to get a plain {@link Response}.
     */
    private record BuiltResponse(Response.ResponseBuilder builder) {
        Response.ResponseBuilder toResponseBuilder() { return builder; }
        Response build() { return builder.build(); }
    }

    private String correlationId() {
        if (requestCtx == null) return null;
        var id = requestCtx.getProperty(CorrelationIdFilter.REQUEST_PROPERTY);
        return id == null ? null : id.toString();
    }

    private static String safeMessage(WebApplicationException wae, int status) {
        // Don't leak internal message on 5xx
        if (status >= 500) return "Something went wrong on our end. Please try again.";
        return wae.getMessage() == null ? "Request failed." : wae.getMessage();
    }
}
