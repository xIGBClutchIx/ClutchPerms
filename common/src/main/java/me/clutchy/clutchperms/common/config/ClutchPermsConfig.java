package me.clutchy.clutchperms.common.config;

import java.util.Objects;

/**
 * Shared runtime configuration for ClutchPerms.
 *
 * @param backups backup-related configuration
 * @param audit audit-related configuration
 * @param commands command-output configuration
 * @param chat chat-display configuration
 * @param paper Paper-specific configuration
 */
public record ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsAuditRetentionConfig audit, ClutchPermsCommandConfig commands, ClutchPermsChatConfig chat,
        ClutchPermsPaperConfig paper) {

    /**
     * Current config schema version.
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates runtime configuration.
     */
    public ClutchPermsConfig {
        backups = Objects.requireNonNull(backups, "backups");
        audit = Objects.requireNonNull(audit, "audit");
        commands = Objects.requireNonNull(commands, "commands");
        chat = Objects.requireNonNull(chat, "chat");
        paper = Objects.requireNonNull(paper, "paper");
    }

    /**
     * Creates a runtime config with default audit retention settings.
     *
     * @param backups backup-related configuration
     * @param commands command-output configuration
     * @param chat chat-display configuration
     * @param paper Paper-specific configuration
     */
    public ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsCommandConfig commands, ClutchPermsChatConfig chat, ClutchPermsPaperConfig paper) {
        this(backups, ClutchPermsAuditRetentionConfig.defaults(), commands, chat, paper);
    }

    /**
     * Creates a runtime config with default Paper settings.
     *
     * @param backups backup-related configuration
     * @param commands command-output configuration
     * @param chat chat-display configuration
     */
    public ClutchPermsConfig(ClutchPermsBackupConfig backups, ClutchPermsCommandConfig commands, ClutchPermsChatConfig chat) {
        this(backups, commands, chat, ClutchPermsPaperConfig.defaults());
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
        return new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), ClutchPermsAuditRetentionConfig.defaults(), ClutchPermsCommandConfig.defaults(),
                ClutchPermsChatConfig.defaults(), ClutchPermsPaperConfig.defaults());
    }

}
