package com.usm.servicerequest.dto;

import jakarta.validation.constraints.NotBlank;

/** BR-04 (guide §4.1) - escalation always moves responsibility, so the new unit is mandatory. */
public record EscalateRequest(
        @NotBlank(message = "responsibleServiceUnit is required to escalate")
        String responsibleServiceUnit
) {
}
