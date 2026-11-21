package com.mess.repository.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens PostgreSQL connections. Settings come from environment variables. */
public class Database implements ConnectionProvider {

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** DB_URL (default jdbc:postgresql://localhost:5432/mess_db), DB_USER (default postgres), DB_PASSWORD. */
    public static Database fromEnv() {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/mess_db");
        String user = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        return new Database(url, user, password);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
