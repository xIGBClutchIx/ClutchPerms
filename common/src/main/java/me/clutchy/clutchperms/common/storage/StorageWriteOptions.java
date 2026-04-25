package me.clutchy.clutchperms.common.storage;

import java.util.Objects;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;

/**
 * Options used when writing JSON-backed storage files.
 *
 * @param backupRetentionLimit newest backups retained per storage kind
 */
public record StorageWriteOptions(int backupRetentionLimit) {

    /**
     * Validates storage write options.
     */
    public StorageWriteOptions {
        if (backupRetentionLimit < ClutchPermsBackupConfig.MIN_RETENTION_LIMIT || backupRetentionLimit > ClutchPermsBackupConfig.MAX_RETENTION_LIMIT) {
            throw new IllegalArgumentException(
                    "backupRetentionLimit must be between " + ClutchPermsBackupConfig.MIN_RETENTION_LIMIT + " and " + ClutchPermsBackupConfig.MAX_RETENTION_LIMIT);
        }
    }

    /**
     * Returns default storage write options.
     *
     * @return default storage write options
     */
    public static StorageWriteOptions defaults() {
        return new StorageWriteOptions(ClutchPermsBackupConfig.DEFAULT_RETENTION_LIMIT);
    }

    /**
     * Requires non-null options, using defaults when none are supplied.
     *
     * @param options candidate options
     * @return supplied options or defaults
     */
    public static StorageWriteOptions defaultIfNull(StorageWriteOptions options) {
        return Objects.requireNonNullElseGet(options, StorageWriteOptions::defaults);
    }
}
