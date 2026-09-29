package com.usm.servicerequest.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTokenValidatorTest {

    private static final String KID = "test-key-id-001";
    private static final String ISSUER = "university-identity-service";
    private static final String AUDIENCE = "university-services-platform";

    @Mock
    private IdentityJwksProvider jwksProvider;

    private IdentityServiceProperties properties;
    private ExternalTokenValidator validator;

    private KeyPair keyPair;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        keyPair = keyPairGen.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();

        properties = new IdentityServiceProperties();
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);

        validator = new ExternalTokenValidator(jwksProvider, properties);
    }

    private String createSignedJwt(String kid, String iss, String aud, String sub,
                                   List<String> roles, String universityId, String accountType,
                                   Date exp, RSAPrivateKey signingKey) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .build();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(iss)
                .audience(aud)
                .subject(sub)
                .issueTime(new Date())
                .expirationTime(exp);

        if (roles != null) {
            claimsBuilder.claim("roles", roles);
        }
        if (universityId != null) {
            claimsBuilder.claim("university_id", universityId);
        }
        if (accountType != null) {
            claimsBuilder.claim("account_type", accountType);
        }

        SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
        JWSSigner signer = new RSASSASigner(signingKey);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    @Test
    void validate_validTokenWithMultipleRoles_succeeds() throws Exception {
        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                KID, ISSUER, AUDIENCE, "usr-servicedesk-001",
                List.of("SERVICE_DESK_OFFICER", "ADMINISTRATIVE_STAFF"),
                "SDO001", "STAFF", exp, privateKey
        );

        ExternalTokenResult result = validator.validate(token);

        assertThat(result.userId()).isEqualTo("usr-servicedesk-001");
        assertThat(result.universityId()).isEqualTo("SDO001");
        assertThat(result.accountType()).isEqualTo("STAFF");
        assertThat(result.roles()).containsExactlyInAnyOrder(
                Role.SERVICE_DESK_OFFICER,
                Role.ADMINISTRATIVE_STAFF
        );
    }

    @Test
    void validate_expiredToken_isRejected() throws Exception {
        Date expired = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));
        String token = createSignedJwt(
                KID, ISSUER, AUDIENCE, "usr-1",
                List.of("STUDENT"), "STU001", "STUDENT", expired, privateKey
        );

        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validate_wrongIssuer_isRejected() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                KID, "wrong-issuer", AUDIENCE, "usr-1",
                List.of("STUDENT"), "STU001", "STUDENT", exp, privateKey
        );

        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("issuer mismatch");
    }

    @Test
    void validate_wrongAudience_isRejected() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                KID, ISSUER, "wrong-audience", "usr-1",
                List.of("STUDENT"), "STU001", "STUDENT", exp, privateKey
        );

        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("audience mismatch");
    }

    @Test
    void validate_unknownRoleInRolesArray_isSkippedNotFatal() throws Exception {
        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                KID, ISSUER, AUDIENCE, "usr-1",
                List.of("STUDENT", "SOME_FUTURE_ROLE_UNKNOWN_TO_SRV"),
                "STU001", "STUDENT", exp, privateKey
        );

        ExternalTokenResult result = validator.validate(token);

        assertThat(result.userId()).isEqualTo("usr-1");
        assertThat(result.roles()).containsExactly(Role.STUDENT);
    }

    @Test
    void validate_invalidSignature_isRejected() throws Exception {
        KeyPairGenerator otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        KeyPair otherKeyPair = otherGen.generateKeyPair();

        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        // Signed with a different private key than what the jwksProvider returns
        String token = createSignedJwt(
                KID, ISSUER, AUDIENCE, "usr-1",
                List.of("STUDENT"), "STU001", "STUDENT", exp, (RSAPrivateKey) otherKeyPair.getPrivate()
        );

        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid token signature");
    }

    @Test
    void validate_kidNotFound_isRejected() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                "unknown-kid", ISSUER, AUDIENCE, "usr-1",
                List.of("STUDENT"), "STU001", "STUDENT", exp, privateKey
        );

        when(jwksProvider.getPublicKey("unknown-kid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("No public key found for kid");
    }

    @Test
    void validate_missingSubject_isRejected() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(
                KID, ISSUER, AUDIENCE, "",
                List.of("STUDENT"), "STU001", "STUDENT", exp, privateKey
        );

        when(jwksProvider.getPublicKey(KID)).thenReturn(Optional.of(publicKey));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("subject ('sub') is missing or blank");
    }
}
