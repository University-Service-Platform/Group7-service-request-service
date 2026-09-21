package com.usm.servicerequest.domain;

/**
 * §6 of the guide: "New -> Acknowledged -> Assigned -> In Progress -> Resolved
 * -> Closed, with Rejected / Cancelled / Escalated as side-branches."
 *
 * The guide explicitly flags that these exact names are NOT yet locked with
 * the Tech Lead (see guide §9, question 1). Centralizing every status value
 * in this one enum is the point of §6 and §13's placeholder-and-swap pattern:
 * when the real names come back from the Tech Lead review, this is the only
 * file that needs to change - every switch/if in the codebase should be
 * enum-based (never a raw string literal) so the compiler catches anything a
 * rename affects.
 */
public enum RequestStatus {
    NEW,
    ACKNOWLEDGED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    REJECTED,
    CANCELLED,
    ESCALATED
}
