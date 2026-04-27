package me.clutchy.clutchperms.common.config;

/**
 * Backup-related runtime configuration.
 *
 * @param retentionLimit newest database backups retained
 */
public record ClutchPermsBackupConfig(int retentionLimit) {

    /**
     * Default newest-backup retention.
     */
    public static final int DEFAULT_RETENTION_LIMIT = 10;

    /**
     * Minimum allowed backup retention.
     */
    public static final int MIN_RETENTION_LIMIT = 1;

    /**
     * Maximum allowed backup retention.
     */
    public static final int MAX_RETENTION_LIMIT = 1000;

    /**
     * Validates backup configuration.
     */
    public ClutchPermsBackupConfig {
        if (retentionLimit < MIN_RETENTION_LIMIT || retentionLimit > MAX_RETENTION_LIMIT) {
            throw new IllegalArgumentException("backups.retentionLimit must be between " + MIN_RETENTION_LIMIT + " and " + MAX_RETENTION_LIMIT);
        }
    }

    /**
     * Returns default backup configuration.
     *
     * @return default backup configuration
     */
    public static ClutchPermsBackupConfig defaults() {
        return new ClutchPermsBackupConfig(DEFAULT_RETENTION_LIMIT);
    }
}
