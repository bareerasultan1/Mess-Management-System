package com.giki.mess.repository.persistence;

import com.giki.mess.repository.PersistenceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Access to the embedded SQLite database.
 *
 * SQLite is a file inside the application, so there is no server to pool connections for:
 * each operation opens a short-lived connection with foreign keys switched on, WAL journaling
 * (readers do not block the writer) and a busy timeout (a second writer waits instead of failing).
 * inTransaction() groups several statements so a multi-table update is all-or-nothing.
 */
public final class SqliteDatabase {

    /** A database action that may throw SQLException. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final String jdbcUrl;

    public SqliteDatabase(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl cannot be blank");
        }
        this.jdbcUrl = jdbcUrl;
    }

    public static SqliteDatabase forFile(Path file) {
        return new SqliteDatabase("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** Creates the tables from schema.sql if they do not exist yet. */
    public void initialize() {
        try (InputStream in = SqliteDatabase.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new PersistenceException("schema.sql not found on the classpath", null);
            }
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder sql = new StringBuilder();
            for (String line : script.split("\\R")) {
                if (!line.strip().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
            try (Connection c = open(); Statement st = c.createStatement()) {
                for (String statement : sql.toString().split(";")) {
                    if (!statement.isBlank()) {
                        st.execute(statement);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            throw new PersistenceException("Could not initialise the database", e);
        }
    }

    public Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 10000");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return c;
    }

    /** Runs the work in one transaction: committed on success, rolled back on any failure. */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Runs read-only work on a fresh connection. */
    public <T> T read(SqlWork<T> work) throws SQLException {
        try (Connection c = open()) {
            return work.run(c);
        }
    }
}
