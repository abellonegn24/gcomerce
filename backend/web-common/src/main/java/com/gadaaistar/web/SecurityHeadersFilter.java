package com.gadaaistar.web;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Emits a baseline set of security response headers on <em>every</em> HTTP
 * response — including error envelopes produced by {@link GlobalExceptionMapper}.
 *
 * <p>Primary purpose is anti-clickjacking defence (INSA CWE-1021 finding):
 * <ul>
 *   <li>{@code Content-Security-Policy: frame-ancestors 'self'} — the modern
 *       CSP-level control that prevents the application from being rendered
 *       inside an attacker-controlled iframe.</li>
 *   <li>{@code X-Frame-Options: DENY} — legacy defence-in-depth for browsers
 *       that do not honour the CSP {@code frame-ancestors} directive. {@code DENY}
 *       is used (rather than {@code SAMEORIGIN}) because these endpoints are
 *       JSON APIs that are never intended to be framed at all.</li>
 * </ul>
 *
 * <p>Two additional hardening headers are added opportunistically since they
 * are cheap, expected by auditors, and have no behavioural downside:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — disables MIME sniffing.</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — limits
 *       referrer leakage to cross-origin callers.</li>
 * </ul>
 *
 * <h4>Configuration</h4>
 * All values are configurable via {@code application.properties} so staging /
 * prod can tighten them without code changes, and a master switch
 * ({@code gadaa.security.headers.enabled=false}) disables the filter entirely
 * (useful for tests that assert an exact header set).
 *
 * <p><strong>Note:</strong> this filter only covers the Quarkus API hosts
 * ({@code api.*}, {@code admin-api.*}). The Angular SPA hosts are served by a
 * reverse proxy (Nginx / Cloudflare) which must emit the same headers on HTML
 * responses — see {@code AGENTS.md}.
 */
@Provider
@ApplicationScoped
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @ConfigProperty(name = "gadaa.security.headers.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "gadaa.security.headers.frame-ancestors", defaultValue = "'self'")
    String frameAncestors;

    @ConfigProperty(name = "gadaa.security.headers.x-frame-options", defaultValue = "DENY")
    String xFrameOptions;

    @Override
    public void filter(ContainerRequestContext requestCtx,
                       ContainerResponseContext responseCtx) throws IOException {
        if (!enabled) return;

        var headers = responseCtx.getHeaders();
        // putSingle — overwrite any caller-supplied value so a malicious upstream
        // cannot inject a more permissive frame-ancestors / X-Frame-Options.
        headers.putSingle("Content-Security-Policy", "frame-ancestors " + frameAncestors);
        headers.putSingle("X-Frame-Options", xFrameOptions);
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
