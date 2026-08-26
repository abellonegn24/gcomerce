package com.gadaaistar.web.ratelimit;

import java.time.Duration;

import com.gadaaistar.common.exceptions.RateLimitExceededException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS request filter that enforces {@link RateLimit} annotations.
 *
 * <p>Runs <em>before</em> authentication (priority = AUTHENTICATION − 100) so
 * unauthenticated abuse of /auth/login etc. also counts against the limit.
 *
 * <h4>Client identity</h4>
 * We derive a stable identifier from, in order:
 * <ol>
 *   <li>The first entry of {@code X-Forwarded-For} (trusted behind Cloudflare / Nginx).</li>
 *   <li>{@code X-Real-IP}.</li>
 *   <li>The Vert.x socket remote address.</li>
 * </ol>
 * Falls back to the string {@code "unknown"} so that even mis-configured
 * proxies get <em>some</em> rate-limiting instead of none.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RateLimitFilter implements ContainerRequestFilter {

    @Context ResourceInfo resourceInfo;
    @Context HttpServerRequest vertxRequest;

    @Inject RateLimitService service;

    @Override
    public void filter(ContainerRequestContext ctx) {
        RateLimit limit = resolveAnnotation();
        if (limit == null) return;                // endpoint isn't rate-limited

        String clientId = resolveClientId(ctx);
        var decision = service.tryAcquire(
                limit.bucket(), clientId, limit.max(),
                Duration.ofSeconds(limit.windowSeconds()));

        if (!decision.allowed()) {
            // Delegate to GlobalExceptionMapper — it turns this into a 429
            // envelope and injects the Retry-After header.
            throw new RateLimitExceededException(limit.bucket(), decision.retryAfterSeconds());
        }
    }

    // -------------------------------------------------------------------------

    private RateLimit resolveAnnotation() {
        var method = resourceInfo.getResourceMethod();
        if (method != null) {
            var annotation = method.getAnnotation(RateLimit.class);
            if (annotation != null) return annotation;
        }
        var cls = resourceInfo.getResourceClass();
        return cls != null ? cls.getAnnotation(RateLimit.class) : null;
    }

    private String resolveClientId(ContainerRequestContext ctx) {
        var xff = ctx.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
        }
        var xRealIp = ctx.getHeaderString("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) return xRealIp.trim();

        if (vertxRequest != null && vertxRequest.remoteAddress() != null) {
            return vertxRequest.remoteAddress().host();
        }
        return "unknown";
    }
}
