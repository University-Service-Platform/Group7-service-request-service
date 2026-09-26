package com.usm.servicerequest.dto;

import com.usm.servicerequest.domain.RequestStatus;
import jakarta.validation.constraints.NotNull;


public record InternalStatusUpdateRequest(
        @NotNull(message = "status is required")
        RequestStatus status
) {
}
