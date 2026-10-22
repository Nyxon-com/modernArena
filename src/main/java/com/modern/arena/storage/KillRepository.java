//made with love by nyxon

package com.modern.arena.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KillRepository {

  private final StorageManager storage;
  private final String incrementSql;
  private final String selectSql;
  private final String claimSql;

  public KillRepository(StorageManager storage) {
    this.storage = storage;
    String kills = storage.table("kills");
    this.incrementSql = storage.sqlite()
        ? "INSERT INTO " + kills + " (player_uuid, mob_id, kills) VALUES (?, ?, 1) "
            + "ON CONFLICT(player_uuid, mob_id) DO UPDATE SET kills = kills + 1"
        : "INSERT INTO " + kills + " (player_uuid, mob_id, kills) VALUES (?, ?, 1) "
            + "ON DUPLICATE KEY UPDATE kills = kills + 1";
    this.selectSql = "SELECT kills FROM " + kills + " WHERE player_uuid = ? AND mob_id = ?";
    this.claimSql = (storage.sqlite() ? "INSERT OR IGNORE INTO " : "INSERT IGNORE INTO ")
        + storage.table("milestones")
        + " (player_uuid, mob_id, threshold) VALUES (?, ?, ?)";
  }

  public int increment(UUID uuid, String mobId) throws SQLException {
    try (Connection connection = this.storage.connection()) {
      try (PreparedStatement upsert = connection.prepareStatement(this.incrementSql)) {
        upsert.setString(1, uuid.toString());
        upsert.setString(2, mobId);
        upsert.executeUpdate();
      }
      try (PreparedStatement select = connection.prepareStatement(this.selectSql)) {
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

  public Map<String, Integer> list(UUID uuid) throws SQLException {
    Map<String, Integer> kills = new LinkedHashMap<>();
    String sql = "SELECT mob_id, kills FROM " + this.storage.table("kills")
        + " WHERE player_uuid = ? ORDER BY mob_id";
    try (Connection connection = this.storage.connection();
       PreparedStatement select = connection.prepareStatement(sql)) {
      select.setString(1, uuid.toString());
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          kills.put(result.getString(1), result.getInt(2));
        }
      }
    }
    return kills;
  }

  public Set<UUID> listPlayers() throws SQLException {
    Set<UUID> players = new LinkedHashSet<>();
    String sql = "SELECT DISTINCT player_uuid FROM " + this.storage.table("kills");
    try (Connection connection = this.storage.connection();
       PreparedStatement select = connection.prepareStatement(sql);
       ResultSet result = select.executeQuery()) {
      while (result.next()) {
        try {
          players.add(UUID.fromString(result.getString(1)));
        } catch (IllegalArgumentException ignored) {

        }
      }
    }
    return players;
  }

  public boolean claim(UUID uuid, String mobId, int threshold) throws SQLException {
    try (Connection connection = this.storage.connection();
       PreparedStatement insert = connection.prepareStatement(this.claimSql)) {
      insert.setString(1, uuid.toString());
      insert.setString(2, mobId);
      insert.setInt(3, threshold);
      return insert.executeUpdate() > 0;
    }
  }

  public int clearAllClaims() throws SQLException {
    String sql = "DELETE FROM " + this.storage.table("milestones");
    try (Connection connection = this.storage.connection();
       PreparedStatement delete = connection.prepareStatement(sql)) {
      return delete.executeUpdate();
    }
  }
}
