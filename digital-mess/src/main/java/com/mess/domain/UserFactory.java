package com.mess.domain;

/** Single place that turns a role into the matching User subclass. */
public final class UserFactory {

    private UserFactory() { }

    public static User create(Role role, Long id, String username, String fullName,
                              String passwordHash, String credentialId, boolean active) {
        return switch (role) {
            case STUDENT -> new StudentUser(id, username, fullName, passwordHash, credentialId, active);
            case STAFF -> new StaffUser(id, username, fullName, passwordHash, credentialId, active);
            case ADMIN -> new AdminUser(id, username, fullName, passwordHash, credentialId, active);
        };
    }
}
