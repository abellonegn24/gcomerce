package com.gadaaistar.web.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.gadaaistar.common.Audience;
import com.gadaaistar.common.exceptions.UnauthorizedException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Issues and verifies HS256-signed JWTs for storefront and admin audiences.
 *
 * <p>Two token flavors:
 * <ul>
 *   <li>{@link #issueAccessToken} — long-lived (minutes) session token, standard
 *       claims plus {@code email}, {@code roles}, {@code aud_type}.</li>
 *   <li>{@link #issueShortLivedToken} — short-lived (seconds/minutes) purpose-built
 *       token for two-phase flows (MFA challenges, email verification links, ...).
 *       Uses {@code typ} (JOSE header) to distinguish purpose.</li>
 * </ul>
 *
 * <p><strong>Deployment note:</strong> HMAC-SHA256 with a shared secret is fine for
 * a single-service deployment. If we ever split the auth server from the resource
 * server (or introduce a mobile client that verifies tokens locally), swap this
 * for RS256 / ES256 with a JWKS endpoint.
 */
@ApplicationScoped
public class JwtIssuer {

    private static final Logger LOG = Logger.getLogger(JwtIssuer.class);

    @ConfigProperty(name = "gadaa.jwt.storefront-issuer")   String storefrontIssuer;
    @ConfigProperty(name = "gadaa.jwt.storefront-secret")   String storefrontSecret;

    @ConfigProperty(name = "gadaa.jwt.admin-issuer")        String adminIssuer;
    @ConfigProperty(name = "gadaa.jwt.admin-secret")        String adminSecret;

    @ConfigProperty(name = "gadaa.jwt.access-ttl-minutes")  int accessTtlMinutes;

    // -------------------------------------------------------------------------
    // Issuing — access tokens
    // -------------------------------------------------------------------------

    public IssuedToken issueAccessToken(Audience audience, long subjectId, String email, String... roles) {
        var now       = Instant.now();
        var expiresAt = now.plus(Duration.ofMinutes(accessTtlMinutes));

        var claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(subjectId))
                .issuer(issuerFor(audience))
                .audience(audienceValueFor(audience))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("roles", roles == null ? new String[0] : roles)
                .claim("aud_type", audience.name())
                .build();

        return sign(claims, "JWT", audience);
    }

    // -------------------------------------------------------------------------
    // Issuing — short-lived purpose tokens (e.g. MFA challenge)
    // -------------------------------------------------------------------------

    /**
     * Issue a purpose-scoped short-lived JWT. The {@code purpose} label is
     * placed in the JOSE {@code typ} header so verifiers can reject a token
     * that was issued for the wrong flow (defence-in-depth).
     */
    public IssuedToken issueShortLivedToken(Audience audience, long subjectId, String purpose,
                                             Duration ttl, Map<String, Object> extraClaims) {
        var now       = Instant.now();
        var expiresAt = now.plus(ttl);

        var builder = new JWTClaimsSet.Builder()
                .subject(String.valueOf(subjectId))
                .issuer(issuerFor(audience))
                .audience(audienceValueFor(audience))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("aud_type", audience.name())
                .claim("purpose", purpose);

        if (extraClaims != null) {
            for (var e : extraClaims.entrySet()) builder.claim(e.getKey(), e.getValue());
        }

        return sign(builder.build(), purpose, audience);
    }

    // -------------------------------------------------------------------------
    // Verifying
    // -------------------------------------------------------------------------

    public JWTClaimsSet verify(String token, Audience expectedAudience) {
        return verifyInternal(token, expectedAudience, null);
    }

    /**
     * Verify AND require the JOSE {@code typ} header to match {@code expectedPurpose}.
     * Use for short-lived purpose tokens issued via {@link #issueShortLivedToken}.
     */
    public JWTClaimsSet verifyPurpose(String token, Audience expectedAudience, String expectedPurpose) {
        return verifyInternal(token, expectedAudience, expectedPurpose);
    }

    // -------------------------------------------------------------------------

    private IssuedToken sign(JWTClaimsSet claims, String type, Audience audience) {
        var header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType(type == null ? "JWT" : type))
                .build();
        var signed = new SignedJWT(header, claims);
        try {
            signed.sign(new MACSigner(secretFor(audience).getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            LOG.errorf(e, "Failed to sign JWT for audience %s", audience);
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return new IssuedToken(signed.serialize(), claims.getExpirationTime().toInstant());
    }

    private JWTClaimsSet verifyInternal(String token, Audience expectedAudience, String expectedPurpose) {
        SignedJWT signed;
        try {
            signed = SignedJWT.parse(token);
        } catch (java.text.ParseException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "Malformed JWT.");
        }

        try {
            var verifier = new MACVerifier(secretFor(expectedAudience).getBytes(StandardCharsets.UTF_8));
            if (!signed.verify(verifier)) {
                throw new UnauthorizedException("INVALID_TOKEN", "JWT signature verification failed.");
            }
        } catch (JOSEException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "JWT verification error: " + e.getMessage());
        }

        JWTClaimsSet claims;
        try {
            claims = signed.getJWTClaimsSet();
        } catch (java.text.ParseException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "JWT claims are malformed.");
        }

        var now = new Date();
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "JWT has expired.");
        }
        if (!issuerFor(expectedAudience).equals(claims.getIssuer())) {
            throw new UnauthorizedException("INVALID_TOKEN", "JWT issuer does not match.");
        }
        var audValue = audienceValueFor(expectedAudience);
        if (claims.getAudience() == null || !claims.getAudience().contains(audValue)) {
            throw new UnauthorizedException("INVALID_TOKEN", "JWT audience does not match.");
        }
        if (expectedPurpose != null) {
            var actualType = signed.getHeader().getType();
            if (actualType == null || !expectedPurpose.equals(actualType.getType())) {
                throw new UnauthorizedException("INVALID_TOKEN",
                        "JWT purpose does not match (expected " + expectedPurpose + ").");
            }
        }

        return claims;
    }

    private String issuerFor(Audience audience) {
        return switch (audience) {
            case STOREFRONT -> storefrontIssuer;
            case ADMIN      -> adminIssuer;
        };
    }

    private String secretFor(Audience audience) {
        var secret = switch (audience) {
            case STOREFRONT -> storefrontSecret;
            case ADMIN      -> adminSecret;
        };
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret for " + audience + " is missing or shorter than 32 characters.");
        }
        return secret;
    }

    private String audienceValueFor(Audience audience) {
        return switch (audience) {
            case STOREFRONT -> "gadaa-storefront";
            case ADMIN      -> "gadaa-admin";
        };
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
