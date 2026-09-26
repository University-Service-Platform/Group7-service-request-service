package com.usm.servicerequest.domain;

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
