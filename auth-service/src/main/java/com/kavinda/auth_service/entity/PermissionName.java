package com.kavinda.auth_service.entity;

public enum PermissionName {

    PROFILE_READ("profile:read"),
    PROFILE_UPDATE("profile:update"),

    PROJECT_CREATE("project:create"),
    PROJECT_READ("project:read"),
    PROJECT_UPDATE("project:update"),
    PROJECT_DELETE("project:delete"),

    ANALYTICS_READ("analytics:read"),

    USER_READ("user:read"),
    USER_UPDATE("user:update"),
    USER_DISABLE("user:disable"),

    ROLE_READ("role:read"),
    ROLE_ASSIGN("role:assign"),
    ROLE_REMOVE("role:remove");

    private final String authority;

    PermissionName(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

}
