package com.usm.servicerequest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Group 5's identity-access-service integration.
 * Bound from the `services.identity.*` block in application.yml.
 */
@ConfigurationProperties(prefix = "services.identity")
public class IdentityServiceProperties {

    private String baseUrl = "http://localhost:8001";
    private String jwksUri = "http://localhost:8001/.well-known/jwks.json";
    private String issuer = "university-identity-service";
    private String audience = "university-services-platform";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
