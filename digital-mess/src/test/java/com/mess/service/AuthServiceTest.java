package com.mess.service;

import com.mess.domain.Role;
import com.mess.domain.User;
import com.mess.domain.UserFactory;
import com.mess.exception.AuthenticationException;
import com.mess.exception.ValidationException;
import com.mess.support.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    private InMemoryUserRepository users;
    private PasswordHasher hasher;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        hasher = new PasswordHasher(1000);
        auth = new AuthService(users, hasher);
    }

    @Test
    void registeredUserCanLogIn() {
        auth.register("ali", "Ali Khan", "Mess@1234", Role.STUDENT, "RFID:04A3B2C1");
        User user = auth.login("ali", "Mess@1234");
        assertEquals("Ali Khan", user.getFullName());
        assertEquals(Role.STUDENT, user.getRole());
        assertNotNull(user.getId());
    }

    @Test
    void passwordIsStoredHashedNotPlain() {
        User saved = auth.register("ali", "Ali Khan", "Mess@1234", Role.STUDENT, null);
        assertNotEquals("Mess@1234", saved.getPasswordHash());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        auth.register("ali", "Ali Khan", "Mess@1234", Role.STUDENT, null);
        AuthenticationException wrongPassword =
                assertThrows(AuthenticationException.class, () -> auth.login("ali", "wrong-pass"));
        AuthenticationException unknownUser =
                assertThrows(AuthenticationException.class, () -> auth.login("nobody", "Mess@1234"));
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    @Test
    void inactiveAccountCannotLogIn() {
        users.save(UserFactory.create(Role.STUDENT, null, "old", "Old Student",
                hasher.hash("Mess@1234"), null, false));
        assertThrows(AuthenticationException.class, () -> auth.login("old", "Mess@1234"));
    }

    @Test
    void blankLoginInputIsRejectedAsInvalid() {
        assertThrows(ValidationException.class, () -> auth.login("  ", "Mess@1234"));
        assertThrows(ValidationException.class, () -> auth.login("ali", ""));
        assertThrows(ValidationException.class, () -> auth.login(null, null));
    }

    @Test
    void registerRejectsBadInput() {
        assertThrows(ValidationException.class,
                () -> auth.register("a", "Ali Khan", "Mess@1234", Role.STUDENT, null));       // username too short
        assertThrows(ValidationException.class,
                () -> auth.register("ali", " ", "Mess@1234", Role.STUDENT, null));            // blank name
        assertThrows(ValidationException.class,
                () -> auth.register("ali", "Ali Khan", "short", Role.STUDENT, null));         // short password
        assertThrows(ValidationException.class,
                () -> auth.register("ali", "Ali Khan", "Mess@1234", null, null));             // no role
    }

    @Test
    void duplicateUsernameIsRejected() {
        auth.register("ali", "Ali Khan", "Mess@1234", Role.STUDENT, null);
        assertThrows(ValidationException.class,
                () -> auth.register("ali", "Another Ali", "Mess@1234", Role.STUDENT, null));
    }
}
