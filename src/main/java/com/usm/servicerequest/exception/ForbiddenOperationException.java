package com.usm.servicerequest.exception;

/** Thrown for ownership/role checks that Spring Security's @PreAuthorize can't express (e.g. BR-10). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
