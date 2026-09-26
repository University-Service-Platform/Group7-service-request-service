package com.usm.servicerequest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FacilityValidationResponse(
        boolean success,
        String message,
        FacilityValidationResult data,
        String timestamp
) {
}
