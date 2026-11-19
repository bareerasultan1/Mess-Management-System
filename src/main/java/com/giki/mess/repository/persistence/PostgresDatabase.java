package com.giki.mess.repository.persistence;

import com.giki.mess.repository.DatabaseConfig;
import com.giki.mess.repository.PersistenceException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Access to the PostgreSQL database.
 *
 * <p>Opening a PostgreSQL connection is slow, so a small connection pool (HikariCP) keeps a few
 * connections open and lends them out; closing a lent connection simply returns it to the pool.
 * inTransaction() groups several statements so a multi-table update is all-or-nothing.
 */
public final class PostgresDatabase implements AutoCloseable {

    /** A database action that may throw SQLException. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final HikariDataSource pool;

    private PostgresDatabase(HikariDataSource pool) {
        this.pool = pool;
    }

    /**
     * Connects to the server described by the config.
     *
     * @throws PersistenceException with a clear message if the server cannot be reached or the
     *         user name / password / database name is wrong
     */
    public static PostgresDatabase connect(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.url());
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(10);
        hikari.setConnectionTimeout(5_000);
        hikari.setPoolName("mess-db-pool");
        try {
            return new PostgresDatabase(new HikariDataSource(hikari));   // connects immediately
        } catch (RuntimeException e) {
            throw new PersistenceException("Could not connect to the database at " + config.url()
                + " as user '" + config.user() + "'. Check that PostgreSQL is running and that"
                + " MESS_DB_URL, MESS_DB_USER and MESS_DB_PASSWORD are correct.", e);
        }
    }

    /** Creates the tables from schema.sql if they do not exist yet (safe to run at every start). */
    public void initialize() {
        try (InputStream in = PostgresDatabase.class.getResourceAsStream("/schema.sql")) {
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
            inTransaction(c -> {
                try (Statement st = c.createStatement()) {
                    for (String statement : sql.toString().split(";")) {
                        if (!statement.isBlank()) {
                            st.execute(statement);
                        }
                    }
                }
                return null;
            });
        } catch (IOException | SQLException e) {
            throw new PersistenceException("Could not initialise the database", e);
        }
    }

    /** Runs the work in one transaction: committed on success, rolled back on any failure. */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection c = pool.getConnection()) {
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

    /** Runs read-only work on a pooled connection. */
    public <T> T read(SqlWork<T> work) throws SQLException {
        try (Connection c = pool.getConnection()) {
            return work.run(c);
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
