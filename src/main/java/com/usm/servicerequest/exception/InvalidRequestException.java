package com.usm.servicerequest.exception;

/** Thrown for business-rule violations that must be a 400, not a silent null (e.g. BR-05). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
