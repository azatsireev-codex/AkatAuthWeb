package net.akat.auth.repository;

import org.slf4j.Logger;

import java.sql.*;

public class IpConfirmationRepository {
    private final Connection connection;
    private final Logger logger;

    public IpConfirmationRepository(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS ip_confirmations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nickname TEXT NOT NULL UNIQUE," + // ✅ UNIQUE - только одна запись на игрока
                "new_ip TEXT NOT NULL," +
                "original_ip TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "expires_at INTEGER NOT NULL)"; // ❌ Убрали confirmed

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            logger.info("✅ Таблица ip_confirmations создана");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ip_confirmations table", e);
        }
    }

    /**
     * Создаёт или обновляет подтверждение нового IP
     * Если запись уже есть - обновляет new_ip и сбрасывает таймер
     */
    public void createOrUpdateConfirmation(String nickname, String newIp, String originalIp) {
        cleanupExpired(); // Сначала чистим истекшие

        if (hasActiveConfirmation(nickname)) {
            // ✅ Обновляем существующую запись
            updateConfirmationIp(nickname, newIp);
            logger.info("🔄 Обновлено подтверждение IP для {}: {}", nickname, newIp);
        } else {
            // ✅ Создаём новую запись
            createNewConfirmation(nickname, newIp, originalIp);
        }
    }

    private void createNewConfirmation(String nickname, String newIp, String originalIp) {
        String sql = "INSERT INTO ip_confirmations (nickname, new_ip, original_ip, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        long now = System.currentTimeMillis();
        long expiresAt = now + (5 * 60 * 1000); // +5 минут

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.setString(2, newIp);
            stmt.setString(3, originalIp);
            stmt.setLong(4, now);
            stmt.setLong(5, expiresAt);
            stmt.executeUpdate();

            logger.info("✅ Создано подтверждение IP для {}: {} → {}", nickname, originalIp, newIp);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create IP confirmation", e);
        }
    }

    private void updateConfirmationIp(String nickname, String newIp) {
        String sql = "UPDATE ip_confirmations SET new_ip = ?, expires_at = ? " +
                "WHERE nickname = ? AND expires_at > ?";

        long newExpiresAt = System.currentTimeMillis() + (5 * 60 * 1000);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newIp);
            stmt.setLong(2, newExpiresAt);
            stmt.setString(3, nickname);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();

            logger.debug("Обновлён new_ip для {}: {}", nickname, newIp);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update IP confirmation", e);
        }
    }

    /**
     * Проверяет, есть ли активное подтверждение для nickname
     */
    public boolean hasActiveConfirmation(String nickname) {
        String sql = "SELECT 1 FROM ip_confirmations WHERE nickname = ? AND expires_at > ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.setLong(2, System.currentTimeMillis());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            logger.error("Ошибка проверки подтверждения IP", e);
            return false;
        }
    }

    /**
     * Получает текущий new_ip из активного подтверждения
     */
    public String getCurrentNewIp(String nickname) {
        String sql = "SELECT new_ip FROM ip_confirmations WHERE nickname = ? AND expires_at > ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.setLong(2, System.currentTimeMillis());

            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("new_ip") : null;
        } catch (SQLException e) {
            logger.error("Ошибка получения new_ip", e);
            return null;
        }
    }

    /**
     * Удаляет подтверждение для игрока (когда сайт подтвердил)
     */
    public void deleteConfirmation(String nickname) {
        String sql = "DELETE FROM ip_confirmations WHERE nickname = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.executeUpdate();
            logger.debug("Удалено подтверждение IP для {}", nickname);
        } catch (SQLException e) {
            logger.error("Ошибка удаления подтверждения IP", e);
        }
    }

    /**
     * Удаляет истекшие подтверждения
     */
    public int cleanupExpired() {
        String sql = "DELETE FROM ip_confirmations WHERE expires_at <= ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                logger.debug("Удалено {} истекших подтверждений IP", deleted);
            }
            return deleted;
        } catch (SQLException e) {
            logger.error("Ошибка очистки истекших подтверждений", e);
            return 0;
        }
    }

    public String confirmByNickname(String nickname) {
        cleanupExpired();
        String sql = "SELECT id, new_ip FROM ip_confirmations " +
                "WHERE nickname = ? AND expires_at > ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            stmt.setLong(2, System.currentTimeMillis());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            long id = rs.getLong("id");
            String newIp = rs.getString("new_ip");

            deleteById(id);
            return newIp;

        } catch (SQLException e) {
            logger.error("Ошибка подтверждения для {}", nickname, e);
            return null;
        }
    }

    private void deleteById(long id) {
        String sql = "DELETE FROM ip_confirmations WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
            logger.debug("Удалено подтверждение ID: {}", id);
        } catch (SQLException e) {
            logger.error("Ошибка удаления подтверждения по ID", e);
        }
    }
}
