package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PlayerProfileDatabaseManager {
    public static final int SCHEMA_VERSION = 1;

    private final File databaseFile;
    private final File backupDirectory;

    public PlayerProfileDatabaseManager(File dataFolder) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("dataFolder cannot be null");
        }
        File dataDirectory = new File(dataFolder, "data");
        this.databaseFile = new File(dataDirectory, "player_profiles.db");
        this.backupDirectory = new File(dataDirectory, "backups");
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public void initialize() throws SQLException {
        ensureDirectories();
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
                statement.execute("CREATE TABLE IF NOT EXISTS player_profiles (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "selected_race TEXT, " +
                    "selected_class TEXT, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "exp INTEGER NOT NULL DEFAULT 0, " +
                    "home_world TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL, " +
                    "is_active INTEGER NOT NULL DEFAULT 1) ");
                statement.execute("CREATE TABLE IF NOT EXISTS player_quest_progress (" +
                    "uuid TEXT NOT NULL, " +
                    "quest_id TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "current_step TEXT, " +
                    "started_at TEXT, " +
                    "completed_at TEXT, " +
                    "reward_claimed INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (uuid, quest_id), " +
                    "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS player_world_progress (" +
                    "uuid TEXT NOT NULL, " +
                    "world_id TEXT NOT NULL, " +
                    "unlocked INTEGER NOT NULL DEFAULT 0, " +
                    "completed INTEGER NOT NULL DEFAULT 0, " +
                    "last_visited TEXT, " +
                    "PRIMARY KEY (uuid, world_id), " +
                    "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS player_portal_progress (" +
                    "uuid TEXT NOT NULL, " +
                    "portal_id TEXT NOT NULL, " +
                    "unlocked INTEGER NOT NULL DEFAULT 0, " +
                    "last_used TEXT, " +
                    "PRIMARY KEY (uuid, portal_id), " +
                    "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            }
            ensureSchemaVersion();
        }
    }

    public int getSchemaVersion() throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT value FROM meta WHERE key = 'schema_version'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Integer.parseInt(resultSet.getString("value"));
                }
            }
        }
        return 0;
    }

    public void migrateIfNeeded() throws SQLException {
        ensureDirectories();
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            }
        }
        int currentVersion = getSchemaVersion();
        if (currentVersion == 0) {
            setSchemaVersion(SCHEMA_VERSION);
            return;
        }
        if (currentVersion < SCHEMA_VERSION) {
            setSchemaVersion(SCHEMA_VERSION);
        }
    }

    public void backupDatabase() throws IOException, SQLException {
        ensureDirectories();
        if (!databaseFile.exists()) {
            return;
        }
        Files.createDirectories(backupDirectory.toPath());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backupFile = new File(backupDirectory, "player_profiles.db." + timestamp + ".bak");
        Files.copy(databaseFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = openConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
                return resultSet.next();
            }
        }
    }

    public boolean createPlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                                       int level, int exp, String homeWorld, String createdAt) throws SQLException {
        String sql = "INSERT INTO player_profiles " +
            "(uuid, player_name, selected_race, selected_class, level, exp, home_world, created_at, updated_at, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1) ON CONFLICT(uuid) DO NOTHING";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            statement.setString(3, selectedRace);
            statement.setString(4, selectedClass);
            statement.setInt(5, level);
            statement.setInt(6, exp);
            statement.setString(7, homeWorld);
            statement.setString(8, createdAt);
            statement.setString(9, createdAt);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean hasPlayerProfile(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM player_profiles WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public List<String> getPlayerProfileTableNames() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = openConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private void ensureDirectories() {
        File directory = databaseFile.getParentFile();
        if (directory != null) {
            directory.mkdirs();
        }
        if (backupDirectory != null) {
            backupDirectory.mkdirs();
        }
    }

    private void ensureSchemaVersion() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            try (ResultSet resultSet = statement.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
                if (!resultSet.next()) {
                    statement.execute("INSERT INTO meta (key, value) VALUES ('schema_version', '" + SCHEMA_VERSION + "')");
                }
            }
        }
    }

    private void setSchemaVersion(int version) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO meta (key, value) VALUES ('schema_version', ?) " +
                     "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, Integer.toString(version));
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        return DriverManager.getConnection(url);
    }
}
