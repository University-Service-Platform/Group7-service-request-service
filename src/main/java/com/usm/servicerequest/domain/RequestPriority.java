package com.usm.servicerequest.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

/**
 * §3.1: "Low / Medium / High / Critical - requester's suggestion; officer can
 * override in triage."
 */
public enum RequestPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonCreator
    public static RequestPriority fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        for (RequestPriority prio : values()) {
            if (prio.name().equals(normalized)) {
                return prio;
            }
        }
        throw new IllegalArgumentException("Unknown priority: '" + value + "'. Valid priorities: "
                + Arrays.toString(values()));
    }
}
