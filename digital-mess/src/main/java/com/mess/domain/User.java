package com.mess.domain;

/**
 * Base class for every person who can log in. Subclasses only differ in role
 * and in whether they may scan into the mess, so any User can be used
 * wherever a User is expected (Liskov Substitution).
 */
public abstract class User {

    private final Long id;            // null until the user is saved
    private final String username;
    private final String fullName;
    private final String passwordHash;
    private final String credentialId; // normalized RFID / biometric id, may be null
    private final boolean active;

    protected User(Long id, String username, String fullName, String passwordHash,
                   String credentialId, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.credentialId = credentialId;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public String getCredentialId() { return credentialId; }
    public boolean isActive() { return active; }

    public abstract Role getRole();

    /** Only students are tracked by the mess entry/exit terminals. */
    public abstract boolean canScanIntoMess();
}
