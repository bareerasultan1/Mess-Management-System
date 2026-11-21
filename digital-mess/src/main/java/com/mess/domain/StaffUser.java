package com.mess.domain;

public class StaffUser extends User {

    public StaffUser(Long id, String username, String fullName, String passwordHash,
                     String credentialId, boolean active) {
        super(id, username, fullName, passwordHash, credentialId, active);
    }

    @Override
    public Role getRole() { return Role.STAFF; }

    @Override
    public boolean canScanIntoMess() { return false; }
}
