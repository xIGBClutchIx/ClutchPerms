package me.clutchy.clutchperms.common.config;

/**
 * Chat-display runtime configuration.
 *
 * @param enabled whether ClutchPerms formats chat with prefixes and suffixes
 */
public record ClutchPermsChatConfig(boolean enabled) {

    /**
     * Default chat display formatting state.
     */
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Returns default chat configuration.
     *
     * @return default chat configuration
     */
    public static ClutchPermsChatConfig defaults() {
        return new ClutchPermsChatConfig(DEFAULT_ENABLED);
    }
}
