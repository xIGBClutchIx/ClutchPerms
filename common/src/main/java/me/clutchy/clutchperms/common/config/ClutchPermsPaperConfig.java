package me.clutchy.clutchperms.common.config;

/**
 * Paper-specific runtime configuration.
 *
 * @param replaceOpCommands whether Paper registers ClutchPerms-backed /op and /deop replacements
 */
public record ClutchPermsPaperConfig(boolean replaceOpCommands) {

    /**
     * Default Paper /op and /deop replacement state.
     */
    public static final boolean DEFAULT_REPLACE_OP_COMMANDS = true;

    /**
     * Returns default Paper configuration.
     *
     * @return default Paper configuration
     */
    public static ClutchPermsPaperConfig defaults() {
        return new ClutchPermsPaperConfig(DEFAULT_REPLACE_OP_COMMANDS);
    }
}
