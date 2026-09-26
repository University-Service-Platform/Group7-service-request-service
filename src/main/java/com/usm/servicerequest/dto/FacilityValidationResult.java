package com.usm.servicerequest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record FacilityValidationResult(
        Long resourceId,
        String resourceCode,
        Long facilityId,
        boolean exists,
        boolean active,
        boolean available,
        Integer capacity,
        boolean approvalRequired,
        String operatingHoursStart,
        String operatingHoursEnd,
        boolean validForReservation,
        String message
) {
    /**
     * Fallback result when the facility validation service is unreachable or encounters an error.
     */
    public static FacilityValidationResult unreachable() {
        return new FacilityValidationResult(
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                false,
                "Facility validation service unreachable"
        );
    }
}
