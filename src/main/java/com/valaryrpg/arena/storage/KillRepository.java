package com.valaryrpg.arena.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Query per (uuid, mob_id): increment kill e claim milestone. */
public final class KillRepository {

    private final StorageManager storage;

    public KillRepository(StorageManager storage) {
        this.storage = storage;
    }

    public int increment(UUID uuid, String mobId) throws SQLException {
        String table = this.storage.table("kills");
        try (Connection connection = this.storage.connection()) {
            if (this.storage.sqlite()) {
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO " + table + " (player_uuid, mob_id, kills) VALUES (?, ?, 1) "
                                + "ON CONFLICT(player_uuid, mob_id) DO UPDATE SET kills = kills + 1")) {
                    upsert.setString(1, uuid.toString());
                    upsert.setString(2, mobId);
                    upsert.executeUpdate();
                }
            } else {
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO " + table + " (player_uuid, mob_id, kills) VALUES (?, ?, 1) "
                                + "ON DUPLICATE KEY UPDATE kills = kills + 1")) {
                    upsert.setString(1, uuid.toString());
                    upsert.setString(2, mobId);
                    upsert.executeUpdate();
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT kills FROM " + table + " WHERE player_uuid = ? AND mob_id = ?")) {
                select.setString(1, uuid.toString());
                select.setString(2, mobId);
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        return result.getInt(1);
                    }
                }
            }
        }
        return 1;
    }

    public Set<Integer> claimed(UUID uuid, String mobId) throws SQLException {
        Set<Integer> values = new HashSet<>();
        try (Connection connection = this.storage.connection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT threshold FROM " + this.storage.table("milestones")
                             + " WHERE player_uuid = ? AND mob_id = ?")) {
            select.setString(1, uuid.toString());
            select.setString(2, mobId);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    values.add(result.getInt(1));
                }
            }
        }
        return values;
    }

    public void claim(UUID uuid, String mobId, int threshold) throws SQLException {
        try (Connection connection = this.storage.connection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO " + this.storage.table("milestones")
                             + " (player_uuid, mob_id, threshold) VALUES (?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, mobId);
            insert.setInt(3, threshold);
            insert.executeUpdate();
        } catch (SQLException exception) {
            if (!isDuplicate(exception)) {
                throw exception;
            }
        }
    }

    private boolean isDuplicate(SQLException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("UNIQUE") || message.contains("Duplicate"));
    }
}
