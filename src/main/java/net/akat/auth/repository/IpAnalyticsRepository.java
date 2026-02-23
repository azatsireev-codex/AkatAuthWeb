package net.akat.auth.repository;

import com.google.inject.Singleton;
import org.slf4j.Logger;

import java.sql.*;

@Singleton
public class IpAnalyticsRepository {
    private final Connection connection;
    private final Logger logger;

    private static final int MAX_NICKNAME_LENGTH = 16;
    private static final int MAX_IP_LENGTH = 45; // Для IPv6

    public IpAnalyticsRepository(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
        createTable();
    }

    private void createTable() {
        // Простая таблица с IP как текст
        String sql = "CREATE TABLE IF NOT EXISTS ip_analytics (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nick TEXT NOT NULL," +               // Никнейм
                "ip TEXT NOT NULL," +                 // IP как текст (192.168.1.100)
                "ts INTEGER NOT NULL," +              // Время в секундах
                "confirmed INTEGER NOT NULL DEFAULT 0" + // 0 = false, 1 = true
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            logger.info("✅ Таблица ip_analytics создана (IP как текст)");

            // Создаем полезные индексы
            createIndexes();

        } catch (SQLException e) {
            logger.error("Ошибка при создании таблицы ip_analytics", e);
            throw new RuntimeException("Failed to create ip_analytics table", e);
        }
    }

    private void createIndexes() {
        try (Statement stmt = connection.createStatement()) {
            // Основной индекс для поиска по нику и IP
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ip_analytics_nick_ip ON ip_analytics(nick, ip)");

            // Индекс для поиска по IP
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ip_analytics_ip ON ip_analytics(ip)");

            // Индекс по времени для очистки
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ip_analytics_ts ON ip_analytics(ts)");

            logger.debug("Индексы созданы");
        } catch (SQLException e) {
            logger.warn("Не удалось создать индексы", e);
        }
    }

    /**
     * Метод 1: Логирует вход или обновляет время
     * Если записи с таким ником и IP нет → создает новую с confirmed = 0
     * Если запись есть → обновляет время
     */
    public void logOrUpdateTimestamp(String nickname, String ipAddress) {
        if (nickname == null || ipAddress == null) return;

        // Обрезаем ник если нужно
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            nickname = nickname.substring(0, MAX_NICKNAME_LENGTH);
        }

        // Обрезаем IP если слишком длинный
        if (ipAddress.length() > MAX_IP_LENGTH) {
            ipAddress = ipAddress.substring(0, MAX_IP_LENGTH);
        }

        // Убираем пробелы
        ipAddress = ipAddress.trim();

        long timestamp = System.currentTimeMillis() / 1000;
        String lowercaseNick = nickname.toLowerCase();

        try {
            // 1. Пытаемся обновить время существующей записи
            String updateSql = "UPDATE ip_analytics SET ts = ? WHERE nick = ? AND ip = ?";

            try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                stmt.setLong(1, timestamp);
                stmt.setString(2, lowercaseNick);
                stmt.setString(3, ipAddress);

                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    // Время обновлено
                    return;
                }
            }

            // 2. Записи нет - создаем новую с confirmed = 0
            String insertSql = "INSERT INTO ip_analytics (nick, ip, ts, confirmed) VALUES (?, ?, ?, 0)";

            try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                stmt.setString(1, lowercaseNick);
                stmt.setString(2, ipAddress);
                stmt.setLong(3, timestamp);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            logger.error("Ошибка при логировании для {} @ {}", nickname, ipAddress, e);
        }
    }

    /**
     * Метод 2: Подтверждает или создает подтвержденную запись
     * Ищет запись с ником и IP → меняет confirmed = 0 на 1
     * Если записи нет → создает новую с confirmed = 1
     */
    public boolean confirmOrCreateRecord(String nickname, String ipAddress) {
        if (nickname == null || ipAddress == null) return false;

        // Обрезаем ник если нужно
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            nickname = nickname.substring(0, MAX_NICKNAME_LENGTH);
        }

        // Обрезаем IP если слишком длинный
        if (ipAddress.length() > MAX_IP_LENGTH) {
            ipAddress = ipAddress.substring(0, MAX_IP_LENGTH);
        }

        // Убираем пробелы
        ipAddress = ipAddress.trim();

        long timestamp = System.currentTimeMillis() / 1000;
        String lowercaseNick = nickname.toLowerCase();

        try {
            // 1. Пытаемся обновить существующую запись
            String updateSql = "UPDATE ip_analytics SET confirmed = 1, ts = ? WHERE nick = ? AND ip = ?";

            try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                stmt.setLong(1, timestamp);
                stmt.setString(2, lowercaseNick);
                stmt.setString(3, ipAddress);

                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    logger.info("✅ Подтверждена запись для {} @ {}", nickname, ipAddress);
                    return true;
                }
            }

            // 2. Записи нет - создаем новую с confirmed = 1
            String insertSql = "INSERT INTO ip_analytics (nick, ip, ts, confirmed) VALUES (?, ?, ?, 1)";

            try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                stmt.setString(1, lowercaseNick);
                stmt.setString(2, ipAddress);
                stmt.setLong(3, timestamp);
                stmt.executeUpdate();

                logger.info("✅ Создана подтвержденная запись для {} @ {}", nickname, ipAddress);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Ошибка при подтверждении для {} @ {}", nickname, ipAddress, e);
            return false;
        }
    }

    /**
     * Очищает старые записи (опционально)
     */
    public void cleanupOldRecords(int keepDays) {
        long cutoffTs = (System.currentTimeMillis() / 1000) - (keepDays * 86400L);

        try {
            String sql = "DELETE FROM ip_analytics WHERE ts < ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, cutoffTs);
                int deleted = stmt.executeUpdate();

                if (deleted > 0) {
                    logger.info("Удалено {} старых записей аналитики", deleted);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка при очистке старых записей", e);
        }
    }
}
