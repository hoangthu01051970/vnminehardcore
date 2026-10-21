package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlayerProfileDatabaseManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializesExpectedTablesAndSchemaVersion() throws Exception {
        File dataDir = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager manager = new PlayerProfileDatabaseManager(dataDir);

        manager.initialize();

        assertEquals(1, manager.getSchemaVersion());
        assertTrue(manager.tableExists("meta"));
        assertTrue(manager.tableExists("player_profiles"));
        assertTrue(manager.tableExists("player_quest_progress"));
        assertTrue(manager.tableExists("player_world_progress"));
        assertTrue(manager.tableExists("player_portal_progress"));
    }

    @Test
    public void createsBackupWhenDatabaseExists() throws Exception {
        File dataDir = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager manager = new PlayerProfileDatabaseManager(dataDir);
        manager.initialize();

        manager.createPlayerProfile(
            "11111111-2222-3333-4444-555555666666",
            "Ares",
            "human",
            "swordsman",
            1,
            0,
            "human-realm",
            "2026-10-21T00:00:00Z"
        );
        manager.backupDatabase();

        File backupsDir = new File(dataDir, "data/backups");
        File[] backups = backupsDir.listFiles((dir, name) -> name.startsWith("player_profiles.db."));
        assertTrue(backups != null && backups.length > 0);
    }

    @Test
    public void migrationKeepsCurrentSchemaVersionStable() throws Exception {
        File dataDir = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager manager = new PlayerProfileDatabaseManager(dataDir);

        manager.initialize();
        manager.migrateIfNeeded();

        assertEquals(1, manager.getSchemaVersion());
    }

    @Test
    public void doesNotOverwriteAnExistingPlayerProfile() throws Exception {
        File dataDir = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager manager = new PlayerProfileDatabaseManager(dataDir);
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";

        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));
        assertTrue(manager.hasPlayerProfile(uuid));
        org.junit.Assert.assertFalse(manager.createPlayerProfile(uuid, "Ares", "demon", "berserker", 1, 0,
            "demon-realm", "2026-10-21T01:00:00Z"));

        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + manager.getDatabaseFile());
             var statement = connection.prepareStatement(
                 "SELECT selected_race, selected_class, home_world FROM player_profiles WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (var result = statement.executeQuery()) {
                org.junit.Assert.assertTrue(result.next());
                assertEquals("human", result.getString("selected_race"));
                assertEquals("swordsman", result.getString("selected_class"));
                assertEquals("human-realm", result.getString("home_world"));
            }
        }
    }
}
