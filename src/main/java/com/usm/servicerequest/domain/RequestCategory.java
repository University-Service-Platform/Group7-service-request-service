package com.usm.servicerequest.domain;

/**
 * §3.1 of the guide: "enum/lookup: Facility, Equipment, IT, General".
 * The requester picks one on submission; the Service Desk Officer may
 * override it during triage (§4.1 PATCH .../triage).
 */
public enum RequestCategory {
    FACILITY,
    EQUIPMENT,
    IT,
    GENERAL
}
