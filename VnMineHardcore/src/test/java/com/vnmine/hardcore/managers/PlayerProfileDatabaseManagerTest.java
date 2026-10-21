package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlayerProfileDatabaseManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializesExpectedTablesAndCurrentSchemaVersion() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();

        manager.initialize();

        assertEquals(2, manager.getSchemaVersion());
        assertTrue(manager.tableExists("meta"));
        assertTrue(manager.tableExists("player_profiles"));
        assertTrue(manager.tableExists("player_quest_progress"));
        assertTrue(manager.tableExists("player_world_progress"));
        assertTrue(manager.tableExists("player_portal_progress"));
    }

    @Test
    public void migratesLegacyProfileTableAndPreservesExistingData() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 1);

        manager.initialize();

        assertEquals(2, manager.getSchemaVersion());
        assertTrue(columnExists(manager.getDatabaseFile(), "selected_class"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT player_name, selected_race FROM player_profiles WHERE uuid = 'legacy-player'")) {
            assertTrue(resultSet.next());
            assertEquals("Legacy", resultSet.getString("player_name"));
            assertEquals("human", resultSet.getString("selected_race"));
        }
    }

    @Test
    public void migrationIsAtomicWhenSchemaVersionCannotBeUpdated() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 1);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(1, manager.getSchemaVersion());
        assertFalse(columnExists(manager.getDatabaseFile(), "selected_class"));
    }

    @Test
    public void refusesDatabaseCreatedByNewerPluginVersion() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 3);

        SQLException exception = assertThrows(SQLException.class, manager::initialize);

        assertTrue(exception.getMessage().contains("newer than supported"));
        assertEquals(3, manager.getSchemaVersion());
    }

    @Test
    public void createsConsistentBackupContainingCommittedProfile() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        manager.backupDatabase();

        File backup = latestBackup(manager);
        assertNotNull(backup);
        try (Connection connection = open(backup);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT player_name FROM player_profiles WHERE uuid = '" + uuid + "'")) {
            assertTrue(resultSet.next());
            assertEquals("Ares", resultSet.getString("player_name"));
        }
    }

    @Test
    public void backsUpExistingStateBeforeCreatingAnotherProfile() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String firstUuid = "11111111-2222-3333-4444-555555666666";
        String secondUuid = "22222222-3333-4444-5555-666666777777";
        assertTrue(manager.createPlayerProfile(firstUuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertTrue(manager.createPlayerProfile(secondUuid, "Boreas", "human", "mage", 1, 0,
            "human-realm", "2026-10-21T01:00:00Z"));

        File backup = latestBackup(manager);
        assertNotNull(backup);
        try (Connection connection = open(backup);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM player_profiles")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    public void retriesBusyDatabaseAndRetainsOnlyConfiguredBackupCount() throws Exception {
        class BusyOnceManager extends PlayerProfileDatabaseManager {
            private boolean failNextBackup;
            private int backupCalls;

            private BusyOnceManager(File dataFolder) {
                super(dataFolder, 2, 1, 10);
            }

            private void failNextBackup() {
                failNextBackup = true;
            }

            @Override
            public synchronized void backupDatabase() throws IOException, SQLException {
                backupCalls++;
                if (failNextBackup) {
                    failNextBackup = false;
                    throw new SQLException("database is locked", "SQLITE_BUSY", 5);
                }
                super.backupDatabase();
            }
        }

        BusyOnceManager manager = new BusyOnceManager(temporaryFolder.newFolder("plugin-data"));
        manager.initialize();
        manager.failNextBackup();

        assertTrue(manager.createPlayerProfile("11111111-2222-3333-4444-555555666666",
            "Ares", "human", "swordsman", 1, 0, "human-realm", "2026-10-21T00:00:00Z"));
        assertEquals(3, manager.backupCalls);

        Thread.sleep(10);
        manager.backupDatabase();
        Thread.sleep(10);
        manager.backupDatabase();

        File[] backups = new File(manager.getDatabaseFile().getParentFile(), "backups")
            .listFiles((directory, name) -> name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        assertNotNull(backups);
        assertEquals(2, backups.length);
    }

    @Test
    public void rollsBackProfileWriteWhenDatabaseRejectsIt() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_profile_insert BEFORE INSERT ON player_profiles " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, () -> manager.createPlayerProfile(
            "11111111-2222-3333-4444-555555666666", "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertFalse(manager.hasPlayerProfile("11111111-2222-3333-4444-555555666666"));
    }

    @Test
    public void unlocksWorldExactlyOnceWithoutCreatingDuplicateProgressRows() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertFalse(manager.isWorldUnlocked(uuid, "demon-realm"));
        assertTrue(manager.unlockWorld(uuid, "demon-realm"));
        assertFalse(manager.unlockWorld(uuid, "demon-realm"));
        assertTrue(manager.isWorldUnlocked(uuid, "demon-realm"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT COUNT(*) FROM player_world_progress WHERE uuid = '" + uuid +
                     "' AND world_id = 'demon-realm'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    public void questProgressAndCompletionAreIdempotent() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertTrue(manager.startQuestProgress(uuid, "first-trial", "find-guide", "started"));
        assertFalse(manager.startQuestProgress(uuid, "first-trial", "find-guide", "duplicate-start"));
        assertTrue(manager.updateQuestStep(uuid, "first-trial", "speak-to-guide"));
        assertTrue(manager.completeQuest(uuid, "first-trial", "speak-to-guide", "completed"));
        assertFalse(manager.completeQuest(uuid, "first-trial", "speak-to-guide", "duplicate-completion"));
        assertFalse(manager.updateQuestStep(uuid, "first-trial", "after-completion"));

        PlayerProfileDatabaseManager.QuestProgress progress =
            manager.getQuestProgress(uuid, "first-trial").orElseThrow();
        assertEquals("completed", progress.status());
        assertEquals("speak-to-guide", progress.currentStep());
        assertEquals("started", progress.startedAt());
        assertEquals("completed", progress.completedAt());
        assertFalse(progress.rewardClaimed());
    }

    @Test
    public void questCompletionFailureRollsBackAndKeepsQuestActive() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));
        assertTrue(manager.startQuestProgress(uuid, "first-trial", "find-guide", "started"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_quest_completion BEFORE UPDATE ON player_quest_progress " +
                "WHEN NEW.status = 'completed' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class,
            () -> manager.completeQuest(uuid, "first-trial", "speak-to-guide", "completed"));

        assertEquals("active", manager.getQuestProgress(uuid, "first-trial").orElseThrow().status());
    }

    @Test
    public void restoresCorruptDatabaseFromLatestValidBackupAndPreservesOriginal() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));
        manager.backupDatabase();
        Files.writeString(manager.getDatabaseFile().toPath(), "corrupt database");

        manager.initialize();

        assertTrue(manager.hasPlayerProfile(uuid));
        assertNotNull(manager.getRecoveryMessage());
        File[] preserved = new File(manager.getDatabaseFile().getParentFile(), "backups")
            .listFiles((directory, name) -> name.contains(".corrupt."));
        assertTrue(preserved != null && preserved.length > 0);
    }

    @Test
    public void doesNotReplaceCorruptDatabaseWhenNoBackupExists() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        File databaseFile = manager.getDatabaseFile();
        Files.createDirectories(databaseFile.getParentFile().toPath());
        byte[] original = "corrupt database".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(databaseFile.toPath(), original);

        assertThrows(SQLException.class, manager::initialize);

        assertTrue(Arrays.equals(original, Files.readAllBytes(databaseFile.toPath())));
    }

    @Test
    public void migrationIsIdempotentAndExistingProfilesAreNotOverwritten() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        manager.migrateIfNeeded();
        manager.initialize();

        assertEquals(2, manager.getSchemaVersion());
        assertFalse(manager.createPlayerProfile(uuid, "Ares", "demon", "berserker", 1, 0,
            "demon-realm", "2026-10-21T01:00:00Z"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT selected_race, selected_class, home_world FROM player_profiles WHERE uuid = '" + uuid + "'")) {
            assertTrue(resultSet.next());
            assertEquals("human", resultSet.getString("selected_race"));
            assertEquals("swordsman", resultSet.getString("selected_class"));
            assertEquals("human-realm", resultSet.getString("home_world"));
        }
    }

    @Test
    public void profileLoadsAfterReconnectAndDatabaseManagerRestartWithoutDuplicates() throws Exception {
        File dataFolder = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager firstManager = new PlayerProfileDatabaseManager(dataFolder);
        firstManager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(firstManager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        PlayerProfileDatabaseManager.PlayerProfile savedProfile =
            firstManager.getPlayerProfile(uuid).orElseThrow();

        assertEquals(savedProfile, firstManager.getPlayerProfile(uuid).orElseThrow());

        PlayerProfileDatabaseManager restartedManager = new PlayerProfileDatabaseManager(dataFolder);
        restartedManager.initialize();
        assertEquals(savedProfile, restartedManager.getPlayerProfile(uuid).orElseThrow());
        assertFalse(restartedManager.createPlayerProfile(uuid, "Changed", "demon", "berserker",
            99, 999, "demon-realm", "overwritten"));
        assertEquals(savedProfile, restartedManager.getPlayerProfile(uuid).orElseThrow());
        assertTrue(restartedManager.getPlayerProfile("missing-player").isEmpty());
    }

    private PlayerProfileDatabaseManager newManager() throws Exception {
        return new PlayerProfileDatabaseManager(temporaryFolder.newFolder("plugin-data"));
    }

    private void createLegacyDatabase(File databaseFile, int schemaVersion) throws Exception {
        Files.createDirectories(databaseFile.getParentFile().toPath());
        try (Connection connection = open(databaseFile);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO meta (key, value) VALUES ('schema_version', '" + schemaVersion + "')");
            statement.execute("CREATE TABLE player_profiles (" +
                "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, selected_race TEXT, " +
                "level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, home_world TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1)");
            statement.execute("INSERT INTO player_profiles " +
                "(uuid, player_name, selected_race, level, exp, home_world, created_at, updated_at) " +
                "VALUES ('legacy-player', 'Legacy', 'human', 1, 0, 'human-realm', 'created', 'updated')");
        }
    }

    private boolean columnExists(File databaseFile, String columnName) throws Exception {
        try (Connection connection = open(databaseFile);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(player_profiles)")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private Connection open(File databaseFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private File latestBackup(PlayerProfileDatabaseManager manager) {
        File backupsDirectory = new File(manager.getDatabaseFile().getParentFile(), "backups");
        File[] backups = backupsDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups == null || backups.length == 0) {
            return null;
        }
        return Arrays.stream(backups).max(java.util.Comparator.comparing(File::getName)).orElse(null);
    }
}
