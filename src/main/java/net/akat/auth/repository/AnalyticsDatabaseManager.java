package net.akat.auth.repository;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class AnalyticsDatabaseManager {
    private final String dbPath;
    private final Logger logger;
    private Connection connection;

    public AnalyticsDatabaseManager(String dbPath, Logger logger) {
        this.dbPath = dbPath;
        this.logger = logger;
        initialize();
    }

    private void initialize() {
        try {
            // Минималистичное подключение
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Критически важные настройки для минимального объема
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = OFF");        // Выключаем журнал для скорости
                stmt.execute("PRAGMA synchronous = OFF");         // Максимальная скорость
                stmt.execute("PRAGMA locking_mode = EXCLUSIVE");  // Эксклюзивная блокировка
                stmt.execute("PRAGMA cache_size = -1024");        // 1MB кэша
                stmt.execute("PRAGMA page_size = 4096");
                stmt.execute("PRAGMA auto_vacuum = FULL");        // Полное сжатие при удалении
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA mmap_size = 0");             // Выключаем memory mapping
            }

            connection.setAutoCommit(true);

            logger.info("✅ База аналитики создана: {} (оптимизированная)", dbPath);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize analytics database", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки соединения", e);
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                // Финальная оптимизация перед закрытием
                try (var stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA optimize");
                }
                connection.close();
            }
        } catch (SQLException e) {
            // Игнорируем ошибки при закрытии
        }
    }

    /**
     * Получить размер базы данных в байтах
     */
    public long getDatabaseSize() {
        try {
            var file = new java.io.File(dbPath);
            return file.length();
        } catch (Exception e) {
            return -1;
        }
    }
}
