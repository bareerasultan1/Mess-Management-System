package com.mess.repository.jdbc;

import com.mess.domain.Role;
import com.mess.domain.User;
import com.mess.domain.UserFactory;
import com.mess.exception.DataAccessException;
import com.mess.repository.IUserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Stateless: a new connection is opened and closed per call, so one instance
 * can be shared safely between threads.
 */
public class JdbcUserRepository implements IUserRepository {

    private static final String COLUMNS =
            "id, username, full_name, password_hash, role, credential_id, active";

    private final ConnectionProvider connections;

    public JdbcUserRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE username = ?",
                username, "find user by username");
    }

    @Override
    public Optional<User> findByCredentialId(String credentialId) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE credential_id = ?",
                credentialId, "find user by credential");
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (username, full_name, password_hash, role, credential_id, active) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getCredentialId());
            ps.setBoolean(6, user.isActive());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                return UserFactory.create(user.getRole(), id, user.getUsername(), user.getFullName(),
                        user.getPasswordHash(), user.getCredentialId(), user.isActive());
            }
        } catch (SQLException e) {
            throw SqlErrors.translate("save user", e);
        }
    }

    private Optional<User> findOne(String sql, String param, String action) {
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrors.translate(action, e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return UserFactory.create(
                Role.valueOf(rs.getString("role")),
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                rs.getString("credential_id"),
                rs.getBoolean("active"));
    }
}
