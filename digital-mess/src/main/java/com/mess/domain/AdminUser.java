package com.mess.domain;

public class AdminUser extends User {

    public AdminUser(Long id, String username, String fullName, String passwordHash,
                     String credentialId, boolean active) {
        super(id, username, fullName, passwordHash, credentialId, active);
    }

    @Override
    public Role getRole() { return Role.ADMIN; }

    @Override
    public boolean canScanIntoMess() { return false; }
}
