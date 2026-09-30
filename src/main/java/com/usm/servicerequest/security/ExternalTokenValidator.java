package com.usm.servicerequest.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates RS256-signed JWTs issued by Group 5's identity-access-service
 * against public keys retrieved via IdentityJwksProvider.
 */
@Component
public class ExternalTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(ExternalTokenValidator.class);

    private final IdentityJwksProvider jwksProvider;
    private final IdentityServiceProperties properties;

    public ExternalTokenValidator(IdentityJwksProvider jwksProvider, IdentityServiceProperties properties) {
        this.jwksProvider = jwksProvider;
        this.properties = properties;
    }

    /**
     * Validates a raw JWT string against Group 5's contract:
     * 1. Header has kid and alg == RS256.
     * 2. Key matching kid fetched from IdentityJwksProvider.
     * 3. RS256 signature verified.
     * 4. Claims verified: exp in the future, iss matches, aud contains configured audience, sub present.
     * 5. Roles parsed into Set<Role>, skipping and logging unknown roles.
     *
     * @param rawJwt the raw JWT string
     * @return ExternalTokenResult containing extracted claims
     * @throws BadCredentialsException on any validation failure
     */
    public ExternalTokenResult validate(String rawJwt) {
        if (rawJwt == null || rawJwt.isBlank()) {
            throw new BadCredentialsException("Token is null or blank");
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(rawJwt);
        } catch (ParseException ex) {
            throw new BadCredentialsException("Failed to parse JWT: " + ex.getMessage(), ex);
        }

        // 1. Header checks
        JWSHeader header = signedJWT.getHeader();
        if (header.getAlgorithm() == null || !JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            throw new BadCredentialsException("Invalid token algorithm: " + header.getAlgorithm() + ", expected RS256");
        }

        String kid = header.getKeyID();
        if (kid == null || kid.isBlank()) {
            throw new BadCredentialsException("Token header missing 'kid' claim");
        }

        // 2. Fetch public key
        RSAPublicKey publicKey = jwksProvider.getPublicKey(kid)
                .orElseThrow(() -> new BadCredentialsException("No public key found for kid: " + kid));

        // 3. Verify signature
        try {
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new BadCredentialsException("Invalid token signature for kid: " + kid);
            }
        } catch (Exception ex) {
            throw new BadCredentialsException("Signature verification failed: " + ex.getMessage(), ex);
        }

        // 4. Validate claims
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException ex) {
            throw new BadCredentialsException("Failed to parse JWT claims set: " + ex.getMessage(), ex);
        }

        Date exp = claims.getExpirationTime();
        if (exp == null || !exp.toInstant().isAfter(Instant.now())) {
            throw new BadCredentialsException("Token has expired at " + exp);
        }

        String issuer = claims.getIssuer();
        if (!properties.getIssuer().equals(issuer)) {
            throw new BadCredentialsException("Token issuer mismatch: expected '" + properties.getIssuer()
                    + "', got '" + issuer + "'");
        }

        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(properties.getAudience())) {
            throw new BadCredentialsException("Token audience mismatch: expected '" + properties.getAudience()
                    + "', got " + audience);
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BadCredentialsException("Token subject ('sub') is missing or blank");
        }

        // 5. Map roles and extract claims
        String universityId = null;
        String accountType = null;
        try {
            universityId = claims.getStringClaim("university_id");
            accountType = claims.getStringClaim("account_type");
        } catch (ParseException ex) {
            log.warn("Failed to parse custom claims (university_id / account_type): {}", ex.getMessage());
        }

        Set<Role> roles = new LinkedHashSet<>();
        List<String> roleStrings = null;
        try {
            roleStrings = claims.getStringListClaim("roles");
        } catch (ParseException ex) {
            log.warn("Failed to parse 'roles' claim as string list: {}", ex.getMessage());
        }

        if (roleStrings != null) {
            for (String roleStr : roleStrings) {
                if (roleStr == null || roleStr.isBlank()) {
                    continue;
                }
                try {
                    Role role = Role.valueOf(roleStr);
                    roles.add(role);
                } catch (IllegalArgumentException unknownRole) {
                    log.warn("Unknown role '{}' in token claims; skipping per Group 5 integration guidance", roleStr);
                }
            }
        }

        return new ExternalTokenResult(sub, universityId, accountType, Collections.unmodifiableSet(roles));
    }
}
