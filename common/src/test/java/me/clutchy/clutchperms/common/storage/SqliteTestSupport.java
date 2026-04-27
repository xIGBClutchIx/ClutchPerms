package me.clutchy.clutchperms.common.storage;

import java.nio.file.Path;

/**
 * Small SQLite helpers shared by common storage tests.
 */
public final class SqliteTestSupport {

    public static Path databaseFile(Path directory) {
        return directory.resolve("database.db");
    }

    public static SqliteStore open(Path databaseFile) {
        return SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE);
    }

    public static SqliteStore openDirectory(Path directory) {
        return open(databaseFile(directory));
    }

    private SqliteTestSupport() {
    }
}
