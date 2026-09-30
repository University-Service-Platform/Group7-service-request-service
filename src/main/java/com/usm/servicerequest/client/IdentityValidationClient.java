package com.usm.servicerequest.client;

import com.usm.servicerequest.dto.UserValidationResponse;
import com.usm.servicerequest.exception.ForbiddenOperationException;
import com.usm.servicerequest.security.IdentityServiceProperties;
import com.usm.servicerequest.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Client for Group 5's identity-access-service live user validation endpoint
 * (GET /api/v1/validation/users/{user_id}?require_active=true&required_role={ROLE}).
 *
 * Enforces live re-validation before sensitive state-changing operations (triage, reject, escalate).
 * Fails closed on any error (403, 404, unreachable/timeout, or invalid response) by throwing
 * ForbiddenOperationException.
 */
@Component
public class IdentityValidationClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public IdentityValidationClient(RestTemplateBuilder restTemplateBuilder, IdentityServiceProperties properties) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(60))
                .build(),
                properties != null ? properties.getBaseUrl() : "http://localhost:8001");
    }

    public IdentityValidationClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * Re-validates a user live against Group 5's identity service before sensitive actions.
     *
     * @param userId the user ID to validate
     * @param requiredRole the role required for the operation
     * @param bearerToken the caller's raw bearer token to forward
     * @throws ForbiddenOperationException if the user is inactive, unauthorized, not found, or if the service is unreachable
     */
    public void validateUser(String userId, Role requiredRole, String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            log.warn("Live identity validation failed: missing bearer token for userId '{}'", userId);
            throw new ForbiddenOperationException("Missing bearer token for identity re-validation");
        }
        if (userId == null || userId.isBlank()) {
            log.warn("Live identity validation failed: missing userId");
            throw new ForbiddenOperationException("Missing userId for identity re-validation");
        }

        String roleName = requiredRole != null ? requiredRole.name() : "";
        String url = baseUrl + "/api/v1/validation/users/{userId}?require_active=true&required_role={requiredRole}";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken.trim());
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<UserValidationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    UserValidationResponse.class,
                    userId.trim(),
                    roleName
            );

            UserValidationResponse body = response.getBody();
            if (body == null || !body.isValidUser() || !body.isAuthorizedUser()) {
                String msg = body != null && body.message() != null ? body.message() : "User is not active or not authorized";
                log.warn("Identity re-validation rejected for user '{}' with role '{}': {}", userId, roleName, msg);
                throw new ForbiddenOperationException("User re-validation failed: " + msg);
            }

            log.info("Live identity re-validation succeeded for user '{}' with role '{}'", userId, roleName);
        } catch (ForbiddenOperationException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            log.warn("Identity service returned HTTP {} during re-validation for user '{}': {}",
                    ex.getStatusCode(), userId, ex.getResponseBodyAsString());
            throw new ForbiddenOperationException("Identity service re-validation refused: HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.error("Identity service unreachable or timed out during re-validation for user '{}': {}",
                    userId, ex.getMessage());
            throw new ForbiddenOperationException("Identity service unreachable or timed out during re-validation");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
