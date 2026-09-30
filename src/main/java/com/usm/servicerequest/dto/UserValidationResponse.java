package com.usm.servicerequest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload returned by Group 5's identity validation endpoint
 * (GET /api/v1/validation/users/{user_id}?require_active=true&required_role={ROLE}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserValidationResponse(
        @JsonProperty("is_valid") @JsonAlias("isValid") Boolean isValid,
        @JsonProperty("is_authorized") @JsonAlias("isAuthorized") Boolean isAuthorized,
        String message,
        UserValidationResponse data
) {
    public boolean isValidUser() {
        if (data != null && data.isValid != null) {
            return data.isValid;
        }
        return isValid != null && isValid;
    }

    public boolean isAuthorizedUser() {
        if (data != null && data.isAuthorized != null) {
            return data.isAuthorized;
        }
        return isAuthorized != null && isAuthorized;
    }
}
