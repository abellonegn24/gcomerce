package com.gadaaistar.storefront.payment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Typed wrapper around the {@code gadaa.cybersource.*} config keys — kept in
 * one place so tests can point at a stub set of values and prod code can inject
 * a single dependency instead of six {@code @ConfigProperty} fields.
 */
@ApplicationScoped
public class CyberSourceConfig {

    @ConfigProperty(name = "gadaa.cybersource.env", defaultValue = "test")
    String env;

    @ConfigProperty(name = "gadaa.cybersource.endpoint",
                    defaultValue = "https://testsecureacceptance.cybersource.com/pay")
    String endpoint;

    @ConfigProperty(name = "gadaa.cybersource.access-key", defaultValue = "")
    String accessKey;

    @ConfigProperty(name = "gadaa.cybersource.profile-id", defaultValue = "")
    String profileId;

    @ConfigProperty(name = "gadaa.cybersource.secret-key", defaultValue = "")
    String secretKey;

    @ConfigProperty(name = "gadaa.frontend.storefront-url",
                    defaultValue = "http://localhost:4200")
    String storefrontBaseUrl;

    public String env()               { return env; }
    public String endpoint()          { return endpoint; }
    public String accessKey()         { return accessKey; }
    public String profileId()         { return profileId; }
    public String secretKey()         { return secretKey; }
    public String storefrontBaseUrl() { return storefrontBaseUrl; }

    /** True when the platform has real gateway credentials wired up. */
    public boolean isConfigured() {
        return !accessKey.isBlank()
                && !profileId.isBlank()
                && !secretKey.isBlank();
    }
}
