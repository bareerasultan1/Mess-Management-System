package com.mess.repository.jdbc;

import com.mess.domain.MealType;
import com.mess.domain.ScanLog;
import com.mess.domain.ScanType;
import com.mess.repository.IScanLogRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Stateless, like JdbcUserRepository: one connection per call. */
public class JdbcScanLogRepository implements IScanLogRepository {

    private final ConnectionProvider connections;

    public JdbcScanLogRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public ScanLog save(ScanLog log) {
        String sql = "INSERT INTO scan_logs (user_id, scan_type, meal_type, scanned_at, scan_date, device_id) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, log.userId());
            ps.setString(2, log.scanType().name());
            ps.setString(3, log.mealType().name());
            ps.setObject(4, OffsetDateTime.ofInstant(log.scannedAt(), ZoneOffset.UTC));
            ps.setObject(5, log.scanDate());
            ps.setString(6, log.deviceId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return log.withId(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw SqlErrors.translate("save scan log", e);
        }
    }

    @Override
    public Optional<ScanLog> findLastScan(long userId) {
        String sql = "SELECT id, user_id, scan_type, meal_type, scanned_at, scan_date, device_id "
                + "FROM scan_logs WHERE user_id = ? ORDER BY scanned_at DESC, id DESC LIMIT 1";
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrors.translate("find last scan", e);
        }
    }

    @Override
    public boolean hasEntry(long userId, MealType mealType, LocalDate scanDate) {
        String sql = "SELECT EXISTS (SELECT 1 FROM scan_logs "
                + "WHERE user_id = ? AND meal_type = ? AND scan_date = ? AND scan_type = 'IN')";
        try (Connection c = connections.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, mealType.name());
            ps.setObject(3, scanDate);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw SqlErrors.translate("check existing entry", e);
        }
    }

    private ScanLog map(ResultSet rs) throws SQLException {
        return new ScanLog(
                rs.getLong("id"),
                rs.getLong("user_id"),
                ScanType.valueOf(rs.getString("scan_type")),
                MealType.valueOf(rs.getString("meal_type")),
                rs.getObject("scanned_at", OffsetDateTime.class).toInstant(),
                rs.getObject("scan_date", LocalDate.class),
                rs.getString("device_id"));
    }
}
