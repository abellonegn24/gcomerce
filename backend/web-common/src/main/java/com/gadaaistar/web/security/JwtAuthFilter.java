package com.gadaaistar.web.security;

import java.io.IOException;

import com.gadaaistar.common.Audience;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

/**
 * Global JAX-RS filter that inspects the {@code Authorization: Bearer …} header.
 *
 * <p>If a valid JWT is present for the appropriate audience,
 * {@link SecurityContextHolder} is populated and downstream endpoints can call
 * {@code requireCustomer()} / {@code requireAdmin()}.
 *
 * <p>The filter deliberately does <em>not</em> reject requests that lack a
 * token — public endpoints (product catalog, hello, etc.) must still work.
 * Invalid tokens are logged at DEBUG and treated as anonymous.
 *
 * <p>Path-based routing decides audience: requests under {@code /api/admin/}
 * are validated against the {@link Audience#ADMIN} secret; everything else
 * uses {@link Audience#STOREFRONT}.
 */
@Provider
@ApplicationScoped
public class JwtAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject JwtIssuer jwtIssuer;
    @Inject SecurityContextHolder holder;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        var path     = ctx.getUriInfo().getPath();
        var authHeader = ctx.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return; // anonymous
        }
        var token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) return;

        var audience = path.startsWith("api/admin/") || path.startsWith("/api/admin/")
                        ? Audience.ADMIN
                        : Audience.STOREFRONT;

        try {
            var claims = jwtIssuer.verify(token, audience);
            long principalId = Long.parseLong(claims.getSubject());
            String email     = (String) claims.getClaim("email");
            Object rolesRaw  = claims.getClaim("roles");
            String[] roles   = rolesRaw instanceof java.util.List<?> list
                                ? list.stream().map(Object::toString).toArray(String[]::new)
                                : new String[0];
            holder.set(audience, principalId, email, roles);
        } catch (Exception e) {
            LOG.debugf("Rejecting Bearer token: %s", e.getMessage());
            // Anonymous — endpoint-level checks will 401 if they require auth.
        }
    }
}
