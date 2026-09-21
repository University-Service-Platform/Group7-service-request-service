package com.usm.servicerequest.security;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small convenience wrapper so controllers/services can write
 * `AuthContextHolder.require()` instead of digging through
 * SecurityContextHolder + casting the principal every time.
 */
public final class AuthContextHolder {

    private AuthContextHolder() {
    }

    public static AuthContext require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof AuthContext authContext)) {
            throw new IllegalStateException("No authenticated AuthContext on the security context - "
                    + "is this endpoint missing from SecurityConfig's permitted list, or did the JWT filter not run?");
        }
        return authContext;
    }
}
