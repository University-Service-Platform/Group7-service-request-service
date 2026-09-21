package com.usm.servicerequest.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from the `usm.jwt.*` block in application.yml - the ONE place the
 * placeholder secret/issuer live. Swapping to Group 5's real signing config
 * later means editing application.yml (or, in prod, an env var / secret
 * store), never Java code. See guide §7 and §13 row 1.
 */
@ConfigurationProperties(prefix = "usm.jwt")
public class JwtProperties {

    private String secret;
    private String issuer;
    private long expirationMinutes = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
