package com.mess.service;

import com.mess.domain.Role;
import com.mess.domain.User;
import com.mess.domain.UserFactory;
import com.mess.exception.AuthenticationException;
import com.mess.exception.DuplicateEntryException;
import com.mess.exception.ValidationException;
import com.mess.repository.IUserRepository;

import java.util.Objects;
import java.util.regex.Pattern;

public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,30}");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String LOGIN_FAILED = "Invalid username or password";

    private final IUserRepository users;
    private final PasswordHasher hasher;

    public AuthService(IUserRepository users, PasswordHasher hasher) {
        this.users = Objects.requireNonNull(users);
        this.hasher = Objects.requireNonNull(hasher);
    }

    /** Returns the logged-in user. Unknown user, wrong password and inactive account all give the same error. */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ValidationException("Username and password are required");
        }
        User user = users.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationException(LOGIN_FAILED));
        if (!user.isActive() || !hasher.verify(password, user.getPasswordHash())) {
            throw new AuthenticationException(LOGIN_FAILED);
        }
        return user;
    }

    /** Creates a new account. credentialId is optional (normalized form, e.g. RFID:04A3B2C1). */
    public User register(String username, String fullName, String password, Role role, String credentialId) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ValidationException("Username must be 3-30 letters, digits, '_' or '.'");
        }
        if (fullName == null || fullName.isBlank() || fullName.length() > 100) {
            throw new ValidationException("Full name must be 1-100 characters");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (role == null) {
            throw new ValidationException("Role is required");
        }
        if (credentialId != null && credentialId.isBlank()) {
            throw new ValidationException("Credential id must not be blank");
        }
        if (users.findByUsername(username).isPresent()) {
            throw new ValidationException("Username already taken");
        }
        User toSave = UserFactory.create(role, null, username, fullName.trim(),
                hasher.hash(password), credentialId, true);
        try {
            return users.save(toSave);
        } catch (DuplicateEntryException e) {
            throw new ValidationException("Username or credential already exists");
        }
    }
}
