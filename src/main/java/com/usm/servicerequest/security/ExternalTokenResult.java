package com.usm.servicerequest.security;

import java.util.Set;

/**
 * Result of successfully validating an RS256 token issued by Group 5's identity-access-service.
 *
 * @param userId sub claim (e.g. "usr-servicedesk-001")
 * @param universityId university_id claim (e.g. "SDO001")
 * @param accountType account_type claim ("STUDENT" / "STAFF")
 * @param roles mapped Role enum values from the token's roles JSON array
 */
public record ExternalTokenResult(
        String userId,
        String universityId,
        String accountType,
        Set<Role> roles
) {
}
