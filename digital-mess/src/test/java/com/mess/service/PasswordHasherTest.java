package com.mess.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void correctPasswordVerifies() {
        String stored = hasher.hash("Mess@1234");
        assertTrue(hasher.verify("Mess@1234", stored));
    }

    @Test
    void wrongPasswordFails() {
        String stored = hasher.hash("Mess@1234");
        assertFalse(hasher.verify("mess@1234", stored));
    }

    @Test
    void sameSaltIsNeverReused() {
        assertNotEquals(hasher.hash("Mess@1234"), hasher.hash("Mess@1234"));
    }

    @Test
    void malformedStoredValueFailsInsteadOfThrowing() {
        assertFalse(hasher.verify("Mess@1234", "not-a-hash"));
        assertFalse(hasher.verify("Mess@1234", "pbkdf2$abc$!!$!!"));
        assertFalse(hasher.verify("Mess@1234", null));
    }
}
