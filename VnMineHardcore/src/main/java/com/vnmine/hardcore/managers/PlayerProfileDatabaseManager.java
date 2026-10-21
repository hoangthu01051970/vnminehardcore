package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PlayerProfileDatabaseManager {
    public static final int SCHEMA_VERSION = 2;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private final File databaseFile;
    private final File backupDirectory;
    private String recoveryMessage;
    private int backupRetentionCount;
    private int writeRetries;
    private int writeRetryDelayMillis;

    public PlayerProfileDatabaseManager(File dataFolder) {
        this(dataFolder, 12, 2, 50);
    }

    public PlayerProfileDatabaseManager(File dataFolder, int backupRetentionCount, int writeRetries,
                                        int writeRetryDelayMillis) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("dataFolder cannot be null");
        }
        configureMaintenance(backupRetentionCount, writeRetries, writeRetryDelayMillis);
        File dataDirectory = new File(dataFolder, "data");
        this.databaseFile = new File(dataDirectory, "player_profiles.db");
        this.backupDirectory = new File(dataDirectory, "backups");
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public String getRecoveryMessage() {
        return recoveryMessage;
    }

    public synchronized void configureMaintenance(int backupRetentionCount, int writeRetries,
                                                  int writeRetryDelayMillis) {
        if (backupRetentionCount < 1 || backupRetentionCount > 100) {
            throw new IllegalArgumentException("backupRetentionCount must be between 1 and 100.");
        }
        if (writeRetries < 0 || writeRetries > 3) {
            throw new IllegalArgumentException("writeRetries must be between 0 and 3.");
        }
        if (writeRetryDelayMillis < 10 || writeRetryDelayMillis > 250) {
            throw new IllegalArgumentException("writeRetryDelayMillis must be between 10 and 250.");
        }
        this.backupRetentionCount = backupRetentionCount;
        this.writeRetries = writeRetries;
        this.writeRetryDelayMillis = writeRetryDelayMillis;
    }

    public void initialize() throws SQLException {
        ensureDirectories();
        recoverDatabaseIfNeeded();
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            }
        }
        migrateIfNeeded();
        try {
            backupDatabase();
        } catch (IOException exception) {
            throw new SQLException("Could not create the initial player profile database backup.", exception);
        }
    }

    public int getSchemaVersion() throws SQLException {
        try (Connection connection = openConnection()) {
            return readSchemaVersion(connection);
        }
    }

    private void waitBeforeRetry(SQLException originalException) throws SQLException {
        try {
            Thread.sleep(writeRetryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            SQLException interrupted = new SQLException("Interrupted while retrying a busy profile database.",
                exception);
            interrupted.addSuppressed(originalException);
            throw interrupted;
        }
    }

    private boolean isSqliteBusy(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int primaryCode = current.getErrorCode() & 0xff;
            if (primaryCode == 5 || primaryCode == 6) {
                return true;
            }
            for (Throwable cause = current.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sqlCause) {
                    int causeCode = sqlCause.getErrorCode() & 0xff;
                    if (causeCode == 5 || causeCode == 6) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pruneBackups() throws IOException {
        File[] backups = backupDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups == null) {
            throw new IOException("Could not list player profile database backups.");
        }
        List<File> validBackups = new ArrayList<>();
        for (File backup : backups) {
            if (isDatabaseHealthy(backup)) {
                validBackups.add(backup);
            }
        }
        if (validBackups.size() <= backupRetentionCount) {
            return;
        }
        validBackups.sort(Comparator.comparingLong(File::lastModified)
            .thenComparing(File::getName));
        int deleteCount = validBackups.size() - backupRetentionCount;
        for (int index = 0; index < deleteCount; index++) {
            Files.delete(validBackups.get(index).toPath());
        }
    }

    public void migrateIfNeeded() throws SQLException {
        ensureDirectories();
        int currentVersion;
        boolean hasProfiles;
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            }
            hasProfiles = tableExists(connection, "player_profiles");
            currentVersion = hasProfiles ? readSchemaVersion(connection) : 0;
        }

        if (!hasProfiles) {
            createFreshDatabase();
            return;
        }
        if (currentVersion == 0) {
            currentVersion = 1;
        }
        if (currentVersion > SCHEMA_VERSION) {
            throw new SQLException("Player profile database schema version " + currentVersion
                + " is newer than supported version " + SCHEMA_VERSION + ".");
        }
        if (currentVersion == SCHEMA_VERSION) {
            return;
        }

        try {
            backupDatabase();
        } catch (IOException exception) {
            throw new SQLException("Could not back up the player profile database before migration.", exception);
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                int version = currentVersion;
                while (version < SCHEMA_VERSION) {
                    if (version == 1) {
                        migrateV1ToV2(connection);
                        version = 2;
                        setSchemaVersion(connection, version);
                    } else {
                        throw new SQLException("No player profile database migration exists from version "
                            + version + ".");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public synchronized void backupDatabase() throws IOException, SQLException {
        ensureDirectories();
        if (!databaseFile.exists()) {
            return;
        }
        Files.createDirectories(backupDirectory.toPath());
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            if (resultSet.next() && resultSet.getInt(1) != 0) {
                throw new SQLException("SQLite could not checkpoint the profile database before backup.",
                    "SQLITE_BUSY", 5);
            }
        }

        File temporaryFile = new File(backupDirectory, "player_profiles.db.backup.tmp");
        File backupFile = newBackupFile();
        try {
            Files.copy(databaseFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporaryFile, backupFile);
            pruneBackups();
        } finally {
            Files.deleteIfExists(temporaryFile.toPath());
        }
    }

    private File newBackupFile() {
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        for (int sequence = 0; ; sequence++) {
            String fileName = "player_profiles.db." + timestamp + "-"
                + String.format(Locale.ROOT, "%04d", sequence) + ".bak";
            File candidate = new File(backupDirectory, fileName);
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }

    public boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = openConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
                return resultSet.next();
            }
        }
    }

    public synchronized boolean createPlayerProfile(String uuid, String playerName, String selectedRace,
                                                     String selectedClass, int level, int exp, String homeWorld,
                                                     String createdAt) throws SQLException {
        boolean backupCreated = false;
        for (int attempt = 0; attempt <= writeRetries; attempt++) {
            try {
                if (!hasPlayerProfile(uuid) && !backupCreated) {
                    backupDatabase();
                    backupCreated = true;
                }
                return insertPlayerProfile(uuid, playerName, selectedRace, selectedClass, level, exp,
                    homeWorld, createdAt);
            } catch (IOException exception) {
                throw new SQLException("Could not back up the player profile database before saving.", exception);
            } catch (SQLException exception) {
                if (!isSqliteBusy(exception) || attempt == writeRetries) {
                    throw exception;
                }
                waitBeforeRetry(exception);
            }
        }
        throw new SQLException("Player profile save exhausted its retry attempts.");
    }

    private boolean insertPlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                                        int level, int exp, String homeWorld, String createdAt) throws SQLException {
        String sql = "INSERT INTO player_profiles " +
            "(uuid, player_name, selected_race, selected_class, level, exp, home_world, created_at, updated_at, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1) ON CONFLICT(uuid) DO NOTHING";
        return executeWriteTransactionOnce(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
        });
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

    public Optional<PlayerProfile> getPlayerProfile(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT uuid, player_name, selected_race, selected_class, level, exp, home_world, " +
                     "created_at, updated_at, is_active FROM player_profiles WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerProfile(
                    resultSet.getString("uuid"),
                    resultSet.getString("player_name"),
                    resultSet.getString("selected_race"),
                    resultSet.getString("selected_class"),
                    resultSet.getInt("level"),
                    resultSet.getInt("exp"),
                    resultSet.getString("home_world"),
                    resultSet.getString("created_at"),
                    resultSet.getString("updated_at"),
                    resultSet.getInt("is_active") == 1
                ));
            }
        }
    }

    public boolean unlockWorld(String uuid, String worldId) throws SQLException {
        String sql = "INSERT INTO player_world_progress (uuid, world_id, unlocked) VALUES (?, ?, 1) " +
            "ON CONFLICT(uuid, world_id) DO UPDATE SET unlocked = 1 " +
            "WHERE player_world_progress.unlocked = 0";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, worldId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean isWorldUnlocked(String uuid, String worldId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT unlocked FROM player_world_progress WHERE uuid = ? AND world_id = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, worldId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt("unlocked") == 1;
            }
        }
    }

    public boolean startQuestProgress(String uuid, String questId, String currentStep, String startedAt)
        throws SQLException {
        String sql = "INSERT INTO player_quest_progress " +
            "(uuid, quest_id, status, current_step, started_at, reward_claimed) " +
            "VALUES (?, ?, 'active', ?, ?, 0) ON CONFLICT(uuid, quest_id) DO NOTHING";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                statement.setString(3, currentStep);
                statement.setString(4, startedAt);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean updateQuestStep(String uuid, String questId, String currentStep) throws SQLException {
        String sql = "UPDATE player_quest_progress SET current_step = ? " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'active'";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currentStep);
                statement.setString(2, uuid);
                statement.setString(3, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean completeQuest(String uuid, String questId, String currentStep, String completedAt)
        throws SQLException {
        String sql = "UPDATE player_quest_progress SET status = 'completed', current_step = ?, completed_at = ? " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'active'";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currentStep);
                statement.setString(2, completedAt);
                statement.setString(3, uuid);
                statement.setString(4, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public Optional<QuestProgress> getQuestProgress(String uuid, String questId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT status, current_step, started_at, completed_at, reward_claimed " +
                     "FROM player_quest_progress WHERE uuid = ? AND quest_id = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new QuestProgress(
                    resultSet.getString("status"),
                    resultSet.getString("current_step"),
                    resultSet.getString("started_at"),
                    resultSet.getString("completed_at"),
                    resultSet.getInt("reward_claimed") == 1
                ));
            }
        }
    }

    private <T> T executeWriteTransaction(SqlWork<T> operation) throws SQLException {
        for (int attempt = 0; attempt <= writeRetries; attempt++) {
            try {
                return executeWriteTransactionOnce(operation);
            } catch (SQLException exception) {
                if (!isSqliteBusy(exception) || attempt == writeRetries) {
                    throw exception;
                }
                waitBeforeRetry(exception);
            }
        }
        throw new SQLException("Player profile database write exhausted its retry attempts.");
    }

    private <T> T executeWriteTransactionOnce(SqlWork<T> operation) throws SQLException {
        try (Connection connection = openConnection();
             Statement transaction = connection.createStatement()) {
            transaction.execute("BEGIN IMMEDIATE");
            try {
                T result = operation.execute(connection);
                transaction.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public record QuestProgress(String status, String currentStep, String startedAt, String completedAt,
                                boolean rewardClaimed) {
    }

    public record PlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                                int level, int exp, String homeWorld, String createdAt, String updatedAt,
                                boolean active) {
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
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

    private void ensureDirectories() throws SQLException {
        File directory = databaseFile.getParentFile();
        try {
            if (directory != null) {
                Files.createDirectories(directory.toPath());
            }
            if (backupDirectory != null) {
                Files.createDirectories(backupDirectory.toPath());
            }
        } catch (IOException exception) {
            throw new SQLException("Could not create player profile database directories.", exception);
        }
    }

    private void createFreshDatabase() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                createCurrentTables(connection);
                setSchemaVersion(connection, SCHEMA_VERSION);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void createCurrentTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_profiles (" +
                "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, selected_race TEXT, selected_class TEXT, " +
                "level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, home_world TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_quest_progress (" +
                "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, status TEXT NOT NULL, current_step TEXT, " +
                "started_at TEXT, completed_at TEXT, reward_claimed INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, quest_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_world_progress (" +
                "uuid TEXT NOT NULL, world_id TEXT NOT NULL, unlocked INTEGER NOT NULL DEFAULT 0, " +
                "completed INTEGER NOT NULL DEFAULT 0, last_visited TEXT, PRIMARY KEY (uuid, world_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_portal_progress (" +
                "uuid TEXT NOT NULL, portal_id TEXT NOT NULL, unlocked INTEGER NOT NULL DEFAULT 0, " +
                "last_used TEXT, PRIMARY KEY (uuid, portal_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
        }
    }

    private void migrateV1ToV2(Connection connection) throws SQLException {
        if (!columnExists(connection, "player_profiles", "selected_class")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE player_profiles ADD COLUMN selected_class TEXT");
            }
        }
        createCurrentTables(connection);
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO meta (key, value) VALUES ('schema_version', ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, Integer.toString(version));
            statement.executeUpdate();
        }
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT value FROM meta WHERE key = 'schema_version'");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 0;
            }
            try {
                return Integer.parseInt(resultSet.getString("value"));
            } catch (NumberFormatException exception) {
                throw new SQLException("Player profile database has an invalid schema_version value.", exception);
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }

    private void recoverDatabaseIfNeeded() throws SQLException {
        if (!databaseFile.isFile() || isDatabaseHealthy(databaseFile)) {
            return;
        }
        File[] backups = backupDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups != null) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed()
                .thenComparing(File::getName, Comparator.reverseOrder()));
            for (File backup : backups) {
                if (!isDatabaseHealthy(backup)) {
                    continue;
                }
                preserveCorruptDatabase();
                File temporaryFile = new File(databaseFile.getParentFile(), "player_profiles.db.restore.tmp");
                try {
                    Files.copy(backup.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    moveReplacing(temporaryFile, databaseFile);
                    recoveryMessage = "Player profile database was restored from " + backup.getName() + ".";
                    return;
                } catch (IOException exception) {
                    throw new SQLException("Could not restore player profile database from "
                        + backup.getName() + ".", exception);
                } finally {
                    try {
                        Files.deleteIfExists(temporaryFile.toPath());
                    } catch (IOException exception) {
                        throw new SQLException("Could not remove temporary profile database restore file.", exception);
                    }
                }
            }
        }
        throw new SQLException("Player profile database is corrupt and no valid backup was found. "
            + "The original database has been preserved for manual recovery.");
    }

    private boolean isDatabaseHealthy(File file) {
        if (!file.isFile()) {
            return false;
        }
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA integrity_check")) {
            return resultSet.next() && "ok".equalsIgnoreCase(resultSet.getString(1));
        } catch (SQLException exception) {
            return false;
        }
    }

    private void preserveCorruptDatabase() throws SQLException {
        String suffix = ".corrupt." + BACKUP_TIMESTAMP.format(Instant.now());
        try {
            if (databaseFile.exists()) {
                Files.move(databaseFile.toPath(), new File(backupDirectory, databaseFile.getName() + suffix).toPath());
            }
            for (String sidecar : List.of(databaseFile.getName() + "-wal", databaseFile.getName() + "-shm")) {
                File sidecarFile = new File(databaseFile.getParentFile(), sidecar);
                if (sidecarFile.exists()) {
                    Files.move(sidecarFile.toPath(), new File(backupDirectory, sidecar + suffix).toPath());
                }
            }
        } catch (IOException exception) {
            throw new SQLException("Could not preserve the corrupt player profile database.", exception);
        }
    }

    private void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
