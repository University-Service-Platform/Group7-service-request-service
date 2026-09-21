package com.usm.servicerequest.security;

/**
 * Roles as claimed in the JWT `role` claim (see JwtTokenService and guide §7).
 * Exact claim NAME still needs confirming with Group 5 (guide §9/§14 item 5);
 * the set of role VALUES below comes from who-can-call-it in guide §4.1/§4.2.
 *
 * SERVICE is not one of Group 5's real user roles - it is this project's own
 * placeholder for service-to-service calls (work-order-service calling back
 * into this service). See README "Service-to-service auth" for why, and
 * guide §13 for the general placeholder-and-swap pattern this follows.
 */
public enum Role {
    STUDENT,
    ACADEMIC_STAFF,
    ADMIN_STAFF,
    SERVICE_DESK_OFFICER,
    TECHNICIAN,
    SERVICE
}
