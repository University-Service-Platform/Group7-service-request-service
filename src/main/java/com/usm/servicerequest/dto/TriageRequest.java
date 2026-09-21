package com.usm.servicerequest.dto;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestPriority;
import jakarta.validation.constraints.NotBlank;

/** US-04/05, FR-03/04, API-03, BR-03 (guide §4.1) - Service Desk Officer only. */
public record TriageRequest(
        RequestCategory category,
        RequestPriority priority,
        @NotBlank(message = "responsibleServiceUnit is required for triage")
        String responsibleServiceUnit
) {
}
