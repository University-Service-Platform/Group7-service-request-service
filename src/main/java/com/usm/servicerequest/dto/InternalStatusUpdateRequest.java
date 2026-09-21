package com.usm.servicerequest.dto;

import com.usm.servicerequest.domain.RequestStatus;
import jakarta.validation.constraints.NotNull;

/**
 * PATCH /api/service-requests/{id}/status (internal) - guide §4.1: "Called by
 * work-order-service to push Assigned/Resolved status + timestamp... not
 * exposed to the frontend/gateway." Only RequestStatus.ASSIGNED,
 * RequestStatus.IN_PROGRESS and RequestStatus.RESOLVED are accepted from this
 * endpoint (see ServiceRequestServiceImpl.applyInternalStatusUpdate) - every
 * other transition happens through the requester/officer-facing endpoints
 * above, which is what keeps BR-09 (status + timestamp together) enforceable.
 */
public record InternalStatusUpdateRequest(
        @NotNull(message = "status is required")
        RequestStatus status
) {
}
