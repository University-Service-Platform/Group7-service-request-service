package com.usm.servicerequest.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Parsed, validated JWT claims for the current request. Controllers and
 * services depend on THIS object, never on raw JWT fields.
 *
 * Supports both single-role internal/dev tokens and multi-role Group 5 external tokens.
 */
public class AuthContext {

    private final String userId;
    // Primary role for legacy single-role code paths — authorization should rely on the granted authorities, not this field.
    private final Role role;
    private final Set<Role> roles;
    private final String departmentOrServiceUnit;

    /**
     * Legacy single-role constructor used by the internal HS256 and dev token paths.
     */
    public AuthContext(String userId, Role role, String departmentOrServiceUnit) {
        this.userId = userId;
        this.role = role;
        this.roles = role != null ? Collections.singleton(role) : Collections.emptySet();
        this.departmentOrServiceUnit = departmentOrServiceUnit;
    }

    /**
     * Multi-role constructor used when parsing Group 5 tokens carrying multiple roles.
     * Note: Group 5 identity tokens do not carry department or service_unit claims;
     * departmentOrServiceUnit should be passed as null.
     */
    public AuthContext(String userId, Set<Role> roles, String departmentOrServiceUnit) {
        this.userId = userId;
        this.roles = roles != null ? Collections.unmodifiableSet(roles) : Collections.emptySet();
        // Primary role for legacy single-role code paths — authorization should rely on the granted authorities, not this field.
        this.role = this.roles.isEmpty() ? null : this.roles.iterator().next();
        this.departmentOrServiceUnit = departmentOrServiceUnit;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * Returns the primary role.
     * Primary role for legacy single-role code paths — authorization should rely on the granted authorities, not this field.
     */
    public Role getRole() {
        return role;
    }

    /**
     * Returns all roles held by the user.
     */
    public Set<Role> getRoles() {
        return roles;
    }

    public boolean hasRole(Role roleToCheck) {
        return roles.contains(roleToCheck) || Objects.equals(this.role, roleToCheck);
    }

    public String getDepartmentOrServiceUnit() {
        return departmentOrServiceUnit;
    }

    public boolean isServiceDeskOfficer() {
        return hasRole(Role.SERVICE_DESK_OFFICER);
    }

    public boolean isServiceCall() {
        return hasRole(Role.SERVICE);
    }

    @Override
    public String toString() {
        return "AuthContext{" +
                "userId='" + userId + '\'' +
                ", role=" + role +
                ", roles=" + roles +
                ", dept='" + departmentOrServiceUnit + '\'' +
                '}';
    }
}
