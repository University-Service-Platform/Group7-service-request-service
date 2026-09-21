package com.usm.servicerequest.dto;

import jakarta.validation.constraints.Size;

/** US-12, FR-11 (guide §4.1) - original requester only, after the request has been resolved. */
public record ConfirmRequest(
        @Size(max = 500)
        String confirmationFeedback
) {
}
