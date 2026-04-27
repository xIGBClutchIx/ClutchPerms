package me.clutchy.clutchperms.common.config;

import java.util.Objects;

/**
 * Shared runtime configuration for ClutchPerms.
 *
 * @param backups backup-related configuration
 * @param commands command-output configuration
 * @param chat chat-display configuration
 */
public record ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsCommandConfig commands, ClutchPermsChatConfig chat) {

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
        chat = Objects.requireNonNull(chat, "chat");
    }

    /**
     * Creates a runtime config with default chat settings.
     *
     * @param backups backup-related configuration
     * @param commands command-output configuration
     */
    public ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsCommandConfig commands) {
        this(backups, commands, ClutchPermsChatConfig.defaults());
    }

    /**
     * Returns the default runtime configuration.
     *
     * @return default runtime configuration
     */
    public static ClutchPermsConfig defaults() {
        return new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), ClutchPermsCommandConfig.defaults(), ClutchPermsChatConfig.defaults());
    }

}
