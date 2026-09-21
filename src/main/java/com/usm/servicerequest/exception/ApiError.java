package com.usm.servicerequest.exception;

import java.time.Instant;

/** Consistent error shape for every 4xx/5xx response from this service (guide §9 question 4). */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
