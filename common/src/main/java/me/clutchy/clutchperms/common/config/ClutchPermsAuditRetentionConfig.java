package me.clutchy.clutchperms.common.config;

/**
 * Audit history retention configuration.
 *
 * @param enabled whether automatic audit retention pruning is enabled
 * @param maxAgeDays maximum audit row age in days
 * @param maxEntries maximum audit rows retained, or {@code 0} for no count cap
 */
public record ClutchPermsAuditRetentionConfig(boolean enabled, int maxAgeDays, int maxEntries) {

    /**
     * Default automatic audit retention state.
     */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Default maximum audit age in days.
     */
    public static final int DEFAULT_MAX_AGE_DAYS = 90;

    /**
     * Default maximum audit entries; {@code 0} disables count retention.
     */
    public static final int DEFAULT_MAX_ENTRIES = 0;

    /**
     * Minimum allowed audit retention age.
     */
    public static final int MIN_MAX_AGE_DAYS = 1;

    /**
     * Maximum allowed audit retention age.
     */
    public static final int MAX_MAX_AGE_DAYS = 3650;

    /**
     * Minimum allowed audit entry cap.
     */
    public static final int MIN_MAX_ENTRIES = 0;

    /**
     * Maximum allowed audit entry cap.
     */
    public static final int MAX_MAX_ENTRIES = 1_000_000;

    /**
     * Validates audit retention configuration.
     */
    public ClutchPermsAuditRetentionConfig {
        if (maxAgeDays < MIN_MAX_AGE_DAYS || maxAgeDays > MAX_MAX_AGE_DAYS) {
            throw new IllegalArgumentException("audit.retention.maxAgeDays must be between " + MIN_MAX_AGE_DAYS + " and " + MAX_MAX_AGE_DAYS);
        }
        if (maxEntries < MIN_MAX_ENTRIES || maxEntries > MAX_MAX_ENTRIES) {
            throw new IllegalArgumentException("audit.retention.maxEntries must be between " + MIN_MAX_ENTRIES + " and " + MAX_MAX_ENTRIES);
        }
    }

    /**
     * Returns default audit retention configuration.
     *
     * @return default audit retention configuration
     */
    public static ClutchPermsAuditRetentionConfig defaults() {
        return new ClutchPermsAuditRetentionConfig(DEFAULT_ENABLED, DEFAULT_MAX_AGE_DAYS, DEFAULT_MAX_ENTRIES);
    }
}
