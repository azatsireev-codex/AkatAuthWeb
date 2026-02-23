package net.akat.auth.repository;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    private Connection connection;
    private final String databasePath;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
    }

    public DatabaseManager(String databasePath) {
        this.databasePath = databasePath;
        ensureDatabaseDirectoryExists();
    }

    /**
     * Создает все родительские папки для файла базы данных
     */
    private void ensureDatabaseDirectoryExists() {
        try {
            File dbFile = new File(databasePath);
            File parentDir = dbFile.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    System.err.println("WARNING: Could not create directory: " + parentDir.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.err.println("WARNING: Failed to create database directory: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Игнорируем ошибку закрытия
            }
        }
    }
}
