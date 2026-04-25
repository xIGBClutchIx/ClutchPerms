package me.clutchy.clutchperms.common.config;

/**
 * Command-output runtime configuration.
 *
 * @param helpPageSize rows shown per help page
 * @param resultPageSize rows shown per list-result page
 */
public record ClutchPermsCommandConfig(int helpPageSize, int resultPageSize) {

    /**
     * Default help rows shown per page.
     */
    public static final int DEFAULT_HELP_PAGE_SIZE = 7;

    /**
     * Default result rows shown per page.
     */
    public static final int DEFAULT_RESULT_PAGE_SIZE = 8;

    /**
     * Minimum allowed command page size.
     */
    public static final int MIN_PAGE_SIZE = 1;

    /**
     * Maximum allowed command page size.
     */
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * Validates command configuration.
     */
    public ClutchPermsCommandConfig {
        validatePageSize("commands.helpPageSize", helpPageSize);
        validatePageSize("commands.resultPageSize", resultPageSize);
    }

    /**
     * Returns default command configuration.
     *
     * @return default command configuration
     */
    public static ClutchPermsCommandConfig defaults() {
        return new ClutchPermsCommandConfig(DEFAULT_HELP_PAGE_SIZE, DEFAULT_RESULT_PAGE_SIZE);
    }

    private static void validatePageSize(String name, int value) {
        if (value < MIN_PAGE_SIZE || value > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(name + " must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
        }
    }
}
