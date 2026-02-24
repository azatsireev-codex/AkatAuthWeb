package net.akat.auth.repository;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class FamilyAccessRepository {
    private final Connection connection;
    private final Logger logger;

    public FamilyAccessRepository(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS family_access (" +
                "group_id TEXT NOT NULL," +
                "nickname TEXT UNIQUE NOT NULL)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_family_group_id ON family_access(group_id)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create family_access table", e);
        }
    }

    public synchronized String createGroupWithMembers(String nickname1, String nickname2) {
        String groupId = generateNextGroupId();
        addMember(groupId, nickname1);
        addMember(groupId, nickname2);
        return groupId;
    }

    public synchronized void addMember(String groupId, String nickname) {
        String sql = "INSERT OR REPLACE INTO family_access (group_id, nickname) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, nickname);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add member to family group", e);
        }
    }

    public synchronized boolean removeMember(String nickname) {
        String sql = "DELETE FROM family_access WHERE nickname = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove family member", e);
        }
    }

    public synchronized int deleteGroup(String groupId) {
        String sql = "DELETE FROM family_access WHERE group_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete family group", e);
        }
    }

    public synchronized String findGroupIdByNickname(String nickname) {
        String sql = "SELECT group_id FROM family_access WHERE nickname = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, nickname);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("group_id") : null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find family group by nickname", e);
        }
    }

    public synchronized boolean areInSameGroup(String nickname1, String nickname2) {
        String group1 = findGroupIdByNickname(nickname1);
        if (group1 == null) {
            return false;
        }

        String group2 = findGroupIdByNickname(nickname2);
        return group1.equals(group2);
    }

    private String generateNextGroupId() {
        String sql = "SELECT group_id FROM family_access WHERE group_id LIKE 'f%'";
        int maxId = 0;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String groupId = rs.getString("group_id");
                if (groupId == null || groupId.length() < 2) {
                    continue;
                }

                try {
                    int numeric = Integer.parseInt(groupId.substring(1));
                    if (numeric > maxId) {
                        maxId = numeric;
                    }
                } catch (NumberFormatException ignored) {
                    logger.debug("Пропущен нестандартный family group id: {}", groupId);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to generate next family group id", e);
        }

        return "f" + (maxId + 1);
    }
}
