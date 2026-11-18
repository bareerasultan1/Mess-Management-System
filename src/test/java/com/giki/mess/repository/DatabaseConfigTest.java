package com.giki.mess.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DatabaseConfig (connection settings come from the environment, never from code)")
class DatabaseConfigTest {

    @Test
    @DisplayName("defaults point at a local PostgreSQL named mess_db with user mess_user")
    void defaults() {
        DatabaseConfig c = DatabaseConfig.fromEnvironment(Map.of("MESS_DB_PASSWORD", "secret"));

        assertThat(c.url()).isEqualTo("jdbc:postgresql://localhost:5432/mess_db");
        assertThat(c.user()).isEqualTo("mess_user");
        assertThat(c.password()).isEqualTo("secret");
    }

    @Test
    @DisplayName("every setting can be overridden through environment variables")
    void overrides() {
        DatabaseConfig c = DatabaseConfig.fromEnvironment(Map.of(
            "MESS_DB_URL", "jdbc:postgresql://db.example:5433/other",
            "MESS_DB_USER", "alice",
            "MESS_DB_PASSWORD", "pw"));

        assertThat(c.url()).isEqualTo("jdbc:postgresql://db.example:5433/other");
        assertThat(c.user()).isEqualTo("alice");
    }

    @Test
    @DisplayName("a missing password is reported by name, not silently ignored")
    void missingPassword() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DatabaseConfig.fromEnvironment(Map.of()))
            .withMessageContaining("MESS_DB_PASSWORD");
    }

    @Test
    @DisplayName("a non-PostgreSQL url is rejected")
    void wrongUrl() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> new DatabaseConfig("jdbc:mysql://localhost/x", "u", "p"));
    }

    @Test
    @DisplayName("toString never prints the password")
    void passwordHidden() {
        DatabaseConfig c = new DatabaseConfig("jdbc:postgresql://localhost/x", "u", "topsecret");

        assertThat(c.toString()).doesNotContain("topsecret");
    }
}
