package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupScheduleConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsPaperConfig;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

final class CommandCatalogs {

    static final List<ConfigEntry> CONFIG_ENTRIES = List.of(
            integerConfig("backups.retentionLimit", "newest database backups kept", ClutchPermsBackupConfig.MIN_RETENTION_LIMIT, ClutchPermsBackupConfig.MAX_RETENTION_LIMIT,
                    ClutchPermsBackupConfig.DEFAULT_RETENTION_LIMIT, config -> config.backups().retentionLimit(),
                    (config, value) -> new ClutchPermsConfig(new ClutchPermsBackupConfig(value, config.backups().schedule()), config.audit(), config.commands(), config.chat(),
                            config.paper())),
            booleanConfig("backups.schedule.enabled", "automatic database backups", ClutchPermsBackupScheduleConfig.DEFAULT_ENABLED,
                    config -> config.backups().schedule().enabled(),
                    (config, value) -> new ClutchPermsConfig(
                            new ClutchPermsBackupConfig(config.backups().retentionLimit(),
                                    new ClutchPermsBackupScheduleConfig(value, config.backups().schedule().intervalMinutes(), config.backups().schedule().runOnStartup())),
                            config.audit(), config.commands(), config.chat(), config.paper())),
            integerConfig("backups.schedule.intervalMinutes", "minutes between automatic database backups", ClutchPermsBackupScheduleConfig.MIN_INTERVAL_MINUTES,
                    ClutchPermsBackupScheduleConfig.MAX_INTERVAL_MINUTES, ClutchPermsBackupScheduleConfig.DEFAULT_INTERVAL_MINUTES,
                    config -> config.backups().schedule().intervalMinutes(),
                    (config, value) -> new ClutchPermsConfig(
                            new ClutchPermsBackupConfig(config.backups().retentionLimit(),
                                    new ClutchPermsBackupScheduleConfig(config.backups().schedule().enabled(), value, config.backups().schedule().runOnStartup())),
                            config.audit(), config.commands(), config.chat(), config.paper())),
            booleanConfig("backups.schedule.runOnStartup", "startup database backup", ClutchPermsBackupScheduleConfig.DEFAULT_RUN_ON_STARTUP,
                    config -> config.backups().schedule().runOnStartup(),
                    (config, value) -> new ClutchPermsConfig(
                            new ClutchPermsBackupConfig(config.backups().retentionLimit(),
                                    new ClutchPermsBackupScheduleConfig(config.backups().schedule().enabled(), config.backups().schedule().intervalMinutes(), value)),
                            config.audit(), config.commands(), config.chat(), config.paper())),
            booleanConfig("audit.retention.enabled", "automatic audit history retention pruning", ClutchPermsAuditRetentionConfig.DEFAULT_ENABLED,
                    config -> config.audit().enabled(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), new ClutchPermsAuditRetentionConfig(value, config.audit().maxAgeDays(), config.audit().maxEntries()),
                            config.commands(), config.chat(), config.paper())),
            integerConfig("audit.retention.maxAgeDays", "audit history days kept", ClutchPermsAuditRetentionConfig.MIN_MAX_AGE_DAYS,
                    ClutchPermsAuditRetentionConfig.MAX_MAX_AGE_DAYS, ClutchPermsAuditRetentionConfig.DEFAULT_MAX_AGE_DAYS, config -> config.audit().maxAgeDays(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), new ClutchPermsAuditRetentionConfig(config.audit().enabled(), value, config.audit().maxEntries()),
                            config.commands(), config.chat(), config.paper())),
            integerConfig("audit.retention.maxEntries", "newest audit history entries kept; 0 disables count retention", ClutchPermsAuditRetentionConfig.MIN_MAX_ENTRIES,
                    ClutchPermsAuditRetentionConfig.MAX_MAX_ENTRIES, ClutchPermsAuditRetentionConfig.DEFAULT_MAX_ENTRIES, config -> config.audit().maxEntries(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), new ClutchPermsAuditRetentionConfig(config.audit().enabled(), config.audit().maxAgeDays(), value),
                            config.commands(), config.chat(), config.paper())),
            integerConfig("commands.helpPageSize", "command rows shown per help page", ClutchPermsCommandConfig.MIN_PAGE_SIZE, ClutchPermsCommandConfig.MAX_PAGE_SIZE,
                    ClutchPermsCommandConfig.DEFAULT_HELP_PAGE_SIZE, config -> config.commands().helpPageSize(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), config.audit(), new ClutchPermsCommandConfig(value, config.commands().resultPageSize()),
                            config.chat(), config.paper())),
            integerConfig("commands.resultPageSize", "rows shown per list-result page", ClutchPermsCommandConfig.MIN_PAGE_SIZE, ClutchPermsCommandConfig.MAX_PAGE_SIZE,
                    ClutchPermsCommandConfig.DEFAULT_RESULT_PAGE_SIZE, config -> config.commands().resultPageSize(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), config.audit(), new ClutchPermsCommandConfig(config.commands().helpPageSize(), value), config.chat(),
                            config.paper())),
            booleanConfig("chat.enabled", "prefix and suffix chat formatting", ClutchPermsChatConfig.DEFAULT_ENABLED, config -> config.chat().enabled(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), config.audit(), config.commands(), new ClutchPermsChatConfig(value), config.paper())),
            booleanConfig("paper.replaceOpCommands", "Paper /op and /deop ClutchPerms replacements", ClutchPermsPaperConfig.DEFAULT_REPLACE_OP_COMMANDS,
                    config -> config.paper().replaceOpCommands(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), config.audit(), config.commands(), config.chat(), new ClutchPermsPaperConfig(value))));

    static final List<String> CONFIG_KEYS = CONFIG_ENTRIES.stream().map(ConfigEntry::key).toList();

    static final List<CommandHelpEntry> COMMAND_HELP = List.of(new CommandHelpEntry("help [page]", PermissionNodes.ADMIN_HELP, "Shows paged command help."),
            new CommandHelpEntry("status", PermissionNodes.ADMIN_STATUS, "Shows storage, counts, resolver cache, and bridge status."),
            new CommandHelpEntry("reload", PermissionNodes.ADMIN_RELOAD, "Reloads config and database storage, then refreshes runtime permissions."),
            new CommandHelpEntry("validate", PermissionNodes.ADMIN_VALIDATE, "Checks config and database storage without applying it."),
            new CommandHelpEntry("history [page]", PermissionNodes.ADMIN_HISTORY, "Lists command mutation history."),
            new CommandHelpEntry("history prune days <days>", PermissionNodes.ADMIN_HISTORY_PRUNE, "Deletes audit history older than the supplied days."),
            new CommandHelpEntry("history prune count <count>", PermissionNodes.ADMIN_HISTORY_PRUNE, "Keeps only the newest audit history entries."),
            new CommandHelpEntry("undo <id>", PermissionNodes.ADMIN_UNDO, "Reverts one undoable history entry."),
            new CommandHelpEntry("config list", PermissionNodes.ADMIN_CONFIG_VIEW, "Lists active runtime config values."),
            new CommandHelpEntry("config get <key>", PermissionNodes.ADMIN_CONFIG_VIEW, "Shows one config value."),
            new CommandHelpEntry("config set <key> <value>", PermissionNodes.ADMIN_CONFIG_SET, "Updates config and reloads runtime."),
            new CommandHelpEntry("config reset <key|all>", PermissionNodes.ADMIN_CONFIG_RESET, "Restores config defaults."),
            new CommandHelpEntry("backup create", PermissionNodes.ADMIN_BACKUP_CREATE, "Creates a database backup."),
            new CommandHelpEntry("backup list [page]", PermissionNodes.ADMIN_BACKUP_LIST, "Lists database backups."),
            new CommandHelpEntry("backup list page <page>", PermissionNodes.ADMIN_BACKUP_LIST, "Lists all backups on a specific page."),
            new CommandHelpEntry("backup schedule status", PermissionNodes.ADMIN_BACKUP_LIST, "Shows automatic backup schedule status."),
            new CommandHelpEntry("backup schedule <enable|disable>", PermissionNodes.ADMIN_CONFIG_SET, "Toggles automatic database backups."),
            new CommandHelpEntry("backup schedule interval <minutes>", PermissionNodes.ADMIN_CONFIG_SET, "Sets automatic backup interval."),
            new CommandHelpEntry("backup schedule run-now", PermissionNodes.ADMIN_BACKUP_CREATE, "Creates an immediate scheduled backup."),
            new CommandHelpEntry("backup restore <backup-file>", PermissionNodes.ADMIN_BACKUP_RESTORE, "Restores one validated database backup."),
            new CommandHelpEntry("user <target> info", PermissionNodes.ADMIN_USER_INFO, "Shows a quick user summary."),
            new CommandHelpEntry("user <target> list [page]", PermissionNodes.ADMIN_USER_LIST, "Lists direct user permissions."),
            new CommandHelpEntry("user <target> get <node>", PermissionNodes.ADMIN_USER_GET, "Shows one direct user permission."),
            new CommandHelpEntry("user <target> set <node> <true|false>", PermissionNodes.ADMIN_USER_SET, "Sets one direct user permission."),
            new CommandHelpEntry("user <target> clear <node>", PermissionNodes.ADMIN_USER_CLEAR, "Clears one direct user permission."),
            new CommandHelpEntry("user <target> clear-all", PermissionNodes.ADMIN_USER_CLEAR_ALL, "Clears every direct user permission."),
            new CommandHelpEntry("user <target> check <node>", PermissionNodes.ADMIN_USER_CHECK, "Shows the effective permission result."),
            new CommandHelpEntry("user <target> explain <node>", PermissionNodes.ADMIN_USER_EXPLAIN, "Explains matching assignments and the winner."),
            new CommandHelpEntry("user <target> groups [page]", PermissionNodes.ADMIN_USER_GROUPS, "Lists explicit and implicit groups for a user."),
            new CommandHelpEntry("user <target> group <add|remove> <group>", PermissionNodes.ADMIN_USER_GROUPS, "Changes explicit group membership."),
            new CommandHelpEntry("user <target> <prefix|suffix> get", PermissionNodes.ADMIN_USER_DISPLAY_VIEW, "Shows direct and effective user display values."),
            new CommandHelpEntry("user <target> <prefix|suffix> set <text>", PermissionNodes.ADMIN_USER_DISPLAY_SET, "Sets direct user chat display text."),
            new CommandHelpEntry("user <target> <prefix|suffix> clear", PermissionNodes.ADMIN_USER_DISPLAY_CLEAR, "Clears direct user chat display text."),
            new CommandHelpEntry("group list [page]", PermissionNodes.ADMIN_GROUP_LIST, "Lists groups."),
            new CommandHelpEntry("group <group> info", PermissionNodes.ADMIN_GROUP_INFO, "Shows a quick group summary."),
            new CommandHelpEntry("group <group> <create|delete>", PermissionNodes.ADMIN_GROUP_VIEW, "Creates or deletes a group."),
            new CommandHelpEntry("group <group> rename <new-group>", PermissionNodes.ADMIN_GROUP_RENAME, "Renames a group and updates references."),
            new CommandHelpEntry("group <group> list [page]", PermissionNodes.ADMIN_GROUP_VIEW, "Lists group permissions, parents, and members."),
            new CommandHelpEntry("group <group> members [page]", PermissionNodes.ADMIN_GROUP_MEMBERS, "Lists explicit group members."),
            new CommandHelpEntry("group <group> parents [page]", PermissionNodes.ADMIN_GROUP_PARENTS, "Lists parent groups."),
            new CommandHelpEntry("group <group> <get|clear> <node>", PermissionNodes.ADMIN_GROUP_GET, "Reads or clears one group permission."),
            new CommandHelpEntry("group <group> set <node> <true|false>", PermissionNodes.ADMIN_GROUP_SET, "Sets one group permission."),
            new CommandHelpEntry("group <group> clear-all", PermissionNodes.ADMIN_GROUP_CLEAR_ALL, "Clears every direct group permission."),
            new CommandHelpEntry("group <group> parent <add|remove> <parent>", PermissionNodes.ADMIN_GROUP_PARENTS, "Changes group inheritance."),
            new CommandHelpEntry("group <group> <prefix|suffix> get", PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, "Shows group chat display values."),
            new CommandHelpEntry("group <group> <prefix|suffix> set <text>", PermissionNodes.ADMIN_GROUP_DISPLAY_SET, "Sets group chat display text."),
            new CommandHelpEntry("group <group> <prefix|suffix> clear", PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR, "Clears group chat display text."),
            new CommandHelpEntry("track list [page]", PermissionNodes.ADMIN_TRACK_LIST, "Lists tracks."),
            new CommandHelpEntry("track <track> info", PermissionNodes.ADMIN_TRACK_INFO, "Shows a quick track summary."),
            new CommandHelpEntry("track <track> <create|delete>", PermissionNodes.ADMIN_TRACK_CREATE, "Creates or deletes a track."),
            new CommandHelpEntry("track <track> rename <new-track>", PermissionNodes.ADMIN_TRACK_RENAME, "Renames a track."),
            new CommandHelpEntry("track <track> list [page]", PermissionNodes.ADMIN_TRACK_VIEW, "Lists ordered groups on a track."),
            new CommandHelpEntry("track <track> append <group>", PermissionNodes.ADMIN_TRACK_APPEND, "Adds one group at the end of a track."),
            new CommandHelpEntry("track <track> insert <position> <group>", PermissionNodes.ADMIN_TRACK_INSERT, "Inserts one group at a track position."),
            new CommandHelpEntry("track <track> move <group> <position>", PermissionNodes.ADMIN_TRACK_MOVE, "Moves one track group to a new position."),
            new CommandHelpEntry("track <track> remove <group>", PermissionNodes.ADMIN_TRACK_REMOVE, "Removes one group from a track."),
            new CommandHelpEntry("user <target> tracks [page]", PermissionNodes.ADMIN_USER_TRACK_LIST, "Lists the user's current track positions."),
            new CommandHelpEntry("user <target> track promote <track>", PermissionNodes.ADMIN_USER_TRACK_PROMOTE, "Promotes a user on one track."),
            new CommandHelpEntry("user <target> track demote <track>", PermissionNodes.ADMIN_USER_TRACK_DEMOTE, "Demotes a user on one track."),
            new CommandHelpEntry("users list [page]", PermissionNodes.ADMIN_USERS_LIST, "Lists stored user metadata."),
            new CommandHelpEntry("users search <name> [page]", PermissionNodes.ADMIN_USERS_SEARCH, "Searches stored last-known names."),
            new CommandHelpEntry("nodes list [page]", PermissionNodes.ADMIN_NODES_LIST, "Lists known permission nodes."),
            new CommandHelpEntry("nodes search <query> [page]", PermissionNodes.ADMIN_NODES_SEARCH, "Searches known nodes and descriptions."),
            new CommandHelpEntry("nodes add <node> [description]", PermissionNodes.ADMIN_NODES_ADD, "Adds or updates a manual known node."),
            new CommandHelpEntry("nodes remove <node>", PermissionNodes.ADMIN_NODES_REMOVE, "Removes a manual known node."));

    static List<String> backupUsages() {
        return List.of("backup create", "backup list [page]", "backup schedule status", "backup schedule <enable|disable>", "backup schedule interval <minutes>",
                "backup schedule run-now", "backup restore <backup-file>");
    }

    static List<String> backupScheduleUsages() {
        return List.of("backup schedule status", "backup schedule enable", "backup schedule disable", "backup schedule interval <minutes>", "backup schedule run-now");
    }

    static List<String> configUsages() {
        return List.of("config list", "config get <key>", "config set <key> <value>", "config reset <key|all>");
    }

    static List<String> userRootUsages() {
        return List.of("user <target> <info|list|groups>", "user <target> <get|clear|check|explain> <node>", "user <target> set <node> <true|false>", "user <target> clear-all",
                "user <target> group <add|remove> <group>", "user <target> tracks", "user <target> track <promote|demote> <track>", "user <target> <prefix|suffix> get|set|clear");
    }

    static List<String> userTargetUsages(String target) {
        return List.of("user " + target + " <info|list|groups>", "user " + target + " <get|clear|check|explain> <node>", "user " + target + " set <node> <true|false>",
                "user " + target + " clear-all", "user " + target + " group <add|remove> <group>", "user " + target + " tracks",
                "user " + target + " track <promote|demote> <track>", "user " + target + " <prefix|suffix> get|set|clear");
    }

    static List<String> groupRootUsages() {
        return List.of("group list", "group <group> <create|delete|info|list|members|parents>", "group <group> <get|clear> <node>", "group <group> set <node> <true|false>",
                "group <group> clear-all", "group <group> rename <new-group>", "group <group> parent <add|remove> <parent>", "group <group> <prefix|suffix> get|set|clear");
    }

    static List<String> groupTargetUsages(String group) {
        return List.of("group " + group + " <create|delete|info|list|members|parents>", "group " + group + " <get|clear> <node>", "group " + group + " set <node> <true|false>",
                "group " + group + " clear-all", "group " + group + " rename <new-group>", "group " + group + " parent <add|remove> <parent>",
                "group " + group + " <prefix|suffix> get|set|clear");
    }

    static List<String> trackRootUsages() {
        return List.of("track list", "track <track> <create|delete|info|list>", "track <track> rename <new-track>", "track <track> append <group>",
                "track <track> insert <position> <group>", "track <track> move <group> <position>", "track <track> remove <group>");
    }

    static List<String> trackTargetUsages(String track) {
        return List.of("track " + track + " <create|delete|info|list>", "track " + track + " rename <new-track>", "track " + track + " append <group>",
                "track " + track + " insert <position> <group>", "track " + track + " move <group> <position>", "track " + track + " remove <group>");
    }

    static List<String> usersUsages() {
        return List.of("users list", "users search <name>");
    }

    static List<String> nodesUsages() {
        return List.of("nodes list", "nodes search <query>", "nodes add <node> [description]", "nodes remove <node>");
    }

    static Optional<ConfigEntry> findConfigEntry(String key) {
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        return CONFIG_ENTRIES.stream().filter(entry -> entry.key().toLowerCase(Locale.ROOT).equals(normalizedKey)).findFirst();
    }

    static ConfigEntry configEntry(String key) {
        return findConfigEntry(key).orElseThrow(() -> new IllegalStateException("Missing config entry " + key));
    }

    private static ConfigEntry integerConfig(String key, String description, int minimum, int maximum, int defaultValue, ToIntFunction<ClutchPermsConfig> valueGetter,
            BiFunction<ClutchPermsConfig, Integer, ClutchPermsConfig> valueSetter) {
        return new ConfigEntry(key, description, "range " + minimum + "-" + maximum, "an integer from " + minimum + " to " + maximum,
                "must be an integer between " + minimum + " and " + maximum + ".", Integer.toString(defaultValue), config -> Integer.toString(valueGetter.applyAsInt(config)),
                rawValue -> {
                    int value;
                    try {
                        value = Integer.parseInt(rawValue);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("config value must be an integer", exception);
                    }
                    if (value < minimum || value > maximum) {
                        throw new IllegalArgumentException("config value is out of range");
                    }
                    return Integer.toString(value);
                }, (config, value) -> valueSetter.apply(config, Integer.parseInt(value)));
    }

    private static ConfigEntry booleanConfig(String key, String description, boolean defaultValue, Function<ClutchPermsConfig, Boolean> valueGetter,
            BooleanConfigValueSetter valueSetter) {
        return new ConfigEntry(key, description, "values true/false or on/off", "true or false", "must be true/false or on/off.", Boolean.toString(defaultValue),
                config -> Boolean.toString(valueGetter.apply(config)), CommandCatalogs::parseBooleanConfigValue,
                (config, value) -> valueSetter.apply(config, Boolean.parseBoolean(value)));
    }

    private static String parseBooleanConfigValue(String rawValue) {
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "enabled" -> "true";
            case "false", "off", "no", "disabled" -> "false";
            default -> throw new IllegalArgumentException("config value must be a boolean");
        };
    }

    private CommandCatalogs() {
    }

    record CommandHelpEntry(String syntax, String permission, String description) {
    }

    record ConfigEntry(String key, String description, String displayHint, String inputHint, String errorHint, String defaultValue, Function<ClutchPermsConfig, String> valueGetter,
            ConfigValueParser valueParser, ConfigValueSetter valueSetter) {

        String value(ClutchPermsConfig config) {
            return valueGetter.apply(config);
        }

        String normalizeValue(String rawValue) {
            return valueParser.parse(rawValue.trim());
        }

        ClutchPermsConfig withValue(ClutchPermsConfig config, String value) {
            return valueSetter.apply(config, value);
        }
    }

    @FunctionalInterface
    interface ConfigValueParser {

        String parse(String rawValue);
    }

    @FunctionalInterface
    interface ConfigValueSetter {

        ClutchPermsConfig apply(ClutchPermsConfig config, String value);
    }

    @FunctionalInterface
    interface BooleanConfigValueSetter {

        ClutchPermsConfig apply(ClutchPermsConfig config, boolean value);
    }
}
