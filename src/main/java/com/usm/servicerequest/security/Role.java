package com.usm.servicerequest.security;

/**
 * Roles from Group 5's identity-access-service and this project's internal service role.
 *
 * Real Group 5 user roles:
 * - STUDENT
 * - ACADEMIC_STAFF
 * - ADMINISTRATIVE_STAFF (renamed from ADMIN_STAFF to match Group 5's contract)
 * - SERVICE_DESK_OFFICER
 * - TECHNICIAN
 * - ADMIN
 * - STAFF
 * - RESOURCE_MANAGER
 * - EVENT_ORGANIZER
 *
 * Internal role:
 * - SERVICE: Internal placeholder for service-to-service calls (work-order-service
 *   calling back into this service).
 */
public enum Role {
    STUDENT,
    ACADEMIC_STAFF,
    ADMINISTRATIVE_STAFF,
    SERVICE_DESK_OFFICER,
    TECHNICIAN,
    ADMIN,
    STAFF,
    RESOURCE_MANAGER,
    EVENT_ORGANIZER,
    SERVICE
}
