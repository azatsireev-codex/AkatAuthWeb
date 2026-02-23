package net.akat.auth.repository;

import com.google.inject.Singleton;
import net.akat.auth.model.PendingRegistration;

import java.sql.*;
import java.time.Instant;

@Singleton
public class PendingRegistrationRepository {
    private final Connection connection;

    public PendingRegistrationRepository(Connection connection) {
        this.connection = connection;
        createTable();
        createIndexes(); // Создаем индексы отдельно
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS pending_registrations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nickname TEXT UNIQUE NOT NULL," +
                "email TEXT NOT NULL," +
                "ip_address TEXT NOT NULL," +
                "created_at TIMESTAMP NOT NULL," +
                "expires_at TIMESTAMP NOT NULL)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Таблица pending_registrations создана/проверена");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create pending_registrations table", e);
        }
    }

    private void createIndexes() {
        try (Statement stmt = connection.createStatement()) {
            // Создаем индекс для nickname (хотя UNIQUE уже создает индекс)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_nickname ON pending_registrations (nickname)");

            // Создаем индекс для expires_at для быстрой очистки
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_expires ON pending_registrations (expires_at)");

            System.out.println("Индексы для pending_registrations созданы/проверены");
        } catch (SQLException e) {
            System.err.println("Ошибка создания индексов (возможно уже существуют): " + e.getMessage());
            // Не бросаем исключение - индексы не критичны
        }
    }

    public void save(PendingRegistration pendingRegistration) {
        String sql = "INSERT OR REPLACE INTO pending_registrations " +
                "(nickname, email, ip_address, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, pendingRegistration.getNickname());
            stmt.setString(2, pendingRegistration.getEmail());
            stmt.setString(3, pendingRegistration.getIpAddress());
            stmt.setTimestamp(4, Timestamp.from(pendingRegistration.getCreatedAt()));
            stmt.setTimestamp(5, Timestamp.from(pendingRegistration.getExpiresAt()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save pending registration", e);
        }
    }

    public PendingRegistration findByNickname(String nickname) {
        String sql = "SELECT nickname, email, ip_address, created_at, expires_at " +
                "FROM pending_registrations WHERE nickname = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PendingRegistration.Builder()
                        .nickname(rs.getString("nickname"))
                        .email(rs.getString("email"))
                        .ipAddress(rs.getString("ip_address"))
                        .createdAt(rs.getTimestamp("created_at").toInstant())
                        .expiresAt(rs.getTimestamp("expires_at").toInstant())
                        .build();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending registration", e);
        }
    }

    public void delete(String nickname) {
        String sql = "DELETE FROM pending_registrations WHERE nickname = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete pending registration", e);
        }
    }

    /**
     * Очистка запросов на регистрацию старше указанного времени
     * @param hours количество часов
     * @return количество удаленных записей
     */
    public int cleanupOlderThanHours(int hours) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM pending_registrations WHERE created_at < ?")) {

            long cutoffTime = System.currentTimeMillis() - (hours * 3600L * 1000L);
            stmt.setLong(1, cutoffTime);

            int deleted = stmt.executeUpdate();
            return deleted;

        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Очистка истекших регистраций (по expires_at)
     */
    public int cleanupExpired() {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM pending_registrations WHERE expires_at <= ?")) {
            stmt.setLong(1, System.currentTimeMillis());
            return stmt.executeUpdate();
        } catch (Exception e) {
            return 0;
        }
    }
}
