package me.clutchy.clutchperms.common.config;

/**
 * Scheduled database backup configuration.
 *
 * @param enabled whether automatic backups are enabled
 * @param intervalMinutes minutes between automatic backups
 * @param runOnStartup whether to create one backup when the server starts
 */
public record ClutchPermsBackupScheduleConfig(boolean enabled, int intervalMinutes, boolean runOnStartup) {

    /**
     * Default scheduled backup enabled state.
     */
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * Default scheduled backup interval.
     */
    public static final int DEFAULT_INTERVAL_MINUTES = 60;

    /**
     * Default startup backup state.
     */
    public static final boolean DEFAULT_RUN_ON_STARTUP = false;

    /**
     * Minimum allowed scheduled backup interval.
     */
    public static final int MIN_INTERVAL_MINUTES = 5;

    /**
     * Maximum allowed scheduled backup interval.
     */
    public static final int MAX_INTERVAL_MINUTES = 10080;

    /**
     * Validates scheduled backup configuration.
     */
    public ClutchPermsBackupScheduleConfig {
        if (intervalMinutes < MIN_INTERVAL_MINUTES || intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("backups.schedule.intervalMinutes must be between " + MIN_INTERVAL_MINUTES + " and " + MAX_INTERVAL_MINUTES);
        }
    }

    /**
     * Returns default scheduled backup configuration.
     *
     * @return default scheduled backup configuration
     */
    public static ClutchPermsBackupScheduleConfig defaults() {
        return new ClutchPermsBackupScheduleConfig(DEFAULT_ENABLED, DEFAULT_INTERVAL_MINUTES, DEFAULT_RUN_ON_STARTUP);
    }
}
