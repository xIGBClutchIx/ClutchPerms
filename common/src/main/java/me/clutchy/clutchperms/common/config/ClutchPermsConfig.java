package me.clutchy.clutchperms.common.config;

import java.util.Objects;

import me.clutchy.clutchperms.common.storage.StorageWriteOptions;

/**
 * Shared runtime configuration for ClutchPerms.
 *
 * @param backups backup-related configuration
 * @param commands command-output configuration
 */
public record ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsCommandConfig commands) {

    /**
     * Current config schema version.
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates runtime configuration.
     */
    public ClutchPermsConfig {
        backups = Objects.requireNonNull(backups, "backups");
        commands = Objects.requireNonNull(commands, "commands");
    }

    /**
     * Returns the default runtime configuration.
     *
     * @return default runtime configuration
     */
    public static ClutchPermsConfig defaults() {
        return new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), ClutchPermsCommandConfig.defaults());
    }

    /**
     * Converts this runtime config to storage write options.
     *
     * @return storage write options
     */
    public StorageWriteOptions storageWriteOptions() {
        return new StorageWriteOptions(backups.retentionLimit());
    }
}
