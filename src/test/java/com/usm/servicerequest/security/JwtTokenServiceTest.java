package com.usm.servicerequest.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-key-at-least-32-bytes-long-000000");
        properties.setIssuer("usm-g7-test");
        properties.setExpirationMinutes(60);
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void generateThenParse_roundTripsTheSameClaims() {
        String token = jwtTokenService.generateToken("u-123", Role.SERVICE_DESK_OFFICER, "IT Services");

        Optional<AuthContext> parsed = jwtTokenService.parseToken(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().getUserId()).isEqualTo("u-123");
        assertThat(parsed.get().getRole()).isEqualTo(Role.SERVICE_DESK_OFFICER);
        assertThat(parsed.get().getDepartmentOrServiceUnit()).isEqualTo("IT Services");
    }

    @Test
    void parseToken_garbageInput_returnsEmptyRatherThanThrowing() {
        assertThat(jwtTokenService.parseToken("not-a-real-jwt")).isEmpty();
    }

    @Test
    void parseToken_tokenSignedWithADifferentKey_isRejected() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-of-at-least-32-bytes");
        otherProperties.setIssuer("usm-g7-test");
        otherProperties.setExpirationMinutes(60);
        JwtTokenService otherService = new JwtTokenService(otherProperties);

        String tokenFromOtherService = otherService.generateToken("u-999", Role.STUDENT, "Faculty of Science");

        assertThat(jwtTokenService.parseToken(tokenFromOtherService)).isEmpty();
    }
}
