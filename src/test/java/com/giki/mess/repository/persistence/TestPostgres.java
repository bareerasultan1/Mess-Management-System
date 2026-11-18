package com.giki.mess.repository.persistence;

import com.giki.mess.repository.DatabaseConfig;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test helper: a REAL PostgreSQL server started inside the test run (embedded), so the tests
 * need no installation, no password and no Docker, on any machine and in CI.
 * One server is started per test JVM; every test gets a clean, empty database schema.
 * The application itself always connects to a normal PostgreSQL server (see DatabaseConfig).
 */
public final class TestPostgres {

    private static EmbeddedPostgres server;

    private TestPostgres() {
    }

    /** Wipes the schema and returns the connection settings of the (now empty) database. */
    public static synchronized DatabaseConfig freshDatabase() {
        try {
            if (server == null) {
                server = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        server.close();
                    } catch (IOException ignored) {
                        // nothing useful to do while the JVM is exiting
                    }
                }));
            }
            try (Connection c = server.getPostgresDatabase().getConnection();
                 Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA public CASCADE");
                st.execute("CREATE SCHEMA public");
            }
            return new DatabaseConfig(server.getJdbcUrl("postgres", "postgres"), "postgres", "");
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Could not start the embedded test PostgreSQL", e);
        }
    }
}
