package com.modern.arena.storage;

import com.modern.arena.ModernArenaPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Pool Hikari (SQLite / MySQL / MariaDB) e schema kill/milestone. */
public final class StorageManager {

    private final ModernArenaPlugin plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;
    private boolean sqlite;

    public StorageManager(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        FileConfiguration yaml = this.plugin.configs().storage();
        this.tablePrefix = yaml.getString("table-prefix", "valary_arena_");
        String type = yaml.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        this.sqlite = !type.equals("mysql") && !type.equals("mariadb");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ModernArena");
        hikari.setMaximumPoolSize(yaml.getInt("pool.maximum-pool-size", 4));
        hikari.setMinimumIdle(yaml.getInt("pool.minimum-idle", 1));
        hikari.setConnectionTimeout(yaml.getLong("pool.connection-timeout-millis", 10000L));

        if (this.sqlite) {
            File file = new File(this.plugin.getDataFolder(), yaml.getString("sqlite.file", "arena.db"));
            file.getParentFile().mkdirs();
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
        } else {
            String host = yaml.getString("mysql.host", "127.0.0.1");
            int port = yaml.getInt("mysql.port", 3306);
            String database = yaml.getString("mysql.database", "valary_arena");
            boolean ssl = yaml.getBoolean("mysql.ssl", false);
            hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + ssl);
            hikari.setUsername(yaml.getString("mysql.username", "root"));
            hikari.setPassword(yaml.getString("mysql.password", ""));
            hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        }

        this.dataSource = new HikariDataSource(hikari);
        createSchema();
    }

    public Connection connection() throws SQLException {
        return this.dataSource.getConnection();
    }

    public String table(String name) {
        return this.tablePrefix + name;
    }

    public boolean sqlite() {
        return this.sqlite;
    }

    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    private void createSchema() throws SQLException {
        String kills = table("kills");
        String milestones = table("milestones");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      player_uuid VARCHAR(36) NOT NULL,
                      mob_id VARCHAR(128) NOT NULL,
                      kills INT NOT NULL DEFAULT 0,
                      PRIMARY KEY (player_uuid, mob_id)
                    )
                    """.formatted(kills));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      player_uuid VARCHAR(36) NOT NULL,
                      mob_id VARCHAR(128) NOT NULL,
                      threshold INT NOT NULL,
                      PRIMARY KEY (player_uuid, mob_id, threshold)
                    )
                    """.formatted(milestones));
        }
    }
}
