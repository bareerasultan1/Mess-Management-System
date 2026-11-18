package com.mess.domain;

public class StudentUser extends User {

    public StudentUser(Long id, String username, String fullName, String passwordHash,
                       String credentialId, boolean active) {
        super(id, username, fullName, passwordHash, credentialId, active);
    }

    @Override
    public Role getRole() { return Role.STUDENT; }

    @Override
    public boolean canScanIntoMess() { return true; }
}
