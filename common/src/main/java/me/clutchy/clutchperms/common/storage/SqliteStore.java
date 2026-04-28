package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Owns the Hikari-backed SQLite connection pool and schema lifecycle.
 */
public final class SqliteStore implements AutoCloseable {

    /**
     * Current SQLite schema version.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final Path databaseFile;

    private final HikariDataSource dataSource;

    private SqliteStore(Path databaseFile, HikariDataSource dataSource, boolean initializeSchema) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile").toAbsolutePath().normalize();
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (initializeSchema) {
            initializeSchema();
        } else {
            validateSchema();
        }
    }

    /**
     * Opens the live database and creates the schema when needed.
     *
     * @param databaseFile database path
     * @param dependencyMode expected dependency provisioning mode
     * @return open store
     */
    public static SqliteStore open(Path databaseFile, SqliteDependencyMode dependencyMode) {
        return open(databaseFile, dependencyMode, true);
    }

    /**
     * Opens an existing database without creating missing schema objects.
     *
     * @param databaseFile database path
     * @param dependencyMode expected dependency provisioning mode
     * @return open store
     */
    public static SqliteStore openExisting(Path databaseFile, SqliteDependencyMode dependencyMode) {
        return open(databaseFile, dependencyMode, false);
    }

    private static SqliteStore open(Path databaseFile, SqliteDependencyMode dependencyMode, boolean initializeSchema) {
        SqliteDependencyGuard.require(dependencyMode);
        Path normalizedDatabaseFile = Objects.requireNonNull(databaseFile, "databaseFile").toAbsolutePath().normalize();
        try {
            Path parentDirectory = normalizedDatabaseFile.getParent();
            if (initializeSchema && parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to create storage directory for " + normalizedDatabaseFile, exception);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("ClutchPerms SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + normalizedDatabaseFile);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setAutoCommit(true);
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
        try {
            return new SqliteStore(normalizedDatabaseFile, new HikariDataSource(config), initializeSchema);
        } catch (RuntimeException exception) {
            throw new PermissionStorageException("Failed to open SQLite database at " + normalizedDatabaseFile, exception);
        }
    }

    /**
     * Returns the database file path.
     *
     * @return database file path
     */
    public Path databaseFile() {
        return databaseFile;
    }

    /**
     * Returns the pooled data source.
     *
     * @return data source
     */
    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Executes a read action with a pooled connection.
     *
     * @param action action to run
     * @param <T> result type
     * @return action result
     */
    public <T> T read(SqliteReadAction<T> action) {
        Objects.requireNonNull(action, "action");
        try (Connection connection = dataSource.getConnection()) {
            configureConnection(connection);
            return action.run(connection);
        } catch (PermissionStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionStorageException("Failed to read SQLite storage from " + databaseFile, exception);
        } catch (SQLException exception) {
            throw new PermissionStorageException("Failed to read SQLite storage from " + databaseFile, exception);
        }
    }

    /**
     * Executes a write action inside one transaction.
     *
     * @param action action to run
     */
    public void write(SqliteWriteAction action) {
        Objects.requireNonNull(action, "action");
        try (Connection connection = dataSource.getConnection()) {
            configureConnection(connection);
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                action.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (PermissionStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionStorageException("Failed to write SQLite storage to " + databaseFile, exception);
        } catch (SQLException exception) {
            throw new PermissionStorageException("Failed to write SQLite storage to " + databaseFile, exception);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private void initializeSchema() {
        write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS subject_permissions (subject_id TEXT NOT NULL, node TEXT NOT NULL, value TEXT NOT NULL CHECK (value IN ('TRUE', 'FALSE')), PRIMARY KEY (subject_id, node))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS subjects (subject_id TEXT PRIMARY KEY, last_known_name TEXT NOT NULL, last_seen TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS subject_display (subject_id TEXT PRIMARY KEY, prefix TEXT, suffix TEXT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS groups (name TEXT PRIMARY KEY, prefix TEXT, suffix TEXT)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS tracks (name TEXT PRIMARY KEY)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS group_permissions (group_name TEXT NOT NULL, node TEXT NOT NULL, value TEXT NOT NULL CHECK (value IN ('TRUE', 'FALSE')), PRIMARY KEY (group_name, node), FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS group_parents (group_name TEXT NOT NULL, parent_name TEXT NOT NULL, PRIMARY KEY (group_name, parent_name), FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE, FOREIGN KEY (parent_name) REFERENCES groups(name) ON DELETE CASCADE)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS memberships (subject_id TEXT NOT NULL, group_name TEXT NOT NULL, PRIMARY KEY (subject_id, group_name), FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS track_groups (track_name TEXT NOT NULL, position INTEGER NOT NULL CHECK (position >= 1), group_name TEXT NOT NULL, PRIMARY KEY (track_name, position), UNIQUE (track_name, group_name), FOREIGN KEY (track_name) REFERENCES tracks(name) ON DELETE CASCADE, FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS known_nodes (node TEXT PRIMARY KEY, description TEXT NOT NULL)");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT NOT NULL, actor_kind TEXT NOT NULL, actor_id TEXT, actor_name TEXT, action TEXT NOT NULL, target_type TEXT NOT NULL, target_key TEXT NOT NULL, target_display TEXT NOT NULL, before_json TEXT NOT NULL, after_json TEXT NOT NULL, source_command TEXT NOT NULL, undoable INTEGER NOT NULL CHECK (undoable IN (0, 1)), undone INTEGER NOT NULL CHECK (undone IN (0, 1)), undone_by_entry_id INTEGER, undone_at TEXT, undone_by_actor_id TEXT, undone_by_actor_name TEXT)");
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                    rows.next();
                    if (rows.getInt(1) == 0) {
                        statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + CURRENT_SCHEMA_VERSION + ")");
                    }
                }
                validateSchemaVersion(connection);
                statement.executeUpdate("INSERT OR IGNORE INTO groups (name, prefix, suffix) VALUES ('default', NULL, NULL)");
            }
            materializeProtectedOpGroup(connection);
        });
    }

    private void validateSchema() {
        read(connection -> {
            validateSchemaVersion(connection);
            return null;
        });
    }

    private static void validateSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT version FROM schema_version")) {
                if (!rows.next()) {
                    throw new PermissionStorageException("Missing SQLite schema version");
                }
                int version = rows.getInt(1);
                if (version != CURRENT_SCHEMA_VERSION) {
                    throw new PermissionStorageException("Unsupported SQLite schema version " + version);
                }
            }
        }
    }

    private static void materializeProtectedOpGroup(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            boolean opGroupExists;
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM groups WHERE name = 'op'")) {
                rows.next();
                opGroupExists = rows.getInt(1) > 0;
            }
            if (!opGroupExists) {
                validateNoDanglingOpReferences(statement);
                statement.executeUpdate("INSERT INTO groups (name, prefix, suffix) VALUES ('op', NULL, NULL)");
                statement.executeUpdate("INSERT INTO group_permissions (group_name, node, value) VALUES ('op', '*', 'TRUE')");
                return;
            }
        }
        validateProtectedOpGroup(connection);
    }

    private static void validateNoDanglingOpReferences(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT (SELECT COUNT(*) FROM group_permissions WHERE group_name = 'op') + (SELECT COUNT(*) FROM memberships WHERE group_name = 'op') + (SELECT COUNT(*) FROM group_parents WHERE group_name = 'op' OR parent_name = 'op')")) {
            rows.next();
            if (rows.getInt(1) > 0) {
                throw new PermissionStorageException("SQLite op group references exist without protected op group");
            }
        }
    }

    private static void validateProtectedOpGroup(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT prefix, suffix FROM groups WHERE name = 'op'")) {
                if (!rows.next()) {
                    throw new PermissionStorageException("Missing protected op group");
                }
                if (rows.getString("prefix") != null || rows.getString("suffix") != null) {
                    throw new PermissionStorageException("SQLite op group must not store display values");
                }
            }

            int permissions = 0;
            boolean hasWildcardGrant = false;
            try (ResultSet rows = statement.executeQuery("SELECT node, value FROM group_permissions WHERE group_name = 'op' ORDER BY node")) {
                while (rows.next()) {
                    permissions++;
                    if ("*".equals(rows.getString("node")) && "TRUE".equals(rows.getString("value"))) {
                        hasWildcardGrant = true;
                    }
                }
            }
            if (permissions != 1 || !hasWildcardGrant) {
                throw new PermissionStorageException("SQLite op group must contain only *=TRUE");
            }

            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM group_parents WHERE group_name = 'op' OR parent_name = 'op'")) {
                rows.next();
                if (rows.getInt(1) > 0) {
                    throw new PermissionStorageException("SQLite op group must not store parent links");
                }
            }
        }
    }

    private static void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    /**
     * Read action that can throw {@link SQLException}.
     */
    @FunctionalInterface
    public interface SqliteReadAction<T> {

        T run(Connection connection) throws SQLException;
    }

    /**
     * Write action that can throw {@link SQLException}.
     */
    @FunctionalInterface
    public interface SqliteWriteAction {

        void run(Connection connection) throws SQLException;
    }
}
