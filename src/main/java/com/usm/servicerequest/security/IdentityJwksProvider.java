package com.usm.servicerequest.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Optional;

/**
 * Fetches and caches the JWK Set from Group 5's identity-access-service.
 * Provides the RSA public key for a given kid, refetching once if an unknown
 * kid is encountered (per Group 5's integration guidance).
 */
@Component
public class IdentityJwksProvider {

    private static final Logger log = LoggerFactory.getLogger(IdentityJwksProvider.class);

    private final RestTemplate restTemplate;
    private final IdentityServiceProperties properties;
    private volatile JWKSet cachedJwkSet;

    @Autowired
    public IdentityJwksProvider(RestTemplateBuilder restTemplateBuilder, IdentityServiceProperties properties) {
        this(restTemplateBuilder.build(), properties);
    }

    public IdentityJwksProvider(RestTemplate restTemplate, IdentityServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Retrieves the RSA public key for the specified key ID (kid).
     * If the kid is not found in the current cache, refetches the JWKS once
     * before concluding the key is unavailable.
     *
     * @param kid the key ID from the JWT header
     * @return the RSA public key if found, or empty if not found or unreachable
     */
    public Optional<RSAPublicKey> getPublicKey(String kid) {
        if (kid == null || kid.isBlank()) {
            log.warn("getPublicKey requested with null or blank kid");
            return Optional.empty();
        }

        RSAPublicKey key = findKeyInSet(cachedJwkSet, kid);
        if (key != null) {
            return Optional.of(key);
        }

        // Unknown kid in cache: refetch once per Group 5 guidance
        log.info("Key ID '{}' not found in cache. Refetching JWK Set from '{}'", kid, properties.getJwksUri());
        JWKSet refreshed = fetchJwks();
        key = findKeyInSet(refreshed, kid);
        if (key != null) {
            return Optional.of(key);
        }

        log.warn("Key ID '{}' not found in JWK Set from '{}'", kid, properties.getJwksUri());
        return Optional.empty();
    }

    private synchronized JWKSet fetchJwks() {
        String uri = properties.getJwksUri();
        try {
            String response = restTemplate.getForObject(uri, String.class);
            if (response == null || response.isBlank()) {
                log.error("JWKS endpoint '{}' returned empty response", uri);
                return null;
            }
            JWKSet parsed = JWKSet.parse(response);
            this.cachedJwkSet = parsed;
            return parsed;
        } catch (ParseException ex) {
            log.error("Failed to parse JWK Set response from '{}': {}", uri, ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.error("Failed to fetch JWK Set from '{}': {}", uri, ex.getMessage());
            return null;
        }
    }

    private RSAPublicKey findKeyInSet(JWKSet set, String kid) {
        if (set == null || kid == null) {
            return null;
        }
        JWK jwk = set.getKeyByKeyId(kid);
        if (jwk instanceof RSAKey rsaKey) {
            try {
                return rsaKey.toRSAPublicKey();
            } catch (JOSEException ex) {
                log.error("Failed to convert JWK to RSA public key for kid '{}': {}", kid, ex.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Visible for testing. */
    void setCachedJwkSet(JWKSet jwkSet) {
        this.cachedJwkSet = jwkSet;
    }
}
