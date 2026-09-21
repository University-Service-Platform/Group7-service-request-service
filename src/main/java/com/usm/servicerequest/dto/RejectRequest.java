package com.usm.servicerequest.dto;

import jakarta.validation.constraints.NotBlank;

/** US-11, FR-10, BR-05 (guide §4.1/§5) - reject without a reason must be a 400, not a silent null. */
public record RejectRequest(
        @NotBlank(message = "rejectionReason is required")
        String rejectionReason
) {
}
