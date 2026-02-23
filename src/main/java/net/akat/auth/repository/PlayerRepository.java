package net.akat.auth.repository;

import com.google.inject.Singleton;
import net.akat.auth.model.Player;

import java.sql.*;

@Singleton
public class PlayerRepository {
    private final Connection connection;

    public PlayerRepository(Connection connection) {
        this.connection = connection;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nickname TEXT UNIQUE NOT NULL," +
                "email TEXT NOT NULL," +
                "original_ip TEXT NOT NULL," +
                "last_ip TEXT," +
                "registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_login TIMESTAMP," +
                "is_active BOOLEAN DEFAULT TRUE)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create players table", e);
        }
    }

    public void save(Player player) {
        String sql = "INSERT INTO players (nickname, email, original_ip, last_ip) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, player.getNickname());
            stmt.setString(2, player.getEmail());
            stmt.setString(3, player.getOriginalIp());
            stmt.setString(4, player.getLastIp());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save player", e);
        }
    }

    public boolean existsByNickname(String nickname) {
        String sql = "SELECT 1 FROM players WHERE nickname = ? AND is_active = TRUE";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check player existence", e);
        }
    }

    public String findByIp(String ip) {
        String sql = "SELECT nickname FROM players WHERE original_ip = ? AND is_active = TRUE LIMIT 1";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ip);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("nickname") : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player by IP", e);
        }
    }

    public String findByEmail(String email) {
        String sql = "SELECT nickname FROM players WHERE email = ? AND is_active = TRUE LIMIT 1";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("nickname") : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player by email", e);
        }
    }

    public Player findByNickname(String nickname) {
        String sql = "SELECT nickname, email, original_ip, last_ip FROM players WHERE nickname = ? AND is_active = TRUE";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Player.Builder()
                        .nickname(rs.getString("nickname"))
                        .email(rs.getString("email"))
                        .originalIp(rs.getString("original_ip"))
                        .lastIp(rs.getString("last_ip"))
                        .build();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player", e);
        }
    }

    public void updateOriginalIp(String nickname, String newOriginalIp) {
        String sql = "UPDATE players SET original_ip = ?, last_ip = ? WHERE nickname = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newOriginalIp);
            stmt.setString(2, newOriginalIp); // last_ip тоже обновляем
            stmt.setString(3, nickname);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update original IP", e);
        }
    }
}
