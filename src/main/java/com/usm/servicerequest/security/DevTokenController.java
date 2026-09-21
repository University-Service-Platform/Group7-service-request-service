package com.usm.servicerequest.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DEV-ONLY convenience so you can hit every protected endpoint locally before
 * Group 5's real login/identity service is ready (guide §7, §13 row 1).
 *
 * Only wired up when `usm.dev-tools.enabled=true` (application-dev.yml).
 * DO NOT enable this profile anywhere but your own machine - it lets anyone
 * mint a token claiming to be any role.
 *
 * Example:
 *   POST /api/dev/token
 *   { "userId": "u123", "role": "SERVICE_DESK_OFFICER", "department": "IT Services" }
 * Response: { "token": "<paste into Authorization: Bearer ...>" }
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(prefix = "usm.dev-tools", name = "enabled", havingValue = "true")
public class DevTokenController {

    private final JwtTokenService jwtTokenService;

    public DevTokenController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/token")
    public Map<String, String> issueDevToken(@RequestBody DevTokenRequest request) {
        Role role = Role.valueOf(request.role().toUpperCase());
        String token = jwtTokenService.generateToken(request.userId(), role, request.department());
        return Map.of("token", token);
    }

    public record DevTokenRequest(String userId, String role, String department) {
    }
}
