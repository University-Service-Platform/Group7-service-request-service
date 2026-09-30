package com.usm.servicerequest.security;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads `Authorization: Bearer <token>` and branches verification based on the unverified `iss` claim:
 * 1. If `iss` equals "university-identity-service" (Group 5's identity service):
 *    Verifies via ExternalTokenValidator (RS256/JWKS). Grants a Spring Security authority
 *    "ROLE_<X>" for EVERY role held by the user.
 * 2. Otherwise (no iss claim or placeholder "usm-g7-dev"):
 *    Falls through to the existing HS256 JwtTokenService verification path unchanged.
 *
 * Requests with no/invalid token simply proceed unauthenticated; SecurityConfig
 * decides which paths require an authority and which are public.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_EXTERNAL_ISSUER = "university-identity-service";

    private final JwtTokenService jwtTokenService;
    private final ExternalTokenValidator externalTokenValidator;
    private final String externalIssuer;

    public JwtAuthFilter(JwtTokenService jwtTokenService,
                         ExternalTokenValidator externalTokenValidator,
                         IdentityServiceProperties identityProperties) {
        this.jwtTokenService = jwtTokenService;
        this.externalTokenValidator = externalTokenValidator;
        this.externalIssuer = identityProperties != null && identityProperties.getIssuer() != null
                ? identityProperties.getIssuer()
                : DEFAULT_EXTERNAL_ISSUER;
    }

    public JwtAuthFilter(JwtTokenService jwtTokenService) {
        this(jwtTokenService, null, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            String issuer = extractIssuerUnverified(token);

            if (externalTokenValidator != null && (externalIssuer.equals(issuer) || DEFAULT_EXTERNAL_ISSUER.equals(issuer))) {
                // Group 5 external RS256 token verification path
                try {
                    ExternalTokenResult result = externalTokenValidator.validate(token);

                    List<SimpleGrantedAuthority> authorities = result.roles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                            .toList();

                    AuthContext authContext = new AuthContext(result.userId(), result.roles(), null, token);

                    var authentication = new UsernamePasswordAuthenticationToken(authContext, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (AuthenticationException ex) {
                    log.warn("External token authentication failed: {}", ex.getMessage());
                } catch (Exception ex) {
                    log.warn("Unexpected failure validating external token: {}", ex.getMessage());
                }
            } else {
                // Existing internal HS256 verification path, completely unchanged
                Optional<AuthContext> parsed = jwtTokenService.parseToken(token);
                parsed.ifPresent(authContext -> {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authContext.getRole().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(authContext, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractIssuerUnverified(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getIssuer();
        } catch (Exception ex) {
            return null;
        }
    }
}
