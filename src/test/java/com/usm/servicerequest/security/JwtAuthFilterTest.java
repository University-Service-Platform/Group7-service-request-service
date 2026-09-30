package com.usm.servicerequest.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String EXTERNAL_ISSUER = "university-identity-service";
    private static final String INTERNAL_ISSUER = "usm-g7-dev";

    @Mock
    private ExternalTokenValidator externalTokenValidator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtTokenService jwtTokenService;
    private IdentityServiceProperties identityProperties;
    private JwtAuthFilter filter;

    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("internal-dev-secret-key-at-least-32-bytes-long");
        jwtProperties.setIssuer(INTERNAL_ISSUER);
        jwtProperties.setExpirationMinutes(60);
        jwtTokenService = new JwtTokenService(jwtProperties);

        identityProperties = new IdentityServiceProperties();
        identityProperties.setIssuer(EXTERNAL_ISSUER);

        filter = new JwtAuthFilter(jwtTokenService, externalTokenValidator, identityProperties);

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createSignedJwt(String iss) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(iss)
                .subject("usr-test")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }

    @Test
    void doFilter_externalTokenWithMultipleRoles_grantsMultipleAuthoritiesAndNullDept() throws Exception {
        String token = createSignedJwt(EXTERNAL_ISSUER);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        ExternalTokenResult expectedResult = new ExternalTokenResult(
                "usr-servicedesk-001",
                "SDO001",
                "STAFF",
                Set.of(Role.SERVICE_DESK_OFFICER, Role.ADMINISTRATIVE_STAFF)
        );
        when(externalTokenValidator.validate(token)).thenReturn(expectedResult);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthContext.class);

        AuthContext context = (AuthContext) auth.getPrincipal();
        assertThat(context.getUserId()).isEqualTo("usr-servicedesk-001");
        assertThat(context.getDepartmentOrServiceUnit()).isNull();
        assertThat(context.getRoles()).containsExactlyInAnyOrder(Role.SERVICE_DESK_OFFICER, Role.ADMINISTRATIVE_STAFF);

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorityNames).containsExactlyInAnyOrder(
                "ROLE_SERVICE_DESK_OFFICER",
                "ROLE_ADMINISTRATIVE_STAFF"
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_internalToken_routesThroughJwtTokenServiceAndPreservesDept() throws Exception {
        String internalToken = jwtTokenService.generateToken("usr-internal", Role.SERVICE, "SYSTEM");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + internalToken);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthContext.class);

        AuthContext context = (AuthContext) auth.getPrincipal();
        assertThat(context.getUserId()).isEqualTo("usr-internal");
        assertThat(context.getRole()).isEqualTo(Role.SERVICE);
        assertThat(context.getDepartmentOrServiceUnit()).isEqualTo("SYSTEM");

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorityNames).containsExactly("ROLE_SERVICE");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidExternalToken_proceedsUnauthenticated() throws Exception {
        String token = createSignedJwt(EXTERNAL_ISSUER);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(externalTokenValidator.validate(token)).thenThrow(new BadCredentialsException("Token expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_noAuthHeader_proceedsUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
