package me.clutchy.clutchperms.common.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Platform-neutral ClutchPerms storage paths.
 */
public record ClutchPermsStoragePaths(Path configFile, Path databaseFile, Path backupRoot) {

    private static final String CONFIG_FILE_NAME = "config.json";

    private static final String DATABASE_FILE_NAME = "database.db";

    /**
     * Creates storage paths under one platform storage directory.
     *
     * @param storageDirectory directory that contains the live database, config, and backups directory
     * @return normalized storage paths
     */
    public static ClutchPermsStoragePaths inDirectory(Path storageDirectory) {
        Path normalizedStorageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory").toAbsolutePath().normalize();
        return new ClutchPermsStoragePaths(normalizedStorageDirectory.resolve(CONFIG_FILE_NAME), normalizedStorageDirectory.resolve(DATABASE_FILE_NAME),
                normalizedStorageDirectory.resolve("backups"));
    }

    /**
     * Normalizes every storage path.
     */
    public ClutchPermsStoragePaths {
        configFile = normalize(configFile, "configFile");
        databaseFile = normalize(databaseFile, "databaseFile");
        backupRoot = normalize(backupRoot, "backupRoot");
    }

    /**
     * Returns the directory containing the live database and config file.
     *
     * @return storage root
     */
    public Path storageRoot() {
        return databaseFile.getParent();
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
