package com.usm.servicerequest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Top-level response wrapper returned by Group 6's facility/resource validation endpoint
 * (GET /api/resources/code/{code}/validate).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacilityValidationResponse(
        boolean success,
        String message,
        FacilityValidationResult data,
        String timestamp
) {
}
