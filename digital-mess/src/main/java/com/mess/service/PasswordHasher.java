package com.mess.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2-HMAC-SHA256 using only the JDK. Stored format: pbkdf2$iterations$salt$hash (Base64). */
public class PasswordHasher {

    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** A lower iteration count is only meant for fast unit tests. */
    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        Base64.Encoder enc = Base64.getEncoder();
        return "pbkdf2$" + iterations + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash);
    }

    public boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iter);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false; // malformed stored value
        }
    }

    private static byte[] derive(String password, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this JVM", e);
        }
    }
}
