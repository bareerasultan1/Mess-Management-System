package com.giki.mess.repository;

import java.util.Map;

/**
 * Where and how to reach the PostgreSQL server.
 *
 * The password is never written in code or committed to Git: it is read from the environment
 * variable MESS_DB_PASSWORD. The other settings have defaults for a local development server.
 *
 *   MESS_DB_URL       default jdbc:postgresql://localhost:5432/mess_db
 *   MESS_DB_USER      default mess_user
 *   MESS_DB_PASSWORD  required
 */
public record DatabaseConfig(String url, String user, String password) {

    public static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/mess_db";
    public static final String DEFAULT_USER = "mess_user";

    public DatabaseConfig {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("Database url must start with jdbc:postgresql:");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("Database user cannot be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("Database password cannot be null");
        }
    }

    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Separate from the no-argument version so tests can pass their own map. */
    public static DatabaseConfig fromEnvironment(Map<String, String> env) {
        String password = env.get("MESS_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                "Environment variable MESS_DB_PASSWORD is not set. It must hold the password of the "
                    + "PostgreSQL user (see README, section 'Run against PostgreSQL').");
        }
        return new DatabaseConfig(
            env.getOrDefault("MESS_DB_URL", DEFAULT_URL),
            env.getOrDefault("MESS_DB_USER", DEFAULT_USER),
            password);
    }

    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=****]";
    }
}
