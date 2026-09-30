package com.usm.servicerequest.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

/**
 * §3.1 of the guide: "enum/lookup: Facility, Equipment, IT, General".
 * The requester picks one on submission; the Service Desk Officer may
 * override it during triage (§4.1 PATCH .../triage).
 */
public enum RequestCategory {
    FACILITY,
    EQUIPMENT,
    IT,
    GENERAL;

    @JsonCreator
    public static RequestCategory fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (RequestCategory cat : values()) {
            if (cat.name().equals(normalized)) {
                return cat;
            }
        }
        throw new IllegalArgumentException("Unknown category: '" + value + "'. Valid categories: "
                + Arrays.toString(values()));
    }
}
