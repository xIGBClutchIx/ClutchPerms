package me.clutchy.clutchperms.common.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import me.clutchy.clutchperms.common.audit.AuditEntry;
import me.clutchy.clutchperms.common.audit.AuditLogRecord;
import me.clutchy.clutchperms.common.audit.AuditLogRetention;
import me.clutchy.clutchperms.common.command.subcommand.AuthorizedCommand;
import me.clutchy.clutchperms.common.command.subcommand.BackupSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.ConfigSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.GroupSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.NodesSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.TrackSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.UserSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.UsersSubcommand;
import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupScheduleConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsPaperConfig;
import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayResolution;
import me.clutchy.clutchperms.common.display.DisplaySlot;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionExplanation;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.permission.PermissionResolverCacheStats;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupStatus;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

/**
 * Builds the shared Brigadier command tree for ClutchPerms platform adapters.
 */
public final class ClutchPermsCommands {

    /**
     * Root command literal registered by every platform adapter.
     */
    public static final String ROOT_LITERAL = "clutchperms";

    /**
     * Root command aliases registered by every platform adapter.
     */
    public static final List<String> ROOT_ALIASES = List.of("cperms", "perms");

    /**
     * Root command literals registered by every platform adapter, including the primary command and aliases.
     */
    public static final List<String> ROOT_LITERALS = List.of(ROOT_LITERAL, "cperms", "perms");

    /**
     * Health line returned by the status command.
     */
    public static final String STATUS_MESSAGE = CommandLang.STATUS;

    private static final String TARGET_ARGUMENT = CommandArguments.TARGET;

    private static final String NODE_ARGUMENT = CommandArguments.NODE;

    private static final String ASSIGNMENT_ARGUMENT = CommandArguments.ASSIGNMENT;

    private static final String NAME_ARGUMENT = CommandArguments.NAME;

    private static final String QUERY_ARGUMENT = CommandArguments.QUERY;

    private static final String GROUP_ARGUMENT = CommandArguments.GROUP;

    private static final String NEW_GROUP_ARGUMENT = CommandArguments.NEW_GROUP;

    private static final String PARENT_ARGUMENT = CommandArguments.PARENT;

    private static final String TRACK_ARGUMENT = CommandArguments.TRACK;

    private static final String NEW_TRACK_ARGUMENT = CommandArguments.NEW_TRACK;

    private static final String POSITION_ARGUMENT = CommandArguments.POSITION;

    private static final String BACKUP_FILE_ARGUMENT = CommandArguments.BACKUP_FILE;

    private static final String CONFIG_KEY_ARGUMENT = CommandArguments.CONFIG_KEY;

    private static final String CONFIG_VALUE_ARGUMENT = CommandArguments.CONFIG_VALUE;

    private static final String DISPLAY_VALUE_ARGUMENT = CommandArguments.DISPLAY_VALUE;

    private static final String PAGE_ARGUMENT = CommandArguments.PAGE;

    private static final String AUDIT_ID_ARGUMENT = "id";

    private static final String DAYS_ARGUMENT = "days";

    private static final String COUNT_ARGUMENT = "count";

    private static final String UNKNOWN_ARGUMENT = CommandArguments.UNKNOWN;

    private static final int TARGET_MATCH_LIMIT = 5;

    private static final int SUMMARY_VALUE_LIMIT = 5;

    private static final Duration DESTRUCTIVE_CONFIRMATION_TTL = Duration.ofSeconds(30);

    private static final Object CONSOLE_CONFIRMATION_SOURCE = new Object();

    private static final Map<ConfirmationSource, PendingConfirmation> PENDING_CONFIRMATIONS = new HashMap<>();

    private static final Gson GSON = new Gson();

    private static final ThreadLocal<CommandSubject> RESOLVED_SUBJECT_OVERRIDE = new ThreadLocal<>();

    private static Clock confirmationClock = Clock.systemUTC();

    private static final SimpleCommandExceptionType FEEDBACK_MESSAGES = new SimpleCommandExceptionType(new LiteralMessage("command feedback"));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage(CommandLang.invalidNode(node).plainText()));

    private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(value -> new LiteralMessage(CommandLang.invalidValue(value).plainText()));

    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType VALIDATE_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType PERMISSION_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType GROUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType NODE_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType TRACK_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType BACKUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType CONFIG_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType DISPLAY_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType AUDIT_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final List<ConfigEntry> CONFIG_ENTRIES = List.of(
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

    private static final List<String> CONFIG_KEYS = CONFIG_ENTRIES.stream().map(ConfigEntry::key).toList();

    private static final List<CommandHelpEntry> COMMAND_HELP = List.of(new CommandHelpEntry("help [page]", PermissionNodes.ADMIN_HELP, "Shows paged command help."),
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

    /**
     * Creates the root ClutchPerms command node for a platform source type.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment) {
        return create(environment, ROOT_LITERAL);
    }

    /**
     * Creates a ClutchPerms command node for one root literal.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        return builder(environment, rootLiteral).build();
    }

    /**
     * Creates a direct shortcut command that mutates explicit membership in the protected {@code op} group.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param addMembership {@code true} to add the target to {@code op}; {@code false} to remove the target
     * @param requiredPermission permission required for player sources
     * @param <S> platform command source type
     * @return built shortcut command node
     */
    public static <S> LiteralCommandNode<S> createOpGroupShortcut(ClutchPermsCommandEnvironment<S> environment, String rootLiteral, boolean addMembership,
            String requiredPermission) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
        String literal = normalizeRootLiteral(rootLiteral);
        return LiteralArgumentBuilder.<S>literal(literal).requires(source -> canUse(environment, source, requiredPermission))
                .executes(context -> executeAuthorized(environment, context, requiredPermission, source -> sendOpShortcutUsage(environment, context)))
                .then(RequiredArgumentBuilder.<S, String>argument(TARGET_ARGUMENT, StringArgumentType.word())
                        .suggests((context, builder) -> UserSubcommand.suggestUserTargets(environment, context.getSource(), builder))
                        .executes(context -> executeAuthorizedForSubject(environment, context, requiredPermission,
                                source -> mutateOpGroupMembership(environment, context, addMembership))))
                .build();
    }

    /**
     * Creates the root ClutchPerms command builder for platform dispatchers that register builders directly.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment) {
        return builder(environment, ROOT_LITERAL);
    }

    /**
     * Creates a ClutchPerms command builder for one root literal.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        Objects.requireNonNull(environment, "environment");
        String literal = normalizeRootLiteral(rootLiteral);
        AuthorizedCommand<S> authorized = new AuthorizedCommand<>() {

            @Override
            public int run(CommandContext<S> context, String requiredPermission, Command<S> command) throws CommandSyntaxException {
                return executeAuthorized(environment, context, requiredPermission, source -> command.run(context));
            }

            @Override
            public boolean canUse(S source, String requiredPermission) {
                return ClutchPermsCommands.canUse(environment, source, requiredPermission);
            }
        };
        AuthorizedCommand<S> targetAuthorized = new AuthorizedCommand<>() {

            @Override
            public int run(CommandContext<S> context, String requiredPermission, Command<S> command) throws CommandSyntaxException {
                return executeAuthorizedForSubject(environment, context, requiredPermission, source -> command.run(context));
            }

            @Override
            public boolean canUse(S source, String requiredPermission) {
                return ClutchPermsCommands.canUse(environment, source, requiredPermission);
            }
        };

        return LiteralArgumentBuilder.<S>literal(literal)
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context)))
                .then(helpCommand(environment)).then(statusCommand(environment)).then(reloadCommand(environment)).then(validateCommand(environment))
                .then(historyCommand(environment)).then(undoCommand(environment)).then(ConfigSubcommand.builder(authorized, configHandlers(environment), CONFIG_KEYS))
                .then(BackupSubcommand.builder(environment, authorized, backupHandlers(environment)))
                .then(UserSubcommand.builder(environment, authorized, targetAuthorized, userHandlers(environment),
                        (context, builder) -> suggestPermissionNodes(environment, context, builder),
                        (context, builder) -> suggestPermissionAssignment(environment, context, builder)))
                .then(GroupSubcommand.builder(environment, authorized, groupHandlers(environment), (context, builder) -> suggestPermissionNodes(environment, context, builder),
                        (context, builder) -> suggestPermissionAssignment(environment, context, builder)))
                .then(TrackSubcommand.builder(environment, authorized, trackHandlers(environment))).then(UsersSubcommand.builder(authorized, usersHandlers(environment)))
                .then(NodesSubcommand.builder(environment, authorized, nodesHandlers(environment)));
    }

    private static String normalizeRootLiteral(String rootLiteral) {
        String literal = Objects.requireNonNull(rootLiteral, "rootLiteral").trim();
        if (literal.isEmpty()) {
            throw new IllegalArgumentException("root literal must not be blank");
        }
        return literal;
    }

    static void resetDestructiveConfirmationsForTests() {
        synchronized (PENDING_CONFIRMATIONS) {
            PENDING_CONFIRMATIONS.clear();
            confirmationClock = Clock.systemUTC();
        }
    }

    static void setConfirmationClockForTests(Clock clock) {
        synchronized (PENDING_CONFIRMATIONS) {
            confirmationClock = Objects.requireNonNull(clock, "clock");
            PENDING_CONFIRMATIONS.clear();
        }
    }

    private static <S> LiteralArgumentBuilder<S> statusCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("status").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_STATUS))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_STATUS, source -> sendStatus(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> helpCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("help").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_HELP))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context)))
                .then(RequiredArgumentBuilder.<S, String>argument(PAGE_ARGUMENT, StringArgumentType.word())
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> reloadCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("reload").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_RELOAD))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_RELOAD, source -> reloadStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> validateCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("validate").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_VALIDATE))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_VALIDATE, source -> validateStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> historyCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("history").requires(source -> canUseAny(environment, source, PermissionNodes.ADMIN_HISTORY, PermissionNodes.ADMIN_HISTORY_PRUNE))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HISTORY, source -> listHistory(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("prune").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_HISTORY_PRUNE))
                        .then(LiteralArgumentBuilder.<S>literal("days")
                                .then(RequiredArgumentBuilder.<S, Integer>argument(DAYS_ARGUMENT, IntegerArgumentType.integer(1)).executes(
                                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HISTORY_PRUNE, source -> pruneHistoryDays(environment, context)))))
                        .then(LiteralArgumentBuilder.<S>literal("count").then(RequiredArgumentBuilder.<S, Integer>argument(COUNT_ARGUMENT, IntegerArgumentType.integer(1)).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HISTORY_PRUNE, source -> pruneHistoryCount(environment, context))))))
                .then(RequiredArgumentBuilder.<S, String>argument(PAGE_ARGUMENT, StringArgumentType.word())
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HISTORY, source -> listHistory(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> undoCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("undo").requires(source -> canUse(environment, source, PermissionNodes.ADMIN_UNDO))
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_UNDO, source -> sendUndoUsage(environment, context)))
                .then(RequiredArgumentBuilder.<S, Long>argument(AUDIT_ID_ARGUMENT, LongArgumentType.longArg(1))
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_UNDO, source -> undoAuditEntry(environment, context))));
    }

    private static <S> ConfigSubcommand.Handlers<S> configHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new ConfigSubcommand.Handlers<>() {

            @Override
            public int usage(CommandContext<S> context) {
                return sendConfigUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) {
                return listConfig(environment, context);
            }

            @Override
            public int getUsage(CommandContext<S> context) {
                return sendConfigGetUsage(environment, context);
            }

            @Override
            public int get(CommandContext<S> context) throws CommandSyntaxException {
                return getConfig(environment, context);
            }

            @Override
            public int setKeyUsage(CommandContext<S> context) {
                return sendConfigSetKeyUsage(environment, context);
            }

            @Override
            public int setValueUsage(CommandContext<S> context) throws CommandSyntaxException {
                return sendConfigSetValueUsage(environment, context);
            }

            @Override
            public int set(CommandContext<S> context) throws CommandSyntaxException {
                return setConfig(environment, context);
            }

            @Override
            public int resetUsage(CommandContext<S> context) {
                return sendConfigResetUsage(environment, context);
            }

            @Override
            public int reset(CommandContext<S> context) throws CommandSyntaxException {
                return resetConfig(environment, context);
            }

            @Override
            public int unknownUsage(CommandContext<S> context) {
                return sendUnknownConfigUsage(environment, context);
            }
        };
    }

    private static <S> BackupSubcommand.Handlers<S> backupHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new BackupSubcommand.Handlers<>() {

            @Override
            public int usage(CommandContext<S> context) {
                return sendBackupUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listBackups(environment, context);
            }

            @Override
            public int create(CommandContext<S> context) throws CommandSyntaxException {
                return createBackup(environment, context);
            }

            @Override
            public int scheduleStatus(CommandContext<S> context) throws CommandSyntaxException {
                return showBackupScheduleStatus(environment, context);
            }

            @Override
            public int scheduleEnable(CommandContext<S> context) throws CommandSyntaxException {
                return setBackupScheduleEnabled(environment, context, true);
            }

            @Override
            public int scheduleDisable(CommandContext<S> context) throws CommandSyntaxException {
                return setBackupScheduleEnabled(environment, context, false);
            }

            @Override
            public int scheduleIntervalUsage(CommandContext<S> context) {
                return sendUsage(environment, context, "Missing interval minutes.", "Set automatic backup interval in minutes.", List.of("backup schedule interval <minutes>"));
            }

            @Override
            public int scheduleInterval(CommandContext<S> context) throws CommandSyntaxException {
                return setBackupScheduleInterval(environment, context);
            }

            @Override
            public int scheduleRunNow(CommandContext<S> context) throws CommandSyntaxException {
                return createScheduledBackup(environment, context);
            }

            @Override
            public int scheduleUnknownUsage(CommandContext<S> context) {
                return sendUsage(environment, context, "Unknown backup schedule command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                        "Backup schedule supports status, enable, disable, interval, and run-now.", backupScheduleUsages());
            }

            @Override
            public int listPageUsage(CommandContext<S> context) {
                return sendUsage(environment, context, "Missing page number.", "Choose a backup list page.", List.of("backup list page <page>"));
            }

            @Override
            public int restoreFileUsage(CommandContext<S> context) throws CommandSyntaxException {
                return sendBackupRestoreFileUsage(environment, context);
            }

            @Override
            public int restore(CommandContext<S> context) throws CommandSyntaxException {
                return restoreBackup(environment, context);
            }

            @Override
            public int unknownUsage(CommandContext<S> context) {
                return sendUnknownBackupUsage(environment, context);
            }
        };
    }

    private static <S> UserSubcommand.Handlers<S> userHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new UserSubcommand.Handlers<>() {

            @Override
            public int rootUsage(CommandContext<S> context) {
                return sendUserRootUsage(environment, context);
            }

            @Override
            public int targetUsage(CommandContext<S> context) {
                return sendUserTargetUsage(environment, context);
            }

            @Override
            public int unknownTargetUsage(CommandContext<S> context) {
                return sendUnknownUserTargetUsage(environment, context);
            }

            @Override
            public int info(CommandContext<S> context) throws CommandSyntaxException {
                return showUserInfo(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listPermissions(environment, context);
            }

            @Override
            public int getUsage(CommandContext<S> context) {
                return sendUserGetUsage(environment, context);
            }

            @Override
            public int get(CommandContext<S> context) throws CommandSyntaxException {
                return getPermission(environment, context);
            }

            @Override
            public int setUsage(CommandContext<S> context) {
                return sendUserSetUsage(environment, context);
            }

            @Override
            public int set(CommandContext<S> context) throws CommandSyntaxException {
                return setPermission(environment, context);
            }

            @Override
            public int clearUsage(CommandContext<S> context) {
                return sendUserClearUsage(environment, context);
            }

            @Override
            public int clear(CommandContext<S> context) throws CommandSyntaxException {
                return clearPermission(environment, context);
            }

            @Override
            public int clearAll(CommandContext<S> context) throws CommandSyntaxException {
                return clearAllPermissions(environment, context);
            }

            @Override
            public int groups(CommandContext<S> context) throws CommandSyntaxException {
                return listSubjectGroups(environment, context);
            }

            @Override
            public int tracks(CommandContext<S> context) throws CommandSyntaxException {
                return listSubjectTracks(environment, context);
            }

            @Override
            public int prefixUsage(CommandContext<S> context) {
                return sendUserDisplayUsage(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixGet(CommandContext<S> context) throws CommandSyntaxException {
                return getUserDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixSetUsage(CommandContext<S> context) {
                return sendUserDisplaySetUsage(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixSet(CommandContext<S> context) throws CommandSyntaxException {
                return setUserDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixClear(CommandContext<S> context) throws CommandSyntaxException {
                return clearUserDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int suffixUsage(CommandContext<S> context) {
                return sendUserDisplayUsage(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixGet(CommandContext<S> context) throws CommandSyntaxException {
                return getUserDisplay(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixSetUsage(CommandContext<S> context) {
                return sendUserDisplaySetUsage(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixSet(CommandContext<S> context) throws CommandSyntaxException {
                return setUserDisplay(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixClear(CommandContext<S> context) throws CommandSyntaxException {
                return clearUserDisplay(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int groupUsage(CommandContext<S> context) {
                return sendUserGroupUsage(environment, context);
            }

            @Override
            public int groupAddUsage(CommandContext<S> context) {
                return sendUserGroupAddUsage(environment, context);
            }

            @Override
            public int groupAdd(CommandContext<S> context) throws CommandSyntaxException {
                return addSubjectGroup(environment, context);
            }

            @Override
            public int groupRemoveUsage(CommandContext<S> context) {
                return sendUserGroupRemoveUsage(environment, context);
            }

            @Override
            public int groupRemove(CommandContext<S> context) throws CommandSyntaxException {
                return removeSubjectGroup(environment, context);
            }

            @Override
            public int unknownGroupUsage(CommandContext<S> context) {
                return sendUnknownUserGroupUsage(environment, context);
            }

            @Override
            public int trackUsage(CommandContext<S> context) {
                return sendUserTrackUsage(environment, context);
            }

            @Override
            public int trackPromoteUsage(CommandContext<S> context) {
                return sendUserTrackPromoteUsage(environment, context);
            }

            @Override
            public int trackPromote(CommandContext<S> context) throws CommandSyntaxException {
                return promoteSubjectOnTrack(environment, context);
            }

            @Override
            public int trackDemoteUsage(CommandContext<S> context) {
                return sendUserTrackDemoteUsage(environment, context);
            }

            @Override
            public int trackDemote(CommandContext<S> context) throws CommandSyntaxException {
                return demoteSubjectOnTrack(environment, context);
            }

            @Override
            public int unknownTrackUsage(CommandContext<S> context) {
                return sendUnknownUserTrackUsage(environment, context);
            }

            @Override
            public int checkUsage(CommandContext<S> context) {
                return sendUserCheckUsage(environment, context);
            }

            @Override
            public int check(CommandContext<S> context) throws CommandSyntaxException {
                return checkPermission(environment, context);
            }

            @Override
            public int explainUsage(CommandContext<S> context) {
                return sendUserExplainUsage(environment, context);
            }

            @Override
            public int explain(CommandContext<S> context) throws CommandSyntaxException {
                return explainPermission(environment, context);
            }
        };
    }

    private static <S> TrackSubcommand.Handlers<S> trackHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new TrackSubcommand.Handlers<>() {

            @Override
            public int rootUsage(CommandContext<S> context) {
                return sendTrackRootUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listTracks(environment, context);
            }

            @Override
            public int targetUsage(CommandContext<S> context) {
                return sendTrackTargetUsage(environment, context);
            }

            @Override
            public int unknownTargetUsage(CommandContext<S> context) {
                return sendUnknownTrackTargetUsage(environment, context);
            }

            @Override
            public int create(CommandContext<S> context) throws CommandSyntaxException {
                return createTrack(environment, context);
            }

            @Override
            public int delete(CommandContext<S> context) throws CommandSyntaxException {
                return deleteTrack(environment, context);
            }

            @Override
            public int renameUsage(CommandContext<S> context) {
                return sendTrackRenameUsage(environment, context);
            }

            @Override
            public int rename(CommandContext<S> context) throws CommandSyntaxException {
                return renameTrack(environment, context);
            }

            @Override
            public int info(CommandContext<S> context) throws CommandSyntaxException {
                return showTrackInfo(environment, context);
            }

            @Override
            public int show(CommandContext<S> context) throws CommandSyntaxException {
                return listTrack(environment, context);
            }

            @Override
            public int appendUsage(CommandContext<S> context) {
                return sendTrackAppendUsage(environment, context);
            }

            @Override
            public int append(CommandContext<S> context) throws CommandSyntaxException {
                return appendTrackGroup(environment, context);
            }

            @Override
            public int insertUsage(CommandContext<S> context) {
                return sendTrackInsertUsage(environment, context);
            }

            @Override
            public int insert(CommandContext<S> context) throws CommandSyntaxException {
                return insertTrackGroup(environment, context);
            }

            @Override
            public int moveUsage(CommandContext<S> context) {
                return sendTrackMoveUsage(environment, context);
            }

            @Override
            public int move(CommandContext<S> context) throws CommandSyntaxException {
                return moveTrackGroup(environment, context);
            }

            @Override
            public int removeUsage(CommandContext<S> context) {
                return sendTrackRemoveUsage(environment, context);
            }

            @Override
            public int remove(CommandContext<S> context) throws CommandSyntaxException {
                return removeTrackGroup(environment, context);
            }
        };
    }

    private static <S> GroupSubcommand.Handlers<S> groupHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new GroupSubcommand.Handlers<>() {

            @Override
            public int rootUsage(CommandContext<S> context) {
                return sendGroupRootUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listGroups(environment, context);
            }

            @Override
            public int targetUsage(CommandContext<S> context) {
                return sendGroupTargetUsage(environment, context);
            }

            @Override
            public int unknownTargetUsage(CommandContext<S> context) {
                return sendUnknownGroupTargetUsage(environment, context);
            }

            @Override
            public int create(CommandContext<S> context) throws CommandSyntaxException {
                return createGroup(environment, context);
            }

            @Override
            public int delete(CommandContext<S> context) throws CommandSyntaxException {
                return deleteGroup(environment, context);
            }

            @Override
            public int renameUsage(CommandContext<S> context) {
                return sendGroupRenameUsage(environment, context);
            }

            @Override
            public int rename(CommandContext<S> context) throws CommandSyntaxException {
                return renameGroup(environment, context);
            }

            @Override
            public int info(CommandContext<S> context) throws CommandSyntaxException {
                return showGroupInfo(environment, context);
            }

            @Override
            public int show(CommandContext<S> context) throws CommandSyntaxException {
                return listGroup(environment, context);
            }

            @Override
            public int members(CommandContext<S> context) throws CommandSyntaxException {
                return listGroupMembers(environment, context);
            }

            @Override
            public int parents(CommandContext<S> context) throws CommandSyntaxException {
                return listGroupParents(environment, context);
            }

            @Override
            public int parentUsage(CommandContext<S> context) {
                return sendGroupParentUsage(environment, context);
            }

            @Override
            public int parentAddUsage(CommandContext<S> context) {
                return sendGroupParentAddUsage(environment, context);
            }

            @Override
            public int parentAdd(CommandContext<S> context) throws CommandSyntaxException {
                return addGroupParent(environment, context);
            }

            @Override
            public int parentRemoveUsage(CommandContext<S> context) {
                return sendGroupParentRemoveUsage(environment, context);
            }

            @Override
            public int parentRemove(CommandContext<S> context) throws CommandSyntaxException {
                return removeGroupParent(environment, context);
            }

            @Override
            public int unknownParentUsage(CommandContext<S> context) {
                return sendUnknownGroupParentUsage(environment, context);
            }

            @Override
            public int getUsage(CommandContext<S> context) {
                return sendGroupGetUsage(environment, context);
            }

            @Override
            public int get(CommandContext<S> context) throws CommandSyntaxException {
                return getGroupPermission(environment, context);
            }

            @Override
            public int setUsage(CommandContext<S> context) {
                return sendGroupSetUsage(environment, context);
            }

            @Override
            public int set(CommandContext<S> context) throws CommandSyntaxException {
                return setGroupPermission(environment, context);
            }

            @Override
            public int clearUsage(CommandContext<S> context) {
                return sendGroupClearUsage(environment, context);
            }

            @Override
            public int clear(CommandContext<S> context) throws CommandSyntaxException {
                return clearGroupPermission(environment, context);
            }

            @Override
            public int clearAll(CommandContext<S> context) throws CommandSyntaxException {
                return clearAllGroupPermissions(environment, context);
            }

            @Override
            public int prefixUsage(CommandContext<S> context) {
                return sendGroupDisplayUsage(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixGet(CommandContext<S> context) throws CommandSyntaxException {
                return getGroupDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixSetUsage(CommandContext<S> context) {
                return sendGroupDisplaySetUsage(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixSet(CommandContext<S> context) throws CommandSyntaxException {
                return setGroupDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int prefixClear(CommandContext<S> context) throws CommandSyntaxException {
                return clearGroupDisplay(environment, context, DisplaySlot.PREFIX);
            }

            @Override
            public int suffixUsage(CommandContext<S> context) {
                return sendGroupDisplayUsage(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixGet(CommandContext<S> context) throws CommandSyntaxException {
                return getGroupDisplay(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixSetUsage(CommandContext<S> context) {
                return sendGroupDisplaySetUsage(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixSet(CommandContext<S> context) throws CommandSyntaxException {
                return setGroupDisplay(environment, context, DisplaySlot.SUFFIX);
            }

            @Override
            public int suffixClear(CommandContext<S> context) throws CommandSyntaxException {
                return clearGroupDisplay(environment, context, DisplaySlot.SUFFIX);
            }
        };
    }

    private static <S> UsersSubcommand.Handlers<S> usersHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new UsersSubcommand.Handlers<>() {

            @Override
            public int usage(CommandContext<S> context) {
                return sendUsersUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listSubjects(environment, context);
            }

            @Override
            public int searchUsage(CommandContext<S> context) {
                return sendUsersSearchUsage(environment, context);
            }

            @Override
            public int search(CommandContext<S> context) throws CommandSyntaxException {
                return searchSubjects(environment, context);
            }

            @Override
            public int unknownUsage(CommandContext<S> context) {
                return sendUnknownUsersUsage(environment, context);
            }
        };
    }

    private static <S> NodesSubcommand.Handlers<S> nodesHandlers(ClutchPermsCommandEnvironment<S> environment) {
        return new NodesSubcommand.Handlers<>() {

            @Override
            public int usage(CommandContext<S> context) {
                return sendNodesUsage(environment, context);
            }

            @Override
            public int list(CommandContext<S> context) throws CommandSyntaxException {
                return listKnownNodes(environment, context);
            }

            @Override
            public int searchUsage(CommandContext<S> context) {
                return sendNodesSearchUsage(environment, context);
            }

            @Override
            public int search(CommandContext<S> context) throws CommandSyntaxException {
                return searchKnownNodes(environment, context);
            }

            @Override
            public int addUsage(CommandContext<S> context) {
                return sendNodesAddUsage(environment, context);
            }

            @Override
            public int add(CommandContext<S> context) throws CommandSyntaxException {
                return addKnownNode(environment, context);
            }

            @Override
            public int removeUsage(CommandContext<S> context) {
                return sendNodesRemoveUsage(environment, context);
            }

            @Override
            public int remove(CommandContext<S> context) throws CommandSyntaxException {
                return removeKnownNode(environment, context);
            }

            @Override
            public int unknownUsage(CommandContext<S> context) {
                return sendUnknownNodesUsage(environment, context);
            }
        };
    }

    private static <S> int executeAuthorized(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String requiredPermission, CommandAction<S> action)
            throws CommandSyntaxException {
        S source = context.getSource();
        if (!canUse(environment, source, requiredPermission)) {
            if (environment.sourceKind(source) == CommandSourceKind.OTHER) {
                environment.sendMessage(source, CommandLang.error(CommandLang.ERROR_OTHER_SOURCE_DENIED));
                return 0;
            }
            environment.sendMessage(source, CommandLang.error(CommandLang.ERROR_NO_PERMISSION));
            return 0;
        }

        return runActionWithFeedback(environment, source, action);
    }

    private static <S> int executeAuthorizedForSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String requiredPermission, CommandAction<S> action)
            throws CommandSyntaxException {
        return executeAuthorized(environment, context, requiredPermission, source -> {
            String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
            Optional<ResolvedSubject> resolvedSubject = resolveImmediateSubject(environment, context, target);
            if (resolvedSubject.isPresent()) {
                ResolvedSubject subject = resolvedSubject.get();
                if (subject.recordMetadata()) {
                    recordResolvedSubject(environment, subject.subject());
                }
                return withResolvedSubject(subject.subject(), () -> action.run(source));
            }

            CompletableFuture<Optional<CommandSubject>> lookupFuture;
            try {
                lookupFuture = Objects.requireNonNull(environment.resolveSubjectAsync(source, target), "lookupFuture");
            } catch (RuntimeException exception) {
                throw feedback(List.of(CommandLang.userTargetLookupFailed(target, exception)));
            }

            if (lookupFuture.isDone()) {
                final Optional<CommandSubject> completedSubject;
                try {
                    completedSubject = lookupFuture.join();
                } catch (RuntimeException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    throw feedback(List.of(CommandLang.userTargetLookupFailed(target, cause)));
                }
                if (completedSubject.isEmpty()) {
                    throw unknownUserTargetFeedback(environment, context, target);
                }

                CommandSubject subject = completedSubject.get();
                recordResolvedSubject(environment, subject);
                environment.sendMessage(source, CommandLang.userTargetLookupResolved(target, formatSubject(subject)));
                return withResolvedSubject(subject, () -> action.run(source));
            }

            environment.sendMessage(source, CommandLang.userTargetLookupQueued(target));
            lookupFuture.whenComplete((resolved, failure) -> environment.executeOnCommandThread(source,
                    () -> resumeResolvedSubject(environment, context, source, target, action, resolved, failure)));
            return Command.SINGLE_SUCCESS;
        });
    }

    private static <S> int runActionWithFeedback(ClutchPermsCommandEnvironment<S> environment, S source, CommandAction<S> action) {
        try {
            return action.run(source);
        } catch (CommandFeedbackException exception) {
            exception.messages().forEach(message -> environment.sendMessage(source, message));
            return 0;
        } catch (CommandSyntaxException exception) {
            environment.sendMessage(source, CommandLang.error(exception.getRawMessage().getString()));
            return 0;
        }
    }

    private static <S> void resumeResolvedSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, S source, String target, CommandAction<S> action,
            Optional<CommandSubject> resolved, Throwable failure) {
        if (failure != null) {
            environment.sendMessage(source, CommandLang.userTargetLookupFailed(target, failure));
            return;
        }
        if (resolved == null || resolved.isEmpty()) {
            try {
                throw unknownUserTargetFeedback(environment, context, target);
            } catch (CommandFeedbackException exception) {
                exception.messages().forEach(message -> environment.sendMessage(source, message));
            }
            return;
        }

        CommandSubject subject = resolved.get();
        recordResolvedSubject(environment, subject);
        environment.sendMessage(source, CommandLang.userTargetLookupResolved(target, formatSubject(subject)));
        runActionWithFeedback(environment, source, ignored -> withResolvedSubject(subject, () -> action.run(source)));
    }

    private static <S> Optional<ResolvedSubject> resolveImmediateSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String target)
            throws CommandSyntaxException {
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.map(subject -> new ResolvedSubject(subject, false));
        }

        Optional<CommandSubject> cachedSubject = environment.findCachedSubject(context.getSource(), target);
        if (cachedSubject.isPresent()) {
            return cachedSubject.map(subject -> new ResolvedSubject(subject, true));
        }

        Optional<CommandSubject> knownSubject = resolveKnownSubject(environment, context, target);
        if (knownSubject.isPresent()) {
            return knownSubject.map(subject -> new ResolvedSubject(subject, false));
        }

        try {
            UUID subjectId = UUID.fromString(target);
            String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
            return Optional.of(new ResolvedSubject(new CommandSubject(subjectId, displayName), false));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static <S> void recordResolvedSubject(ClutchPermsCommandEnvironment<S> environment, CommandSubject subject) {
        environment.subjectMetadataService().recordSubject(subject.id(), subject.displayName(), Instant.now());
    }

    private static <T> T withResolvedSubject(CommandSubject subject, ThrowingSupplier<T> supplier) throws CommandSyntaxException {
        CommandSubject previous = RESOLVED_SUBJECT_OVERRIDE.get();
        RESOLVED_SUBJECT_OVERRIDE.set(subject);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                RESOLVED_SUBJECT_OVERRIDE.remove();
            } else {
                RESOLVED_SUBJECT_OVERRIDE.set(previous);
            }
        }
    }

    private static <S> int sendCommandList(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        int page = requestedPage(context, "help");
        int pageSize = environment.config().commands().helpPageSize();
        int totalPages = totalPages(COMMAND_HELP.size(), pageSize);
        requirePageInRange(context, page, totalPages, "help");

        environment.sendMessage(context.getSource(), CommandLang.commandListHeader(page, totalPages));
        String rootLiteral = rootLiteral(context);
        pageItems(COMMAND_HELP, page, pageSize)
                .forEach(entry -> environment.sendMessage(context.getSource(), CommandLang.commandHelpEntry(rootLiteral, entry.syntax(), entry.permission(), entry.description())));
        sendPageNavigation(environment, context, "help", page, totalPages);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int sendStatus(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        CommandStatusDiagnostics diagnostics = environment.statusDiagnostics();
        environment.sendMessage(context.getSource(), CommandLang.status());
        environment.sendMessage(context.getSource(), CommandLang.statusDatabaseFile(diagnostics.databaseFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusConfigFile(diagnostics.configFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusBackupRetention(environment.config().backups().retentionLimit()));
        environment.sendMessage(context.getSource(),
                CommandLang.backupScheduleEnabled(environment.config().backups().schedule().enabled(), environment.scheduledBackupStatus().running()));
        environment.sendMessage(context.getSource(),
                CommandLang.statusAuditRetention(environment.config().audit().enabled(), environment.config().audit().maxAgeDays(), environment.config().audit().maxEntries()));
        environment.sendMessage(context.getSource(),
                CommandLang.statusCommandPageSizes(environment.config().commands().helpPageSize(), environment.config().commands().resultPageSize()));
        environment.sendMessage(context.getSource(), CommandLang.statusChatFormatting(environment.config().chat().enabled()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownSubjects(environment.subjectMetadataService().getSubjects().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownGroups(environment.groupService().getGroups().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownTracks(environment.trackService().getTracks().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownNodes(environment.permissionNodeRegistry().getKnownNodes().size()));
        PermissionResolverCacheStats cacheStats = environment.permissionResolver().cacheStats();
        environment.sendMessage(context.getSource(), CommandLang.statusResolverCache(cacheStats.subjects(), cacheStats.nodeResults(), cacheStats.effectiveSnapshots()));
        environment.sendMessage(context.getSource(), CommandLang.statusRuntimeBridge(diagnostics.runtimeBridgeStatus()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listHistory(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        int page = requestedPage(context, "history");
        int pageSize = environment.config().commands().resultPageSize();
        List<AuditEntry> entries = environment.auditLogService().listNewestFirst();
        if (entries.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.historyEmpty());
            return Command.SINGLE_SUCCESS;
        }

        int totalPages = totalPages(entries.size(), pageSize);
        requirePageInRange(context, page, totalPages, "history");
        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = pageItems(entries, page, pageSize).stream().map(entry -> historyRow(rootLiteral, entry)).toList();
        sendPagedRows(environment, context, "History", rows, "history");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int pruneHistoryDays(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        int days = IntegerArgumentType.getInteger(context, DAYS_ARGUMENT);
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int matchedRows = (int) environment.auditLogService().listNewestFirst().stream().filter(entry -> entry.timestamp().isBefore(cutoff)).count();
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("history-prune-days", Integer.toString(days)))) {
            return Command.SINGLE_SUCCESS;
        }

        matchedRows = (int) environment.auditLogService().listNewestFirst().stream().filter(entry -> entry.timestamp().isBefore(cutoff)).count();
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        appendPruneAudit(environment, context, "history.prune.days", "history-prune-days:" + days, "history prune days " + days, matchedRows);
        int deleted = environment.auditLogService().pruneOlderThan(cutoff);
        environment.sendMessage(context.getSource(), CommandLang.historyPruned(deleted));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int pruneHistoryCount(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, COUNT_ARGUMENT);
        int existingRows = environment.auditLogService().listNewestFirst().size();
        int matchedRows = Math.max(0, existingRows + 1 - count);
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("history-prune-count", Integer.toString(count)))) {
            return Command.SINGLE_SUCCESS;
        }

        existingRows = environment.auditLogService().listNewestFirst().size();
        matchedRows = Math.max(0, existingRows + 1 - count);
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        appendPruneAudit(environment, context, "history.prune.count", "history-prune-count:" + count, "history prune count " + count, matchedRows);
        int deleted = environment.auditLogService().pruneBeyondNewest(count);
        environment.sendMessage(context.getSource(), CommandLang.historyPruned(deleted));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> void appendPruneAudit(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String action, String targetKey, String targetDisplay,
            int matchedRows) throws CommandSyntaxException {
        JsonObject before = new JsonObject();
        before.addProperty("matchedRows", matchedRows);
        JsonObject after = new JsonObject();
        after.addProperty("undoable", false);
        try {
            environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(environment, context.getSource()), action, "audit", targetKey, targetDisplay, canonicalJson(before.toString()),
                            canonicalJson(after.toString()), confirmationCommand(context), false));
        } catch (RuntimeException exception) {
            throw AUDIT_OPERATION_FAILED.create(CommandLang.auditFailed(exception));
        }
    }

    private static <S> int sendUndoUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing history id.", "Choose an undoable history entry id.", List.of("undo <id>"));
    }

    private static <S> int sendBackupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing backup command.", "Database backups can be created, listed, or restored by file name.", backupUsages());
    }

    private static <S> int sendConfigUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing config command.", "Config commands inspect or update runtime config.", configUsages());
    }

    private static <S> int sendConfigGetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing config key.", "Choose one config value to inspect.", List.of("config get <key>"));
    }

    private static <S> int sendConfigSetKeyUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing config key.", "Choose one config value to update.", List.of("config set <key> <value>"));
    }

    private static <S> int sendConfigSetValueUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        ConfigEntry entry = getConfigEntry(context);
        return sendUsage(environment, context, "Missing config value.", "Set " + entry.key() + " to " + entry.inputHint() + ".", List.of("config set " + entry.key() + " <value>"));
    }

    private static <S> int sendConfigResetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing config key.", "Reset one config value, or all config values, to defaults.", List.of("config reset <key|all>"));
    }

    private static <S> int sendUnknownConfigUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Unknown config command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Config commands inspect and update runtime config.", configUsages());
    }

    private static <S> int sendBackupRestoreFileUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing backup file.", "Pick a database backup file.", List.of("backup restore <backup-file>"));
    }

    private static <S> int sendUnknownBackupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Unknown backup command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Backup supports create, list, and restore commands.", backupUsages());
    }

    private static <S> int sendUserRootUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing user target.", "Provide an exact online name, resolvable offline name, stored last-known name, or UUID.", userRootUsages());
    }

    private static <S> int sendUserTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing user command.", "Choose what to do with " + target + ".", userTargetUsages(target));
    }

    private static <S> int sendUnknownUserTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Unknown user command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "User commands inspect or mutate direct permissions and group membership.", userTargetUsages(target));
    }

    private static <S> int sendUserGetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the direct user permission node to read.", List.of("user " + target + " get <node>"));
    }

    private static <S> int sendUserSetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing permission assignment.", "Set a node to true or false.", List.of("user " + target + " set <node> <true|false>"));
    }

    private static <S> int sendUserClearUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the direct user permission node to clear.", List.of("user " + target + " clear <node>"));
    }

    private static <S> int sendUserDisplayUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing user " + slot.label() + " command.", "Get, set, or clear this user's direct " + slot.label() + ".",
                List.of("user " + target + " " + slot.label() + " get", "user " + target + " " + slot.label() + " set <text>", "user " + target + " " + slot.label() + " clear"));
    }

    private static <S> int sendUserDisplaySetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing display text.", "Use ampersand formatting codes like &7, &a, &l, &o, &r, and && for a literal ampersand.",
                List.of("user " + target + " " + slot.label() + " set <text>"));
    }

    private static <S> int sendUserCheckUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the effective permission node to check.", List.of("user " + target + " check <node>"));
    }

    private static <S> int sendUserExplainUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the effective permission node to explain.", List.of("user " + target + " explain <node>"));
    }

    private static <S> int sendUserGroupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing user group command.", "Add or remove explicit group membership.",
                List.of("user " + target + " group add <group>", "user " + target + " group remove <group>"));
    }

    private static <S> int sendUserTrackUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing user track command.", "List, promote, or demote this user's track position.",
                List.of("user " + target + " track promote <track>", "user " + target + " track demote <track>"));
    }

    private static <S> int sendUserGroupAddUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing group name.", "Choose the group to add this user to.", List.of("user " + target + " group add <group>"));
    }

    private static <S> int sendUserGroupRemoveUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing group name.", "Choose the group to remove this user from.", List.of("user " + target + " group remove <group>"));
    }

    private static <S> int sendUnknownUserGroupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Unknown user group command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "User group commands add or remove explicit memberships.", List.of("user " + target + " group add <group>", "user " + target + " group remove <group>"));
    }

    private static <S> int sendUserTrackPromoteUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing track name.", "Choose the track to promote this user on.", List.of("user " + target + " track promote <track>"));
    }

    private static <S> int sendUserTrackDemoteUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Missing track name.", "Choose the track to demote this user on.", List.of("user " + target + " track demote <track>"));
    }

    private static <S> int sendUnknownUserTrackUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        return sendUsage(environment, context, "Unknown user track command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "User track commands list, promote, or demote a user's track position.",
                List.of("user " + target + " tracks", "user " + target + " track promote <track>", "user " + target + " track demote <track>"));
    }

    private static <S> int sendGroupRootUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing group command.", "List groups or choose a group to inspect or mutate.", groupRootUsages());
    }

    private static <S> int sendGroupTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing group command.", "Choose what to do with group " + group + ".", groupTargetUsages(group));
    }

    private static <S> int sendUnknownGroupTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Unknown group command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Group commands manage definitions, permissions, parents, and members.", groupTargetUsages(group));
    }

    private static <S> int sendGroupGetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the group permission node to read.", List.of("group " + group + " get <node>"));
    }

    private static <S> int sendGroupSetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing permission assignment.", "Set a group permission node to true or false.",
                List.of("group " + group + " set <node> <true|false>"));
    }

    private static <S> int sendGroupClearUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing permission node.", "Choose the group permission node to clear.", List.of("group " + group + " clear <node>"));
    }

    private static <S> int sendGroupRenameUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing new group name.", "Choose the new group name.", List.of("group " + group + " rename <new-group>"));
    }

    private static <S> int sendGroupDisplayUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing group " + slot.label() + " command.", "Get, set, or clear this group's " + slot.label() + ".",
                List.of("group " + group + " " + slot.label() + " get", "group " + group + " " + slot.label() + " set <text>", "group " + group + " " + slot.label() + " clear"));
    }

    private static <S> int sendGroupDisplaySetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing display text.", "Use ampersand formatting codes like &7, &a, &l, &o, &r, and && for a literal ampersand.",
                List.of("group " + group + " " + slot.label() + " set <text>"));
    }

    private static <S> int sendGroupParentUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing group parent command.", "Add or remove a parent group.",
                List.of("group " + group + " parent add <parent>", "group " + group + " parent remove <parent>"));
    }

    private static <S> int sendGroupParentAddUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing parent group.", "Choose the parent group to add.", List.of("group " + group + " parent add <parent>"));
    }

    private static <S> int sendGroupParentRemoveUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Missing parent group.", "Choose the parent group to remove.", List.of("group " + group + " parent remove <parent>"));
    }

    private static <S> int sendUnknownGroupParentUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String group = StringArgumentType.getString(context, GROUP_ARGUMENT);
        return sendUsage(environment, context, "Unknown group parent command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Group parent commands add or remove inheritance links.", List.of("group " + group + " parent add <parent>", "group " + group + " parent remove <parent>"));
    }

    private static <S> int sendUsersUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing users command.", "List known users or search by last-known name.", usersUsages());
    }

    private static <S> int sendTrackRootUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing track command.", "List tracks or choose one track to inspect or mutate.", trackRootUsages());
    }

    private static <S> int sendTrackTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing track command.", "Choose what to do with track " + track + ".", trackTargetUsages(track));
    }

    private static <S> int sendUnknownTrackTargetUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Unknown track command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Track commands manage ordered groups for promotion and demotion.", trackTargetUsages(track));
    }

    private static <S> int sendTrackRenameUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing new track name.", "Choose the new track name.", List.of("track " + track + " rename <new-track>"));
    }

    private static <S> int sendTrackAppendUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing group name.", "Choose the group to add at the end of the track.", List.of("track " + track + " append <group>"));
    }

    private static <S> int sendTrackInsertUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing insert position or group.", "Choose the position and group to insert on the track.",
                List.of("track " + track + " insert <position> <group>"));
    }

    private static <S> int sendTrackMoveUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing group name or position.", "Choose the track group and new position.",
                List.of("track " + track + " move <group> <position>"));
    }

    private static <S> int sendTrackRemoveUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String track = StringArgumentType.getString(context, TRACK_ARGUMENT);
        return sendUsage(environment, context, "Missing group name.", "Choose the group to remove from the track.", List.of("track " + track + " remove <group>"));
    }

    private static <S> int sendUsersSearchUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing user search query.", "Provide part of a last-known player name.", List.of("users search <name>"));
    }

    private static <S> int sendUnknownUsersUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Unknown users command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Users commands list or search stored subject metadata.", usersUsages());
    }

    private static <S> int sendNodesUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing nodes command.", "List, search, add, or remove known permission nodes.", nodesUsages());
    }

    private static <S> int sendOpShortcutUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing user target.", "Provide an exact online name, resolvable offline name, stored last-known name, or UUID.",
                List.of("<target>"));
    }

    private static <S> int sendNodesSearchUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing node search query.", "Provide part of a known node or description.", List.of("nodes search <query>"));
    }

    private static <S> int sendNodesAddUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing known permission node.", "Add an exact known permission node and optional description.",
                List.of("nodes add <node> [description]"));
    }

    private static <S> int sendNodesRemoveUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing known permission node.", "Remove a manually registered known permission node.", List.of("nodes remove <node>"));
    }

    private static <S> int sendUnknownNodesUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Unknown nodes command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT),
                "Node commands manage the manual known-node registry.", nodesUsages());
    }

    private static <S> int sendUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String error, String detail, List<String> usages) {
        S source = context.getSource();
        environment.sendMessage(source, CommandLang.error(error));
        environment.sendMessage(source, CommandLang.detail(detail));
        environment.sendMessage(source, CommandLang.tryHeader());
        String rootLiteral = rootLiteral(context);
        usages.forEach(usage -> environment.sendMessage(source, CommandLang.suggestion(rootLiteral, usage)));
        return 0;
    }

    private static <S> void sendPagedRows(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String title, List<PagedRow> rows, String pageCommand)
            throws CommandSyntaxException {
        int page = requestedPage(context, pageCommand);
        int pageSize = environment.config().commands().resultPageSize();
        int totalPages = totalPages(rows.size(), pageSize);
        requirePageInRange(context, page, totalPages, pageCommand);

        environment.sendMessage(context.getSource(), CommandLang.listHeader(title, page, totalPages));
        pageItems(rows, page, pageSize).forEach(row -> environment.sendMessage(context.getSource(), CommandLang.listRow(row.text(), row.command())));
        sendPageNavigation(environment, context, pageCommand, page, totalPages);
    }

    private static <S> void sendInfoRows(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String title, List<PagedRow> rows) {
        environment.sendMessage(context.getSource(), CommandLang.heading(title + ":"));
        rows.forEach(row -> environment.sendMessage(context.getSource(), CommandLang.listRow(row.text(), row.command())));
    }

    private static <S> void sendPageNavigation(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String pageCommand, int page, int totalPages) {
        if (totalPages <= 1) {
            return;
        }
        String previousCommand = page > 1 ? fullCommand(rootLiteral(context), pageCommand + " " + (page - 1)) : null;
        String nextCommand = page < totalPages ? fullCommand(rootLiteral(context), pageCommand + " " + (page + 1)) : null;
        environment.sendMessage(context.getSource(), CommandLang.pageNavigation(previousCommand, page - 1, page, totalPages, nextCommand, page + 1));
    }

    private static <S> int requestedPage(CommandContext<S> context, String pageCommand) throws CommandSyntaxException {
        if (!hasArgument(context, PAGE_ARGUMENT)) {
            return 1;
        }
        String rawPage = StringArgumentType.getString(context, PAGE_ARGUMENT);
        int page;
        try {
            page = Integer.parseInt(rawPage);
        } catch (NumberFormatException exception) {
            throw invalidPageFeedback(context, rawPage, pageCommand);
        }
        if (page < 1) {
            throw invalidPageFeedback(context, rawPage, pageCommand);
        }
        return page;
    }

    private static <S> void requirePageInRange(CommandContext<S> context, int page, int totalPages, String pageCommand) throws CommandSyntaxException {
        if (page <= totalPages) {
            return;
        }
        int closestPage = Math.max(1, Math.min(page, totalPages));
        throw feedback(List.of(CommandLang.pageOutOfRange(page), CommandLang.availablePages(totalPages), CommandLang.tryHeader(),
                CommandLang.suggestion(rootLiteral(context), pageCommand + " " + closestPage)));
    }

    private static <S> CommandFeedbackException invalidPageFeedback(CommandContext<S> context, String rawPage, String pageCommand) {
        return feedback(List.of(CommandLang.invalidPage(rawPage), CommandLang.pageStartsAtOne(), CommandLang.tryHeader(),
                CommandLang.suggestion(rootLiteral(context), pageCommand + " 1")));
    }

    private static int totalPages(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private static <T> List<T> pageItems(List<T> items, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        int to = Math.min(items.size(), from + pageSize);
        return items.subList(from, to);
    }

    private static <S> boolean hasArgument(CommandContext<S> context, String argumentName) {
        return context.getNodes().stream().anyMatch(node -> argumentName.equals(node.getNode().getName()));
    }

    private static <S> String rootLiteral(CommandContext<S> context) {
        if (context.getNodes().isEmpty()) {
            return ROOT_LITERAL;
        }
        return context.getNodes().getFirst().getNode().getName();
    }

    private static List<String> backupUsages() {
        return List.of("backup create", "backup list [page]", "backup schedule status", "backup schedule <enable|disable>", "backup schedule interval <minutes>",
                "backup schedule run-now", "backup restore <backup-file>");
    }

    private static List<String> backupScheduleUsages() {
        return List.of("backup schedule status", "backup schedule enable", "backup schedule disable", "backup schedule interval <minutes>", "backup schedule run-now");
    }

    private static List<String> configUsages() {
        return List.of("config list", "config get <key>", "config set <key> <value>", "config reset <key|all>");
    }

    private static List<String> userRootUsages() {
        return List.of("user <target> <info|list|groups>", "user <target> <get|clear|check|explain> <node>", "user <target> set <node> <true|false>", "user <target> clear-all",
                "user <target> group <add|remove> <group>", "user <target> tracks", "user <target> track <promote|demote> <track>", "user <target> <prefix|suffix> get|set|clear");
    }

    private static List<String> userTargetUsages(String target) {
        return List.of("user " + target + " <info|list|groups>", "user " + target + " <get|clear|check|explain> <node>", "user " + target + " set <node> <true|false>",
                "user " + target + " clear-all", "user " + target + " group <add|remove> <group>", "user " + target + " tracks",
                "user " + target + " track <promote|demote> <track>", "user " + target + " <prefix|suffix> get|set|clear");
    }

    private static List<String> groupRootUsages() {
        return List.of("group list", "group <group> <create|delete|info|list|members|parents>", "group <group> <get|clear> <node>", "group <group> set <node> <true|false>",
                "group <group> clear-all", "group <group> rename <new-group>", "group <group> parent <add|remove> <parent>", "group <group> <prefix|suffix> get|set|clear");
    }

    private static List<String> groupTargetUsages(String group) {
        return List.of("group " + group + " <create|delete|info|list|members|parents>", "group " + group + " <get|clear> <node>", "group " + group + " set <node> <true|false>",
                "group " + group + " clear-all", "group " + group + " rename <new-group>", "group " + group + " parent <add|remove> <parent>",
                "group " + group + " <prefix|suffix> get|set|clear");
    }

    private static List<String> trackRootUsages() {
        return List.of("track list", "track <track> <create|delete|info|list>", "track <track> rename <new-track>", "track <track> append <group>",
                "track <track> insert <position> <group>", "track <track> move <group> <position>", "track <track> remove <group>");
    }

    private static List<String> trackTargetUsages(String track) {
        return List.of("track " + track + " <create|delete|info|list>", "track " + track + " rename <new-track>", "track " + track + " append <group>",
                "track " + track + " insert <position> <group>", "track " + track + " move <group> <position>", "track " + track + " remove <group>");
    }

    private static List<String> usersUsages() {
        return List.of("users list", "users search <name>");
    }

    private static List<String> nodesUsages() {
        return List.of("nodes list", "nodes search <query>", "nodes add <node> [description]", "nodes remove <node>");
    }

    private static <S> int reloadStorage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        try {
            environment.reloadStorage();
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw RELOAD_FAILED.create(CommandLang.reloadFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.reloadSuccess());
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int validateStorage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        try {
            environment.validateStorage();
        } catch (RuntimeException exception) {
            throw VALIDATE_FAILED.create(CommandLang.validateFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.validateSuccess());
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        ClutchPermsConfig config = environment.config();
        environment.sendMessage(context.getSource(), CommandLang.configHeader());
        CONFIG_ENTRIES
                .forEach(entry -> environment.sendMessage(context.getSource(), CommandLang.configRow(entry.key(), entry.value(config), entry.description(), entry.displayHint())));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        ConfigEntry entry = getConfigEntry(context);
        environment.sendMessage(context.getSource(), CommandLang.configGet(entry.key(), entry.value(environment.config()), entry.description(), entry.displayHint()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        ConfigEntry entry = getConfigEntry(context);
        String rawValue = StringArgumentType.getString(context, CONFIG_VALUE_ARGUMENT);
        String newValue = parseConfigValue(context, entry, rawValue);
        return setConfigValue(environment, context, entry, newValue);
    }

    private static <S> int setConfigValue(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, ConfigEntry entry, String newValue)
            throws CommandSyntaxException {
        String oldValue = entry.value(environment.config());
        if (oldValue.equals(newValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = configSnapshot(environment.config());

        try {
            environment.updateConfig(config -> entry.withValue(config, newValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }
        recordAudit(environment, context, "config.set", "config", entry.key(), entry.key(), beforeJson, configSnapshot(environment.config()), true);

        environment.sendMessage(context.getSource(), CommandLang.configUpdated(entry.key(), oldValue, newValue));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int resetConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, CONFIG_KEY_ARGUMENT);
        if ("all".equalsIgnoreCase(key)) {
            return resetAllConfig(environment, context);
        }

        ConfigEntry entry = getConfigEntry(context);
        String oldValue = entry.value(environment.config());
        String defaultValue = entry.defaultValue();
        if (oldValue.equals(defaultValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = configSnapshot(environment.config());

        try {
            environment.updateConfig(config -> entry.withValue(config, defaultValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }
        recordAudit(environment, context, "config.reset", "config", entry.key(), entry.key(), beforeJson, configSnapshot(environment.config()), true);

        environment.sendMessage(context.getSource(), CommandLang.configReset(entry.key(), oldValue, defaultValue));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int resetAllConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        ClutchPermsConfig beforeConfig = environment.config();
        if (beforeConfig.equals(ClutchPermsConfig.defaults())) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadyDefaults());
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = configSnapshot(beforeConfig);

        try {
            environment.updateConfig(config -> ClutchPermsConfig.defaults());
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }
        recordAudit(environment, context, "config.reset-all", "config", "all", "all config", beforeJson, configSnapshot(environment.config()), true);

        environment.sendMessage(context.getSource(), CommandLang.configResetAll());
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listBackups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageBackupService backupService = environment.storageBackupService();
        try {
            List<PagedRow> rows = backupService.listBackups(StorageFileKind.DATABASE).stream().map(backup -> backupRow(rootLiteral(context), backup, backup.fileName())).toList();
            if (rows.isEmpty()) {
                environment.sendMessage(context.getSource(), CommandLang.backupsEmpty());
                return Command.SINGLE_SUCCESS;
            }
            sendPagedRows(environment, context, "Backups", rows, "backup list page");
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int createBackup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        try {
            StorageBackup backup = environment.storageBackupService().createBackup()
                    .orElseThrow(() -> new PermissionStorageException("Cannot create database backup because database.db does not exist"));
            environment.sendMessage(context.getSource(), CommandLang.backupCreated(backup.fileName()));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
    }

    private static <S> int createScheduledBackup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        try {
            StorageBackup backup = environment.createScheduledBackupNow()
                    .orElseThrow(() -> new PermissionStorageException("Cannot create database backup because database.db does not exist"));
            environment.sendMessage(context.getSource(), CommandLang.backupCreated(backup.fileName()));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
    }

    private static <S> int showBackupScheduleStatus(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        try {
            ScheduledBackupStatus status = environment.scheduledBackupStatus();
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleHeader());
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleEnabled(status.enabled(), status.running()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleInterval(status.intervalMinutes()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleRunOnStartup(status.runOnStartup()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleNextRun(status.nextRun().map(Instant::toString).orElse("none")));
            environment.sendMessage(context.getSource(),
                    CommandLang.backupScheduleLastSuccess(status.lastSuccess().map(Instant::toString).orElse("none"), status.lastBackupFile().orElse("none")));
            environment.sendMessage(context.getSource(),
                    CommandLang.backupScheduleLastFailure(status.lastFailure().map(Instant::toString).orElse("none"), status.lastFailureMessage().orElse("none")));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
    }

    private static <S> int setBackupScheduleEnabled(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, boolean enabled) throws CommandSyntaxException {
        ConfigEntry entry = configEntry("backups.schedule.enabled");
        return setConfigValue(environment, context, entry, Boolean.toString(enabled));
    }

    private static <S> int setBackupScheduleInterval(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        ConfigEntry entry = configEntry("backups.schedule.intervalMinutes");
        String rawValue = StringArgumentType.getString(context, CONFIG_VALUE_ARGUMENT);
        String newValue = parseConfigValue(context, entry, rawValue);
        return setConfigValue(environment, context, entry, newValue);
    }

    private static <S> int restoreBackup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String backupFileName = StringArgumentType.getString(context, BACKUP_FILE_ARGUMENT);
        StorageBackupService backupService = environment.storageBackupService();
        List<StorageBackup> backups;
        try {
            backups = backupService.listBackups(StorageFileKind.DATABASE);
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
        StorageBackup backup = requireKnownBackupFile(context, backupFileName, backups);
        validateBackup(environment, StorageFileKind.DATABASE, backup);
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("backup-restore", backup.fileName()))) {
            return Command.SINGLE_SUCCESS;
        }
        try {
            environment.restoreBackup(StorageFileKind.DATABASE, backupFileName);
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.backupRestored(backupFileName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> boolean canUse(ClutchPermsCommandEnvironment<S> environment, S source, String requiredPermission) {
        CommandSourceKind sourceKind = environment.sourceKind(source);
        if (sourceKind == CommandSourceKind.CONSOLE) {
            return true;
        }
        if (sourceKind != CommandSourceKind.PLAYER) {
            return false;
        }

        Optional<UUID> subjectId = environment.sourceSubjectId(source);
        return subjectId.isPresent() && environment.permissionResolver().hasPermission(subjectId.get(), requiredPermission);
    }

    private static <S> boolean canUseAny(ClutchPermsCommandEnvironment<S> environment, S source, String... requiredPermissions) {
        for (String requiredPermission : requiredPermissions) {
            if (canUse(environment, source, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    private static <S> int showUserInfo(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String rootLiteral = rootLiteral(context);
        String subjectListCommand = fullCommand(rootLiteral, "user " + subject.id() + " list");
        String subjectGroupsCommand = fullCommand(rootLiteral, "user " + subject.id() + " groups");
        String subjectTracksCommand = fullCommand(rootLiteral, "user " + subject.id() + " tracks");
        String subjectPrefixCommand = fullCommand(rootLiteral, "user " + subject.id() + " prefix get");
        String subjectSuffixCommand = fullCommand(rootLiteral, "user " + subject.id() + " suffix get");

        List<PagedRow> rows = new ArrayList<>();
        rows.add(new PagedRow("subject " + formatSubject(subject), subjectListCommand));
        Optional<SubjectMetadata> metadata = environment.subjectMetadataService().getSubject(subject.id());
        rows.add(new PagedRow(metadata.map(value -> "stored last-known name " + value.lastKnownName() + ", last seen " + value.lastSeen()).orElse("stored metadata none"),
                subjectListCommand));
        rows.add(new PagedRow("direct permissions " + environment.permissionService().getPermissions(subject.id()).size(), subjectListCommand));
        rows.add(new PagedRow("effective permissions " + environment.permissionResolver().getEffectivePermissions(subject.id()).size(), subjectListCommand));
        rows.add(new PagedRow("groups " + summarizeSubjectGroups(environment, subject.id()), subjectGroupsCommand));
        rows.add(new PagedRow("tracks " + summarizeSubjectTracks(environment, subject.id()), subjectTracksCommand));
        rows.add(new PagedRow("direct prefix " + formatDisplayValue(subjectDisplayValue(environment, subject.id(), DisplaySlot.PREFIX)), subjectPrefixCommand));
        rows.add(new PagedRow("effective prefix " + formatEffectiveDisplay(environment.displayResolver().resolve(subject.id(), DisplaySlot.PREFIX)), subjectPrefixCommand));
        rows.add(new PagedRow("direct suffix " + formatDisplayValue(subjectDisplayValue(environment, subject.id(), DisplaySlot.SUFFIX)), subjectSuffixCommand));
        rows.add(new PagedRow("effective suffix " + formatEffectiveDisplay(environment.displayResolver().resolve(subject.id(), DisplaySlot.SUFFIX)), subjectSuffixCommand));

        sendInfoRows(environment, context, "User " + formatSubject(subject), rows);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listPermissions(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        Map<String, PermissionValue> permissions = environment.permissionService().getPermissions(subject.id());
        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = new ArrayList<>();
        addSubjectDisplayRows(environment, rows, rootLiteral, subject);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new PagedRow(entry.getKey() + "=" + entry.getValue().name(), fullCommand(rootLiteral, "user " + subject.id() + " get " + entry.getKey())))
                .forEach(rows::add);
        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }

        sendPagedRows(environment, context, "Permissions for " + formatSubject(subject), rows, "user " + StringArgumentType.getString(context, TARGET_ARGUMENT) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        PermissionValue value = environment.permissionService().getPermission(subject.id(), node);

        environment.sendMessage(context.getSource(), CommandLang.permissionGet(formatSubject(subject), node, value));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        PermissionAssignment assignment = getPermissionAssignment(context);
        if (environment.permissionService().getPermission(subject.id(), assignment.node()) == assignment.value()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionAlreadySet(assignment.node(), formatSubject(subject), assignment.value()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectPermissionsSnapshot(environment, subject.id());

        try {
            environment.permissionService().setPermission(subject.id(), assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(exception));
        }
        recordAudit(environment, context, "user.permission.set", "user-permissions", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectPermissionsSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.permissionSet(assignment.node(), formatSubject(subject), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        if (environment.permissionService().getPermission(subject.id(), node) == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.permissionAlreadyClear(node, formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectPermissionsSnapshot(environment, subject.id());

        try {
            environment.permissionService().clearPermission(subject.id(), node);
        } catch (RuntimeException exception) {
            throw PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(exception));
        }
        recordAudit(environment, context, "user.permission.clear", "user-permissions", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectPermissionsSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.permissionClear(node, formatSubject(subject)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearAllPermissions(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        if (environment.permissionService().getPermissions(subject.id()).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("user-clear-all", subject.id().toString()))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectPermissionsSnapshot(environment, subject.id());
        int removedPermissions;
        try {
            removedPermissions = environment.permissionService().clearPermissions(subject.id());
        } catch (RuntimeException exception) {
            throw PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(exception));
        }

        if (removedPermissions == 0) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(formatSubject(subject)));
        } else {
            recordAudit(environment, context, "user.permission.clear-all", "user-permissions", subject.id().toString(), formatSubject(subject), beforeJson,
                    subjectPermissionsSnapshot(environment, subject.id()), true);
            environment.sendMessage(context.getSource(), CommandLang.permissionsClearAll(formatSubject(subject), removedPermissions));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getUserDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        Optional<DisplayText> directValue = subjectDisplayValue(environment, subject.id(), slot);
        if (directValue.isPresent()) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayDirect(formatSubject(subject), slot.label(), directValue.get().rawText()));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayDirectUnset(formatSubject(subject), slot.label()));
        }

        DisplayResolution resolution = environment.displayResolver().resolve(subject.id(), slot);
        if (resolution.value().isPresent()) {
            environment.sendMessage(context.getSource(),
                    CommandLang.userDisplayEffective(formatSubject(subject), slot.label(), resolution.value().get().rawText(), formatDisplaySource(resolution)));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayEffectiveUnset(formatSubject(subject), slot.label()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setUserDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        DisplayText displayText = getDisplayText(context);
        if (Optional.of(displayText).equals(subjectDisplayValue(environment, subject.id(), slot))) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayAlreadySet(slot.label(), formatSubject(subject), displayText.rawText()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectDisplaySnapshot(environment, subject.id());
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().setSubjectPrefix(subject.id(), displayText);
            } else {
                environment.subjectMetadataService().setSubjectSuffix(subject.id(), displayText);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        recordAudit(environment, context, "user.display." + slot.label() + ".set", "user-display", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectDisplaySnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userDisplaySet(slot.label(), formatSubject(subject), displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearUserDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        if (subjectDisplayValue(environment, subject.id(), slot).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userDisplayAlreadyClear(slot.label(), formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectDisplaySnapshot(environment, subject.id());
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().clearSubjectPrefix(subject.id());
            } else {
                environment.subjectMetadataService().clearSubjectSuffix(subject.id());
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        recordAudit(environment, context, "user.display." + slot.label() + ".clear", "user-display", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectDisplaySnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userDisplayClear(slot.label(), formatSubject(subject)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listSubjectGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        Set<String> groups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        if (environment.groupService().hasGroup(GroupService.DEFAULT_GROUP)) {
            groups.add(GroupService.DEFAULT_GROUP + " (implicit)");
        }

        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userGroupsEmpty(formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = groups.stream().map(group -> {
            String groupName = group.endsWith(" (implicit)") ? group.substring(0, group.indexOf(" (implicit)")) : group;
            return new PagedRow(group, fullCommand(rootLiteral, "group " + groupName + " list"));
        }).toList();
        sendPagedRows(environment, context, "Groups for " + formatSubject(subject), rows, "user " + StringArgumentType.getString(context, TARGET_ARGUMENT) + " groups");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listSubjectTracks(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            TrackSubjectState state = subjectTrackState(environment, subject.id(), trackName);
            if (!state.hasPosition() && !state.hasConflict()) {
                return null;
            }
            return new PagedRow(trackName + ": " + formatTrackSubjectState(state), fullCommand(rootLiteral, "track " + trackName + " list"));
        }).filter(Objects::nonNull).toList();
        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.userTracksEmpty(formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }
        sendPagedRows(environment, context, "Tracks for " + formatSubject(subject), rows, "user " + StringArgumentType.getString(context, TARGET_ARGUMENT) + " tracks");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int addSubjectGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        if (environment.groupService().getSubjectGroups(subject.id()).contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.userGroupAlreadyAdded(formatSubject(subject), normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectMembershipSnapshot(environment, subject.id());
        try {
            environment.groupService().addSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "user.group.add", "user-groups", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectMembershipSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userGroupAdded(formatSubject(subject), normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int removeSubjectGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        if (!environment.groupService().getSubjectGroups(subject.id()).contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.userGroupAlreadyRemoved(formatSubject(subject), normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectMembershipSnapshot(environment, subject.id());
        try {
            environment.groupService().removeSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "user.group.remove", "user-groups", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectMembershipSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userGroupRemoved(formatSubject(subject), normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int promoteSubjectOnTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        TrackSubjectState state = subjectTrackState(environment, subject.id(), trackName);
        if (state.hasConflict()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(
                    new IllegalArgumentException("user matches multiple explicit groups on track " + trackName + ": " + String.join(", ", state.explicitMatches()))));
        }
        if (state.trackGroups().isEmpty()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track has no groups: " + trackName)));
        }

        Set<String> updatedGroups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        String targetGroup;
        if (state.hasExplicitMatch()) {
            int currentIndex = state.currentIndex();
            if (currentIndex >= state.trackGroups().size() - 1) {
                throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("user is already at the end of track " + trackName)));
            }
            updatedGroups.remove(state.currentGroup());
            targetGroup = state.trackGroups().get(currentIndex + 1);
            updatedGroups.add(targetGroup);
        } else if (state.implicitDefault()) {
            if (state.trackGroups().size() < 2) {
                throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("user is already at the end of track " + trackName)));
            }
            targetGroup = state.trackGroups().get(1);
            updatedGroups.add(targetGroup);
        } else {
            targetGroup = state.trackGroups().getFirst();
            updatedGroups.add(targetGroup);
        }

        String beforeJson = subjectMembershipSnapshot(environment, subject.id());
        try {
            environment.groupService().setSubjectGroups(subject.id(), updatedGroups);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }
        recordAudit(environment, context, "user.track.promote", "user-groups", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectMembershipSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userTrackPromoted(formatSubject(subject), trackName, targetGroup));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int demoteSubjectOnTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        TrackSubjectState state = subjectTrackState(environment, subject.id(), trackName);
        if (state.hasConflict()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(
                    new IllegalArgumentException("user matches multiple explicit groups on track " + trackName + ": " + String.join(", ", state.explicitMatches()))));
        }
        if (state.trackGroups().isEmpty()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track has no groups: " + trackName)));
        }
        if (state.implicitDefault()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("user is already at the start of track " + trackName)));
        }
        if (!state.hasExplicitMatch()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("user is not on track " + trackName)));
        }

        int currentIndex = state.currentIndex();
        if (currentIndex == 0) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("user is already at the start of track " + trackName)));
        }

        Set<String> updatedGroups = new LinkedHashSet<>(environment.groupService().getSubjectGroups(subject.id()));
        updatedGroups.remove(state.currentGroup());
        String targetGroup = state.trackGroups().get(currentIndex - 1);
        if (!GroupService.DEFAULT_GROUP.equals(targetGroup)) {
            updatedGroups.add(targetGroup);
        }

        String beforeJson = subjectMembershipSnapshot(environment, subject.id());
        try {
            environment.groupService().setSubjectGroups(subject.id(), updatedGroups);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }
        recordAudit(environment, context, "user.track.demote", "user-groups", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectMembershipSnapshot(environment, subject.id()), true);
        environment.sendMessage(context.getSource(), CommandLang.userTrackDemoted(formatSubject(subject), trackName, targetGroup));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int mutateOpGroupMembership(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, boolean addMembership) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        boolean hasOpGroup = environment.groupService().getSubjectGroups(subject.id()).contains(GroupService.OP_GROUP);
        if (addMembership == hasOpGroup) {
            environment.sendMessage(context.getSource(),
                    addMembership
                            ? CommandLang.userGroupAlreadyAdded(formatSubject(subject), GroupService.OP_GROUP)
                            : CommandLang.userGroupAlreadyRemoved(formatSubject(subject), GroupService.OP_GROUP));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = subjectMembershipSnapshot(environment, subject.id());
        try {
            if (addMembership) {
                environment.groupService().addSubjectGroup(subject.id(), GroupService.OP_GROUP);
            } else {
                environment.groupService().removeSubjectGroup(subject.id(), GroupService.OP_GROUP);
            }
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.refreshRuntimePermissions();
        recordAudit(environment, context, addMembership ? "user.group.add" : "user.group.remove", "user-groups", subject.id().toString(), formatSubject(subject), beforeJson,
                subjectMembershipSnapshot(environment, subject.id()), true);
        if (addMembership) {
            environment.sendMessage(context.getSource(), CommandLang.userGroupAdded(formatSubject(subject), GroupService.OP_GROUP));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.userGroupRemoved(formatSubject(subject), GroupService.OP_GROUP));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int checkPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        PermissionResolution resolution = environment.permissionResolver().resolve(subject.id(), node);

        String source = formatResolutionSource(resolution);
        String assignmentNode = resolution.assignmentNode();
        if (assignmentNode != null && !assignmentNode.equals(PermissionNodes.normalize(node))) {
            environment.sendMessage(context.getSource(), CommandLang.permissionCheck(formatSubject(subject), node, resolution.value(), source, assignmentNode));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.permissionCheck(formatSubject(subject), node, resolution.value(), source));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int explainPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        PermissionExplanation explanation = environment.permissionResolver().explain(subject.id(), node);
        PermissionResolution resolution = explanation.resolution();

        environment.sendMessage(context.getSource(), CommandLang.permissionExplainHeader(formatSubject(subject), explanation.node()));
        if (resolution.value() == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.permissionExplainResultUnset());
        } else {
            String source = formatResolutionSource(resolution);
            String assignmentNode = resolution.assignmentNode();
            if (assignmentNode != null && !assignmentNode.equals(explanation.node())) {
                environment.sendMessage(context.getSource(), CommandLang.permissionExplainResult(resolution.value(), source, assignmentNode));
            } else {
                environment.sendMessage(context.getSource(), CommandLang.permissionExplainResult(resolution.value(), source));
            }
        }
        environment.sendMessage(context.getSource(), CommandLang.permissionExplainOrder());
        if (explanation.matches().isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionExplainNoMatches());
        } else {
            explanation.matches().forEach(match -> environment.sendMessage(context.getSource(),
                    CommandLang.permissionExplainMatch(formatExplanationSource(match), match.assignmentNode(), match.value(), match.winning())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        Set<String> groups = environment.groupService().getGroups();
        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupsEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = groups.stream().sorted(Comparator.naturalOrder()).map(group -> new PagedRow(group, fullCommand(rootLiteral, "group " + group + " list"))).toList();
        sendPagedRows(environment, context, "Groups", rows, "group list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int createGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
        String normalizedGroupName = normalizeGroupName(groupName);
        if (environment.groupService().hasGroup(normalizedGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupAlreadyExists(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupSnapshot(environment, normalizedGroupName);
        try {
            environment.groupService().createGroup(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.create", "group", normalizedGroupName, normalizedGroupName, beforeJson, groupSnapshot(environment, normalizedGroupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupCreated(normalizedGroupName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int deleteGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        if (GroupService.DEFAULT_GROUP.equals(groupName)) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("default group cannot be deleted")));
        }
        if (GroupService.OP_GROUP.equals(groupName)) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("op group cannot be deleted")));
        }
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("group-delete", groupName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupSnapshot(environment, groupName);
        try {
            environment.groupService().deleteGroup(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.delete", "group", groupName, groupName, beforeJson, groupSnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupDeleted(normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int renameGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String newGroupName = getNewGroupName(context);
        String normalizedNewGroupName = normalizeGroupName(newGroupName);
        if (normalizeGroupName(groupName).equals(normalizedNewGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupAlreadyNamed(normalizeGroupName(groupName), normalizedNewGroupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = renameGroupSnapshot(environment, groupName, normalizedNewGroupName);
        try {
            environment.groupService().renameGroup(groupName, newGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.rename", "group-rename", groupName + "->" + normalizedNewGroupName, groupName + " -> " + normalizedNewGroupName, beforeJson,
                renameGroupSnapshot(environment, groupName, normalizedNewGroupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupRenamed(normalizeGroupName(groupName), normalizeGroupName(newGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int showGroupInfo(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String normalizedGroupName = normalizeGroupName(groupName);
        Map<String, PermissionValue> permissions;
        Set<UUID> members;
        Set<String> parents;
        List<String> childGroups;
        try {
            permissions = environment.groupService().getGroupPermissions(normalizedGroupName);
            members = environment.groupService().getGroupMembers(normalizedGroupName);
            parents = environment.groupService().getGroupParents(normalizedGroupName);
            childGroups = findChildGroups(environment, normalizedGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        String rootLiteral = rootLiteral(context);
        String groupListCommand = fullCommand(rootLiteral, "group " + normalizedGroupName + " list");
        String groupMembersCommand = fullCommand(rootLiteral, "group " + normalizedGroupName + " members");
        String groupParentsCommand = fullCommand(rootLiteral, "group " + normalizedGroupName + " parents");
        String groupPrefixCommand = fullCommand(rootLiteral, "group " + normalizedGroupName + " prefix get");
        String groupSuffixCommand = fullCommand(rootLiteral, "group " + normalizedGroupName + " suffix get");

        List<PagedRow> rows = new ArrayList<>();
        rows.add(new PagedRow("name " + normalizedGroupName, groupListCommand));
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            rows.add(new PagedRow("default group applies implicitly", groupListCommand));
        } else if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
            rows.add(new PagedRow("op group grants * to explicit members only", groupMembersCommand));
        }
        rows.add(new PagedRow("direct permissions " + permissions.size(), groupListCommand));
        rows.add(new PagedRow("parents " + summarizeValues(parents), groupParentsCommand));
        rows.add(new PagedRow("child groups " + summarizeValues(childGroups), fullCommand(rootLiteral, "group list")));
        rows.add(new PagedRow("tracks " + summarizeGroupTracks(environment, normalizedGroupName), fullCommand(rootLiteral, "track list")));
        rows.add(new PagedRow("explicit members " + summarizeGroupMembers(environment, members), groupMembersCommand));
        rows.add(new PagedRow("prefix " + formatDisplayValue(groupDisplayValue(environment, normalizedGroupName, DisplaySlot.PREFIX)), groupPrefixCommand));
        rows.add(new PagedRow("suffix " + formatDisplayValue(groupDisplayValue(environment, normalizedGroupName, DisplaySlot.SUFFIX)), groupSuffixCommand));

        sendInfoRows(environment, context, "Group " + normalizedGroupName, rows);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String normalizedGroupName = normalizeGroupName(groupName);
        Map<String, PermissionValue> permissions;
        Set<UUID> members;
        Set<String> parents;
        try {
            permissions = environment.groupService().getGroupPermissions(groupName);
            members = environment.groupService().getGroupMembers(groupName);
            parents = environment.groupService().getGroupParents(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = new ArrayList<>();
        addGroupDisplayRows(environment, rows, rootLiteral, normalizedGroupName);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new PagedRow("permission " + entry.getKey() + "=" + entry.getValue().name(),
                fullCommand(rootLiteral, "group " + normalizedGroupName + " get " + entry.getKey()))).forEach(rows::add);
        parents.stream().sorted(Comparator.naturalOrder()).map(parent -> new PagedRow("parent " + parent, fullCommand(rootLiteral, "group " + parent + " list")))
                .forEach(rows::add);
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            rows.add(new PagedRow("default group applies implicitly", fullCommand(rootLiteral, "group default list")));
        } else {
            if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
                rows.add(new PagedRow("op group grants * to explicit members only", fullCommand(rootLiteral, "group op members")));
            }
            members.stream().sorted().map(subjectId -> new PagedRow("member " + formatSubject(subjectId, environment), fullCommand(rootLiteral, "user " + subjectId + " list")))
                    .forEach(rows::add);
        }

        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(normalizedGroupName));
            environment.sendMessage(context.getSource(), CommandLang.groupMembersEmpty(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }

        sendPagedRows(environment, context, "Group " + normalizedGroupName, rows, "group " + StringArgumentType.getString(context, GROUP_ARGUMENT) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroupMembers(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String normalizedGroupName = normalizeGroupName(groupName);
        Set<UUID> members;
        try {
            members = environment.groupService().getGroupMembers(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        if (members.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupMembersEmpty(normalizedGroupName));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = members.stream()
                .sorted(Comparator.comparing((UUID subjectId) -> formatSubject(subjectId, environment), String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString))
                .map(subjectId -> new PagedRow(formatSubject(subjectId, environment), fullCommand(rootLiteral, "user " + subjectId + " list"))).toList();
        sendPagedRows(environment, context, "Members of group " + normalizedGroupName, rows, "group " + StringArgumentType.getString(context, GROUP_ARGUMENT) + " members");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroupParents(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String normalizedGroupName = normalizeGroupName(groupName);
        Set<String> parents;
        try {
            parents = environment.groupService().getGroupParents(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        if (parents.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupParentsEmpty(normalizedGroupName));
        } else {
            String rootLiteral = rootLiteral(context);
            List<PagedRow> rows = parents.stream().sorted(Comparator.naturalOrder()).map(parent -> new PagedRow(parent, fullCommand(rootLiteral, "group " + parent + " list")))
                    .toList();
            sendPagedRows(environment, context, "Parents of group " + normalizedGroupName, rows, "group " + StringArgumentType.getString(context, GROUP_ARGUMENT) + " parents");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int addGroupParent(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String parentGroupName = requireExistingParentGroup(environment, context, getParentGroupName(context));
        if (environment.groupService().getGroupParents(groupName).contains(parentGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupParentAlreadyAdded(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupSnapshot(environment, groupName);
        try {
            environment.groupService().addGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.parent.add", "group", groupName, groupName, beforeJson, groupSnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupParentAdded(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int removeGroupParent(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String parentGroupName = requireExistingParentGroup(environment, context, getParentGroupName(context));
        if (!environment.groupService().getGroupParents(groupName).contains(parentGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupParentAlreadyRemoved(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupSnapshot(environment, groupName);
        try {
            environment.groupService().removeGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.parent.remove", "group", groupName, groupName, beforeJson, groupSnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupParentRemoved(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getGroupPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String node = getNode(context);
        PermissionValue value;
        try {
            value = environment.groupService().getGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupPermissionGet(normalizeGroupName(groupName), node, value));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setGroupPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        PermissionAssignment assignment = getPermissionAssignment(context);
        if (environment.groupService().getGroupPermission(groupName, assignment.node()) == assignment.value()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionAlreadySet(assignment.node(), normalizeGroupName(groupName), assignment.value()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupPermissionsSnapshot(environment, groupName);
        try {
            environment.groupService().setGroupPermission(groupName, assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.permission.set", "group-permissions", groupName, groupName, beforeJson, groupPermissionsSnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupPermissionSet(assignment.node(), normalizeGroupName(groupName), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearGroupPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String node = getNode(context);
        if (environment.groupService().getGroupPermission(groupName, node) == PermissionValue.UNSET) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionAlreadyClear(node, normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupPermissionsSnapshot(environment, groupName);
        try {
            environment.groupService().clearGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        recordAudit(environment, context, "group.permission.clear", "group-permissions", groupName, groupName, beforeJson, groupPermissionsSnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupPermissionClear(node, normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearAllGroupPermissions(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        if (GroupService.OP_GROUP.equals(groupName)) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("op group permissions are protected")));
        }
        if (environment.groupService().getGroupPermissions(groupName).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(groupName));
            return Command.SINGLE_SUCCESS;
        }
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("group-clear-all", groupName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupPermissionsSnapshot(environment, groupName);
        int removedPermissions;
        try {
            removedPermissions = environment.groupService().clearGroupPermissions(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        String normalizedGroupName = normalizeGroupName(groupName);
        if (removedPermissions == 0) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(normalizedGroupName));
        } else {
            recordAudit(environment, context, "group.permission.clear-all", "group-permissions", normalizedGroupName, normalizedGroupName, beforeJson,
                    groupPermissionsSnapshot(environment, normalizedGroupName), true);
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsClearAll(normalizedGroupName, removedPermissions));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getGroupDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        Optional<DisplayText> value = groupDisplayValue(environment, groupName, slot);
        if (value.isPresent()) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayGet(groupName, slot.label(), value.get().rawText()));
        } else {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayUnset(groupName, slot.label()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setGroupDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        DisplayText displayText = getDisplayText(context);
        if (Optional.of(displayText).equals(groupDisplayValue(environment, groupName, slot))) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayAlreadySet(slot.label(), groupName, displayText.rawText()));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupDisplaySnapshot(environment, groupName);
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().setGroupPrefix(groupName, displayText);
            } else {
                environment.groupService().setGroupSuffix(groupName, displayText);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        recordAudit(environment, context, "group.display." + slot.label() + ".set", "group-display", groupName, groupName, beforeJson, groupDisplaySnapshot(environment, groupName),
                true);
        environment.sendMessage(context.getSource(), CommandLang.groupDisplaySet(slot.label(), groupName, displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearGroupDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        if (groupDisplayValue(environment, groupName, slot).isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupDisplayAlreadyClear(slot.label(), groupName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = groupDisplaySnapshot(environment, groupName);
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().clearGroupPrefix(groupName);
            } else {
                environment.groupService().clearGroupSuffix(groupName);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        recordAudit(environment, context, "group.display." + slot.label() + ".clear", "group-display", groupName, groupName, beforeJson,
                groupDisplaySnapshot(environment, groupName), true);
        environment.sendMessage(context.getSource(), CommandLang.groupDisplayClear(slot.label(), groupName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listTracks(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        Set<String> tracks = environment.trackService().getTracks();
        if (tracks.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.tracksEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = tracks.stream().sorted(Comparator.naturalOrder()).map(track -> new PagedRow(track, fullCommand(rootLiteral, "track " + track + " list"))).toList();
        sendPagedRows(environment, context, "Tracks", rows, "track list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int createTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = getTrackName(context);
        String normalizedTrackName = normalizeTrackName(trackName);
        if (environment.trackService().hasTrack(normalizedTrackName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackAlreadyExists(normalizedTrackName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = trackSnapshot(environment, normalizedTrackName);
        try {
            environment.trackService().createTrack(trackName);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }

        recordAudit(environment, context, "track.create", "track", normalizedTrackName, normalizedTrackName, beforeJson, trackSnapshot(environment, normalizedTrackName), true);
        environment.sendMessage(context.getSource(), CommandLang.trackCreated(normalizedTrackName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int deleteTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        if (!confirmDestructiveCommand(environment, context, confirmationOperation("track-delete", trackName))) {
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = trackSnapshot(environment, trackName);
        try {
            environment.trackService().deleteTrack(trackName);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }

        recordAudit(environment, context, "track.delete", "track", trackName, trackName, beforeJson, trackSnapshot(environment, trackName), true);
        environment.sendMessage(context.getSource(), CommandLang.trackDeleted(trackName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int renameTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        String newTrackName = getNewTrackName(context);
        String normalizedNewTrackName = normalizeTrackName(newTrackName);
        if (normalizeTrackName(trackName).equals(normalizedNewTrackName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackAlreadyNamed(normalizeTrackName(trackName), normalizedNewTrackName));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = renameTrackSnapshot(environment, trackName, normalizedNewTrackName);
        try {
            environment.trackService().renameTrack(trackName, newTrackName);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }

        recordAudit(environment, context, "track.rename", "track-rename", trackName + "->" + normalizedNewTrackName, trackName + " -> " + normalizedNewTrackName, beforeJson,
                renameTrackSnapshot(environment, trackName, normalizedNewTrackName), true);
        environment.sendMessage(context.getSource(), CommandLang.trackRenamed(trackName, normalizedNewTrackName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int showTrackInfo(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        List<String> groups;
        try {
            groups = environment.trackService().getTrackGroups(trackName);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = new ArrayList<>();
        rows.add(new PagedRow("name " + trackName, fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new PagedRow("groups " + groups.size(), fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new PagedRow("first " + (groups.isEmpty() ? "none" : groups.getFirst()), fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new PagedRow("last " + (groups.isEmpty() ? "none" : groups.getLast()), fullCommand(rootLiteral, "track " + trackName + " list")));
        rows.add(new PagedRow("ordered groups " + summarizeValues(groups), fullCommand(rootLiteral, "track " + trackName + " list")));
        sendInfoRows(environment, context, "Track " + trackName, rows);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        List<String> groups;
        try {
            groups = environment.trackService().getTrackGroups(trackName);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }

        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupsEmpty(trackName));
            return Command.SINGLE_SUCCESS;
        }

        List<PagedRow> rows = new ArrayList<>();
        String rootLiteral = rootLiteral(context);
        for (int index = 0; index < groups.size(); index++) {
            String groupName = groups.get(index);
            rows.add(new PagedRow("#" + (index + 1) + " " + groupName, fullCommand(rootLiteral, "group " + groupName + " list")));
        }
        sendPagedRows(environment, context, "Track " + trackName, rows, "track " + StringArgumentType.getString(context, TRACK_ARGUMENT) + " list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int appendTrackGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPresent(trackName, normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.add(groupName);
        return updateTrackGroups(environment, context, trackName, updatedGroups, "track.group.append", CommandLang.trackGroupAppended(trackName, normalizeGroupName(groupName)));
    }

    private static <S> int insertTrackGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPresent(trackName, normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        int position = getTrackPosition(context);
        if (position > updatedGroups.size() + 1) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track position out of range: " + position)));
        }
        updatedGroups.add(position - 1, groupName);
        return updateTrackGroups(environment, context, trackName, updatedGroups, "track.group.insert",
                CommandLang.trackGroupInserted(trackName, normalizeGroupName(groupName), position));
    }

    private static <S> int moveTrackGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        String groupName = requireTrackGroup(environment, context, trackName, getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        int position = getTrackPosition(context);
        if (position > updatedGroups.size()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track position out of range: " + position)));
        }
        if (updatedGroups.indexOf(groupName) == position - 1) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyPositioned(trackName, normalizeGroupName(groupName), position));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.remove(groupName);
        updatedGroups.add(position - 1, groupName);
        return updateTrackGroups(environment, context, trackName, updatedGroups, "track.group.move",
                CommandLang.trackGroupMoved(trackName, normalizeGroupName(groupName), position));
    }

    private static <S> int removeTrackGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String trackName = requireExistingTrack(environment, context, getTrackName(context));
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        List<String> updatedGroups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
        if (!updatedGroups.contains(groupName)) {
            environment.sendMessage(context.getSource(), CommandLang.trackGroupAlreadyAbsent(trackName, normalizeGroupName(groupName)));
            return Command.SINGLE_SUCCESS;
        }
        updatedGroups.remove(groupName);
        return updateTrackGroups(environment, context, trackName, updatedGroups, "track.group.remove", CommandLang.trackGroupRemoved(trackName, normalizeGroupName(groupName)));
    }

    private static <S> int updateTrackGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String trackName, List<String> updatedGroups, String action,
            CommandMessage successMessage) throws CommandSyntaxException {
        String beforeJson = trackSnapshot(environment, trackName);
        try {
            environment.trackService().setTrackGroups(trackName, updatedGroups);
        } catch (RuntimeException exception) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(exception));
        }
        recordAudit(environment, context, action, "track", trackName, trackName, beforeJson, trackSnapshot(environment, trackName), true);
        environment.sendMessage(context.getSource(), successMessage);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listSubjects(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        Map<UUID, SubjectMetadata> subjects = environment.subjectMetadataService().getSubjects();
        if (subjects.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = subjects.values().stream()
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(subject -> new PagedRow(formatSubjectMetadata(subject), fullCommand(rootLiteral, "user " + subject.subjectId() + " list"))).toList();
        sendPagedRows(environment, context, "Known users", rows, "users list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int searchSubjects(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String query = StringArgumentType.getString(context, NAME_ARGUMENT).trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(subject -> new PagedRow(formatSubjectMetadata(subject), fullCommand(rootLiteral, "user " + subject.subjectId() + " list"))).toList();

        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        sendPagedRows(environment, context, "Matched users", rows, "users search " + query);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listKnownNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        List<KnownPermissionNode> nodes = environment.permissionNodeRegistry().getKnownNodes().stream().sorted(Comparator.comparing(KnownPermissionNode::node)).toList();
        if (nodes.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = nodes.stream().map(node -> new PagedRow(formatKnownNode(node), knownNodeCommand(rootLiteral, node))).toList();
        sendPagedRows(environment, context, "Known permission nodes", rows, "nodes list");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int searchKnownNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String query = StringArgumentType.getString(context, QUERY_ARGUMENT).trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        List<KnownPermissionNode> nodes = environment.permissionNodeRegistry().getKnownNodes().stream()
                .filter(node -> node.node().contains(normalizedQuery) || node.description().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(KnownPermissionNode::node)).toList();
        if (nodes.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = nodes.stream().map(node -> new PagedRow(formatKnownNode(node), knownNodeCommand(rootLiteral, node))).toList();
        sendPagedRows(environment, context, "Matched known permission nodes", rows, "nodes search " + query);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int addKnownNode(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, NODE_ARGUMENT).trim();
        int nodeEnd = firstWhitespaceIndex(input);
        String node = nodeEnd < 0 ? input : input.substring(0, nodeEnd);
        String description = nodeEnd < 0 ? "" : input.substring(nodeEnd).trim();
        String normalizedNode;
        try {
            normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
            environment.manualPermissionNodeRegistry().addNode(normalizedNode, description);
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            throw NODE_OPERATION_FAILED.create(CommandLang.nodeOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.nodeAdded(normalizedNode));
        return Command.SINGLE_SUCCESS;
    }

    private static int firstWhitespaceIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static <S> int removeKnownNode(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, NODE_ARGUMENT);
        String normalizedNode;
        try {
            normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
        } catch (IllegalArgumentException exception) {
            throw NODE_OPERATION_FAILED.create(CommandLang.nodeOperationFailed(exception));
        }
        requireManuallyRegisteredNode(environment, context, normalizedNode);
        try {
            environment.manualPermissionNodeRegistry().removeNode(normalizedNode);
        } catch (RuntimeException exception) {
            throw NODE_OPERATION_FAILED.create(CommandLang.nodeOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.nodeRemoved(normalizedNode));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> CommandSubject resolveSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject resolvedSubject = RESOLVED_SUBJECT_OVERRIDE.get();
        if (resolvedSubject != null) {
            return resolvedSubject;
        }

        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        Optional<ResolvedSubject> immediateSubject = resolveImmediateSubject(environment, context, target);
        if (immediateSubject.isPresent()) {
            return immediateSubject.get().subject();
        }
        throw unknownUserTargetFeedback(environment, context, target);
    }

    private static <S> Optional<CommandSubject> resolveKnownSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String target)
            throws CommandSyntaxException {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        List<SubjectMetadata> matches = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).equals(normalizedTarget))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId)).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw ambiguousKnownUserFeedback(target, matches);
        }

        SubjectMetadata subject = matches.getFirst();
        return Optional.of(new CommandSubject(subject.subjectId(), subject.lastKnownName()));
    }

    private static <S> ConfigEntry getConfigEntry(CommandContext<S> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, CONFIG_KEY_ARGUMENT);
        return findConfigEntry(key).orElseThrow(() -> unknownConfigKeyFeedback(context, key));
    }

    private static Optional<ConfigEntry> findConfigEntry(String key) {
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        return CONFIG_ENTRIES.stream().filter(entry -> entry.key().toLowerCase(Locale.ROOT).equals(normalizedKey)).findFirst();
    }

    private static ConfigEntry configEntry(String key) {
        return findConfigEntry(key).orElseThrow(() -> new IllegalStateException("Missing config entry " + key));
    }

    private static <S> String parseConfigValue(CommandContext<S> context, ConfigEntry entry, String rawValue) throws CommandSyntaxException {
        try {
            return entry.normalizeValue(rawValue);
        } catch (IllegalArgumentException exception) {
            throw invalidConfigValueFeedback(context, entry, rawValue);
        }
    }

    private static <S> String getNode(CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, NODE_ARGUMENT);
        return validateNode(node);
    }

    private static <S> PermissionAssignment getPermissionAssignment(CommandContext<S> context) throws CommandSyntaxException {
        String assignment = StringArgumentType.getString(context, ASSIGNMENT_ARGUMENT).trim();
        int valueStart = lastWhitespaceRunStart(assignment);
        if (valueStart <= 0) {
            throw INVALID_VALUE.create(assignment);
        }

        String node = validateNode(assignment.substring(0, valueStart).trim());
        String rawValue = assignment.substring(valueStart).trim();
        PermissionValue value;
        if ("true".equalsIgnoreCase(rawValue)) {
            value = PermissionValue.TRUE;
        } else if ("false".equalsIgnoreCase(rawValue)) {
            value = PermissionValue.FALSE;
        } else {
            throw INVALID_VALUE.create(rawValue);
        }

        return new PermissionAssignment(node, value);
    }

    private static <S> DisplayText getDisplayText(CommandContext<S> context) throws CommandSyntaxException {
        String rawDisplayText = StringArgumentType.getString(context, DISPLAY_VALUE_ARGUMENT);
        try {
            return DisplayText.parse(rawDisplayText);
        } catch (IllegalArgumentException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
    }

    private static String validateNode(String node) throws CommandSyntaxException {
        try {
            PermissionNodes.normalize(node);
        } catch (IllegalArgumentException exception) {
            throw INVALID_NODE.create(node);
        }
        return node;
    }

    private static int lastWhitespaceRunStart(String value) {
        int index = value.length() - 1;
        while (index >= 0 && !Character.isWhitespace(value.charAt(index))) {
            index--;
        }
        while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static <S> String getGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = StringArgumentType.getString(context, GROUP_ARGUMENT);
        if (groupName.trim().isEmpty()) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("group name must not be blank")));
        }
        return groupName;
    }

    private static <S> String getParentGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String parentGroupName = StringArgumentType.getString(context, PARENT_ARGUMENT);
        if (parentGroupName.trim().isEmpty()) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("group name must not be blank")));
        }
        return parentGroupName;
    }

    private static <S> String getTrackName(CommandContext<S> context) throws CommandSyntaxException {
        String trackName = StringArgumentType.getString(context, TRACK_ARGUMENT);
        if (trackName.trim().isEmpty()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track name must not be blank")));
        }
        return trackName;
    }

    private static <S> String getNewGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String newGroupName = StringArgumentType.getString(context, NEW_GROUP_ARGUMENT);
        if (newGroupName.trim().isEmpty()) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("group name must not be blank")));
        }
        return newGroupName;
    }

    private static <S> String getNewTrackName(CommandContext<S> context) throws CommandSyntaxException {
        String newTrackName = StringArgumentType.getString(context, NEW_TRACK_ARGUMENT);
        if (newTrackName.trim().isEmpty()) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("track name must not be blank")));
        }
        return newTrackName;
    }

    private static <S> int getTrackPosition(CommandContext<S> context) {
        return IntegerArgumentType.getInteger(context, POSITION_ARGUMENT);
    }

    private static <S> String requireExistingGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String groupName) throws CommandSyntaxException {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            throw unknownGroupTargetFeedback(environment, context, normalizedGroupName, false);
        }
        return normalizedGroupName;
    }

    private static <S> String requireExistingParentGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String groupName) throws CommandSyntaxException {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            throw unknownGroupTargetFeedback(environment, context, normalizedGroupName, true);
        }
        return normalizedGroupName;
    }

    private static <S> String requireExistingTrack(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String trackName) throws CommandSyntaxException {
        String normalizedTrackName = normalizeTrackName(trackName);
        if (!environment.trackService().hasTrack(normalizedTrackName)) {
            throw unknownTrackTargetFeedback(environment, context, normalizedTrackName);
        }
        return normalizedTrackName;
    }

    private static <S> String requireTrackGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String trackName, String groupName)
            throws CommandSyntaxException {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (!environment.trackService().getTrackGroups(trackName).contains(normalizedGroupName)) {
            throw TRACK_OPERATION_FAILED.create(CommandLang.trackOperationFailed(new IllegalArgumentException("group is not on track " + trackName + ": " + normalizedGroupName)));
        }
        return normalizedGroupName;
    }

    private static <S> StorageBackup requireKnownBackupFile(CommandContext<S> context, String backupFileName, List<StorageBackup> backups) throws CommandSyntaxException {
        Optional<StorageBackup> knownBackupFile = backups.stream().filter(backup -> backup.fileName().equals(backupFileName)).findFirst();
        if (knownBackupFile.isPresent()) {
            return knownBackupFile.get();
        }

        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownBackupFile(backupFileName));
        List<String> matches = closestMatches(backupFileName, backups.stream().map(backup -> candidate(backup.fileName())).toList());
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestBackupFiles(String.join(", ", matches)));
        }
        messages.add(CommandLang.tryHeader());
        messages.add(CommandLang.suggestion(rootLiteral(context), "backup list"));
        throw feedback(messages);
    }

    private static <S> void validateBackup(ClutchPermsCommandEnvironment<S> environment, StorageFileKind kind, StorageBackup backup) throws CommandSyntaxException {
        try {
            environment.validateBackup(kind, backup.path());
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED
                    .create(CommandLang.backupOperationFailed(new PermissionStorageException("Failed to validate " + kind.token() + " backup " + backup.fileName(), exception)));
        }
    }

    private static <S> boolean confirmDestructiveCommand(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String operationKey) {
        ConfirmationSource source = confirmationSource(environment, context.getSource());
        Instant now;
        synchronized (PENDING_CONFIRMATIONS) {
            now = confirmationClock.instant();
            PendingConfirmation pending = PENDING_CONFIRMATIONS.get(source);
            if (pending != null && pending.operationKey().equals(operationKey) && !now.isAfter(pending.expiresAt())) {
                PENDING_CONFIRMATIONS.remove(source);
                return true;
            }
            PENDING_CONFIRMATIONS.put(source, new PendingConfirmation(operationKey, now.plus(DESTRUCTIVE_CONFIRMATION_TTL)));
        }

        String command = confirmationCommand(context);
        environment.sendMessage(context.getSource(), CommandLang.confirmationRequired());
        environment.sendMessage(context.getSource(), CommandLang.confirmationRepeat(command, DESTRUCTIVE_CONFIRMATION_TTL.toSeconds()));
        return false;
    }

    private static <S> ConfirmationSource confirmationSource(ClutchPermsCommandEnvironment<S> environment, S source) {
        if (environment.sourceKind(source) == CommandSourceKind.PLAYER) {
            return new ConfirmationSource(environment.sourceSubjectId(source).orElseThrow(() -> new IllegalStateException("player command source has no subject id")));
        }
        return new ConfirmationSource(CONSOLE_CONFIRMATION_SOURCE);
    }

    private static <S> String confirmationCommand(CommandContext<S> context) {
        String input = context.getInput().trim();
        return input.startsWith("/") ? input : "/" + input;
    }

    private static String confirmationOperation(String action, String target) {
        return action + ":" + target;
    }

    private static <S> void recordAudit(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String action, String targetType, String targetKey,
            String targetDisplay, String beforeJson, String afterJson, boolean undoable) {
        try {
            environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(environment, context.getSource()), action, targetType, targetKey, targetDisplay, canonicalJson(beforeJson), canonicalJson(afterJson),
                            confirmationCommand(context), undoable));
        } catch (RuntimeException exception) {
            environment.sendMessage(context.getSource(), CommandLang.auditFailed(exception));
            return;
        }
        applyAutomaticAuditRetention(environment, context.getSource());
    }

    private static <S> void applyAutomaticAuditRetention(ClutchPermsCommandEnvironment<S> environment, S source) {
        try {
            AuditLogRetention.apply(environment.config(), environment.auditLogService(), Clock.systemUTC());
        } catch (RuntimeException exception) {
            environment.sendMessage(source, CommandLang.auditRetentionFailed(exception));
        }
    }

    private static <S> Optional<String> actorName(ClutchPermsCommandEnvironment<S> environment, S source) {
        if (environment.sourceKind(source) == CommandSourceKind.PLAYER) {
            return environment.sourceSubjectId(source).flatMap(subjectId -> environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName));
        }
        if (environment.sourceKind(source) == CommandSourceKind.CONSOLE) {
            return Optional.of("console");
        }
        return Optional.empty();
    }

    private static <S> PagedRow historyRow(String rootLiteral, AuditEntry entry) {
        String status = entry.undone() ? " undone" : "";
        String text = "#" + entry.id() + " " + entry.timestamp() + " " + entry.action() + " " + entry.targetDisplay() + " by " + formatAuditActor(entry) + status;
        return new PagedRow(text, fullCommand(rootLiteral, "undo " + entry.id()));
    }

    private static String formatAuditActor(AuditEntry entry) {
        return entry.actorName().or(() -> entry.actorId().map(UUID::toString)).orElse(entry.actorKind().name().toLowerCase(Locale.ROOT));
    }

    private static <S> int undoAuditEntry(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, AUDIT_ID_ARGUMENT);
        AuditEntry entry = environment.auditLogService().get(id).orElseThrow(() -> AUDIT_OPERATION_FAILED.create(CommandLang.auditMissing(id)));
        if (!entry.undoable()) {
            throw AUDIT_OPERATION_FAILED.create(CommandLang.auditNotUndoable(id));
        }
        if (entry.undone()) {
            throw AUDIT_OPERATION_FAILED.create(CommandLang.auditAlreadyUndone(id));
        }

        String currentJson = currentUndoSnapshot(environment, entry);
        if (!canonicalJson(currentJson).equals(canonicalJson(entry.afterJson()))) {
            throw AUDIT_OPERATION_FAILED.create(CommandLang.auditConflict(id));
        }

        applyUndoSnapshot(environment, entry);
        AuditEntry undoEntry;
        try {
            undoEntry = environment.auditLogService()
                    .append(new AuditLogRecord(Instant.now(), environment.sourceKind(context.getSource()), environment.sourceSubjectId(context.getSource()),
                            actorName(environment, context.getSource()), "undo", entry.targetType(), entry.targetKey(), "undo #" + entry.id() + " " + entry.targetDisplay(),
                            canonicalJson(entry.afterJson()), canonicalJson(entry.beforeJson()), confirmationCommand(context), false));
            environment.auditLogService().markUndone(entry.id(), undoEntry.id(), undoEntry.timestamp(), environment.sourceSubjectId(context.getSource()),
                    actorName(environment, context.getSource()));
        } catch (RuntimeException exception) {
            environment.sendMessage(context.getSource(), CommandLang.auditFailed(exception));
            undoEntry = null;
        }
        if (undoEntry != null) {
            applyAutomaticAuditRetention(environment, context.getSource());
        }
        environment.refreshRuntimePermissions();
        environment.sendMessage(context.getSource(), CommandLang.auditUndone(id));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> String currentUndoSnapshot(ClutchPermsCommandEnvironment<S> environment, AuditEntry entry) {
        return switch (entry.targetType()) {
            case "user-permissions" -> subjectPermissionsSnapshot(environment, UUID.fromString(entry.targetKey()));
            case "user-display" -> subjectDisplaySnapshot(environment, UUID.fromString(entry.targetKey()));
            case "user-groups" -> subjectMembershipSnapshot(environment, UUID.fromString(entry.targetKey()));
            case "group" -> groupSnapshot(environment, entry.targetKey());
            case "group-rename" -> renameSnapshotForEntry(environment, entry);
            case "group-permissions" -> groupPermissionsSnapshot(environment, entry.targetKey());
            case "group-display" -> groupDisplaySnapshot(environment, entry.targetKey());
            case "track" -> trackSnapshot(environment, entry.targetKey());
            case "track-rename" -> renameTrackSnapshotForEntry(environment, entry);
            case "config" -> configSnapshot(environment.config());
            default -> throw new IllegalArgumentException("unsupported undo target type: " + entry.targetType());
        };
    }

    private static <S> void applyUndoSnapshot(ClutchPermsCommandEnvironment<S> environment, AuditEntry entry) {
        switch (entry.targetType()) {
            case "user-permissions" -> applySubjectPermissionsSnapshot(environment, UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "user-display" -> applySubjectDisplaySnapshot(environment, UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "user-groups" -> applySubjectMembershipSnapshot(environment, UUID.fromString(entry.targetKey()), entry.beforeJson());
            case "group" -> applyGroupSnapshot(environment, entry.targetKey(), entry.beforeJson());
            case "group-rename" -> applyRenameSnapshot(environment, entry.beforeJson());
            case "group-permissions" -> applyGroupPermissionsSnapshot(environment, entry.targetKey(), entry.beforeJson());
            case "group-display" -> applyGroupDisplaySnapshot(environment, entry.targetKey(), entry.beforeJson());
            case "track" -> applyTrackSnapshot(environment, entry.targetKey(), entry.beforeJson());
            case "track-rename" -> applyRenameTrackSnapshot(environment, entry.beforeJson());
            case "config" -> applyConfigSnapshot(environment, entry.beforeJson());
            default -> throw new IllegalArgumentException("unsupported undo target type: " + entry.targetType());
        }
    }

    private static <S> String subjectPermissionsSnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId) {
        JsonObject root = new JsonObject();
        JsonObject permissions = new JsonObject();
        new TreeMap<>(environment.permissionService().getPermissions(subjectId)).forEach((node, value) -> permissions.addProperty(node, value.name()));
        root.add("permissions", permissions);
        return GSON.toJson(root);
    }

    private static <S> void applySubjectPermissionsSnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, String snapshotJson) {
        JsonObject permissions = JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonObject("permissions");
        environment.permissionService().clearPermissions(subjectId);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> environment.permissionService().setPermission(subjectId, entry.getKey(), PermissionValue.valueOf(entry.getValue().getAsString())));
    }

    private static <S> String subjectDisplaySnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId) {
        return displaySnapshot(environment.subjectMetadataService().getSubjectDisplay(subjectId));
    }

    private static <S> void applySubjectDisplaySnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applySubjectDisplayValue(environment, subjectId, DisplaySlot.PREFIX, root.get("prefix"));
        applySubjectDisplayValue(environment, subjectId, DisplaySlot.SUFFIX, root.get("suffix"));
    }

    private static <S> void applySubjectDisplayValue(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, DisplaySlot slot, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().clearSubjectPrefix(subjectId);
            } else {
                environment.subjectMetadataService().clearSubjectSuffix(subjectId);
            }
            return;
        }
        DisplayText displayText = DisplayText.parse(value.getAsString());
        if (slot == DisplaySlot.PREFIX) {
            environment.subjectMetadataService().setSubjectPrefix(subjectId, displayText);
        } else {
            environment.subjectMetadataService().setSubjectSuffix(subjectId, displayText);
        }
    }

    private static <S> String subjectMembershipSnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId) {
        JsonObject root = new JsonObject();
        JsonArray groups = new JsonArray();
        environment.groupService().getSubjectGroups(subjectId).stream().sorted().forEach(groups::add);
        root.add("groups", groups);
        return GSON.toJson(root);
    }

    private static <S> void applySubjectMembershipSnapshot(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, String snapshotJson) {
        Set<String> desiredGroups = stringSet(JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonArray("groups"));
        environment.groupService().setSubjectGroups(subjectId, desiredGroups);
    }

    private static <S> String groupSnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        JsonObject root = new JsonObject();
        root.addProperty("name", normalizedGroupName);
        boolean exists = environment.groupService().hasGroup(normalizedGroupName);
        root.addProperty("exists", exists);
        if (!exists) {
            return GSON.toJson(root);
        }
        root.add("permissions", permissionsJson(environment.groupService().getGroupPermissions(normalizedGroupName)));
        root.add("display", JsonParser.parseString(groupDisplaySnapshot(environment, normalizedGroupName)));
        root.add("parents", stringArray(environment.groupService().getGroupParents(normalizedGroupName)));
        root.add("members", uuidArray(environment.groupService().getGroupMembers(normalizedGroupName)));
        root.add("children", stringArray(findChildGroups(environment, normalizedGroupName)));
        root.add("tracks", groupTrackReferencesJson(environment, normalizedGroupName));
        return GSON.toJson(root);
    }

    private static <S> void applyGroupSnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        String normalizedGroupName = root.get("name").getAsString();
        if (!root.get("exists").getAsBoolean()) {
            if (environment.groupService().hasGroup(normalizedGroupName)) {
                environment.groupService().deleteGroup(normalizedGroupName);
            }
            return;
        }
        if (!environment.groupService().hasGroup(normalizedGroupName)) {
            environment.groupService().createGroup(normalizedGroupName);
        }
        applyGroupPermissionsSnapshot(environment, normalizedGroupName, objectWith("permissions", root.getAsJsonObject("permissions")));
        applyGroupDisplaySnapshot(environment, normalizedGroupName, root.getAsJsonObject("display").toString());
        applyGroupParents(environment, normalizedGroupName, stringSet(root.getAsJsonArray("parents")));
        applyGroupMembers(environment, normalizedGroupName, uuidSet(root.getAsJsonArray("members")));
        applyGroupTracks(environment, normalizedGroupName, trackReferencePositions(root.getAsJsonArray("tracks")));
        for (String child : stringSet(root.getAsJsonArray("children"))) {
            if (environment.groupService().hasGroup(child) && !environment.groupService().getGroupParents(child).contains(normalizedGroupName)) {
                environment.groupService().addGroupParent(child, normalizedGroupName);
            }
        }
    }

    private static <S> String groupPermissionsSnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        return objectWith("permissions", permissionsJson(environment.groupService().getGroupPermissions(groupName)));
    }

    private static <S> void applyGroupPermissionsSnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName, String snapshotJson) {
        JsonObject permissions = JsonParser.parseString(snapshotJson).getAsJsonObject().getAsJsonObject("permissions");
        environment.groupService().clearGroupPermissions(groupName);
        permissions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> environment.groupService().setGroupPermission(groupName, entry.getKey(), PermissionValue.valueOf(entry.getValue().getAsString())));
    }

    private static <S> String groupDisplaySnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        return displaySnapshot(environment.groupService().getGroupDisplay(groupName));
    }

    private static <S> void applyGroupDisplaySnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyGroupDisplayValue(environment, groupName, DisplaySlot.PREFIX, root.get("prefix"));
        applyGroupDisplayValue(environment, groupName, DisplaySlot.SUFFIX, root.get("suffix"));
    }

    private static <S> void applyGroupDisplayValue(ClutchPermsCommandEnvironment<S> environment, String groupName, DisplaySlot slot, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().clearGroupPrefix(groupName);
            } else {
                environment.groupService().clearGroupSuffix(groupName);
            }
            return;
        }
        DisplayText displayText = DisplayText.parse(value.getAsString());
        if (slot == DisplaySlot.PREFIX) {
            environment.groupService().setGroupPrefix(groupName, displayText);
        } else {
            environment.groupService().setGroupSuffix(groupName, displayText);
        }
    }

    private static <S> void applyGroupParents(ClutchPermsCommandEnvironment<S> environment, String groupName, Set<String> desiredParents) {
        Set<String> currentParents = environment.groupService().getGroupParents(groupName);
        currentParents.stream().filter(parent -> !desiredParents.contains(parent)).toList().forEach(parent -> environment.groupService().removeGroupParent(groupName, parent));
        desiredParents.stream().filter(parent -> !currentParents.contains(parent)).forEach(parent -> environment.groupService().addGroupParent(groupName, parent));
    }

    private static <S> void applyGroupMembers(ClutchPermsCommandEnvironment<S> environment, String groupName, Set<UUID> desiredMembers) {
        Set<UUID> currentMembers = environment.groupService().getGroupMembers(groupName);
        currentMembers.stream().filter(member -> !desiredMembers.contains(member)).toList().forEach(member -> environment.groupService().removeSubjectGroup(member, groupName));
        desiredMembers.stream().filter(member -> !currentMembers.contains(member)).forEach(member -> environment.groupService().addSubjectGroup(member, groupName));
    }

    private static <S> String trackSnapshot(ClutchPermsCommandEnvironment<S> environment, String trackName) {
        String normalizedTrackName = normalizeTrackName(trackName);
        JsonObject root = new JsonObject();
        root.addProperty("name", normalizedTrackName);
        boolean exists = environment.trackService().hasTrack(normalizedTrackName);
        root.addProperty("exists", exists);
        if (!exists) {
            return GSON.toJson(root);
        }
        root.add("groups", orderedStringArray(environment.trackService().getTrackGroups(normalizedTrackName)));
        return GSON.toJson(root);
    }

    private static <S> void applyTrackSnapshot(ClutchPermsCommandEnvironment<S> environment, String trackName, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        String normalizedTrackName = root.get("name").getAsString();
        if (!root.get("exists").getAsBoolean()) {
            if (environment.trackService().hasTrack(normalizedTrackName)) {
                environment.trackService().deleteTrack(normalizedTrackName);
            }
            return;
        }
        if (!environment.trackService().hasTrack(normalizedTrackName)) {
            environment.trackService().createTrack(normalizedTrackName);
        }
        environment.trackService().setTrackGroups(normalizedTrackName, stringList(root.getAsJsonArray("groups")));
    }

    private static <S> String renameTrackSnapshot(ClutchPermsCommandEnvironment<S> environment, String trackName, String newTrackName) {
        JsonObject root = new JsonObject();
        root.addProperty("oldName", normalizeTrackName(trackName));
        root.addProperty("newName", normalizeTrackName(newTrackName));
        root.add("old", JsonParser.parseString(trackSnapshot(environment, trackName)));
        root.add("new", JsonParser.parseString(trackSnapshot(environment, newTrackName)));
        return GSON.toJson(root);
    }

    private static <S> String renameTrackSnapshotForEntry(ClutchPermsCommandEnvironment<S> environment, AuditEntry entry) {
        JsonObject before = JsonParser.parseString(entry.beforeJson()).getAsJsonObject();
        return renameTrackSnapshot(environment, before.get("oldName").getAsString(), before.get("newName").getAsString());
    }

    private static <S> void applyRenameTrackSnapshot(ClutchPermsCommandEnvironment<S> environment, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyTrackSnapshot(environment, root.get("newName").getAsString(), root.getAsJsonObject("new").toString());
        applyTrackSnapshot(environment, root.get("oldName").getAsString(), root.getAsJsonObject("old").toString());
    }

    private static <S> String renameGroupSnapshot(ClutchPermsCommandEnvironment<S> environment, String groupName, String newGroupName) {
        JsonObject root = new JsonObject();
        root.addProperty("oldName", normalizeGroupName(groupName));
        root.addProperty("newName", normalizeGroupName(newGroupName));
        root.add("old", JsonParser.parseString(groupSnapshot(environment, groupName)));
        root.add("new", JsonParser.parseString(groupSnapshot(environment, newGroupName)));
        return GSON.toJson(root);
    }

    private static <S> String renameSnapshotForEntry(ClutchPermsCommandEnvironment<S> environment, AuditEntry entry) {
        JsonObject before = JsonParser.parseString(entry.beforeJson()).getAsJsonObject();
        return renameGroupSnapshot(environment, before.get("oldName").getAsString(), before.get("newName").getAsString());
    }

    private static <S> void applyRenameSnapshot(ClutchPermsCommandEnvironment<S> environment, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        applyGroupSnapshot(environment, root.get("newName").getAsString(), root.getAsJsonObject("new").toString());
        applyGroupSnapshot(environment, root.get("oldName").getAsString(), root.getAsJsonObject("old").toString());
    }

    private static String configSnapshot(ClutchPermsConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("backups.retentionLimit", config.backups().retentionLimit());
        root.addProperty("backups.schedule.enabled", config.backups().schedule().enabled());
        root.addProperty("backups.schedule.intervalMinutes", config.backups().schedule().intervalMinutes());
        root.addProperty("backups.schedule.runOnStartup", config.backups().schedule().runOnStartup());
        root.addProperty("audit.retention.enabled", config.audit().enabled());
        root.addProperty("audit.retention.maxAgeDays", config.audit().maxAgeDays());
        root.addProperty("audit.retention.maxEntries", config.audit().maxEntries());
        root.addProperty("commands.helpPageSize", config.commands().helpPageSize());
        root.addProperty("commands.resultPageSize", config.commands().resultPageSize());
        root.addProperty("chat.enabled", config.chat().enabled());
        root.addProperty("paper.replaceOpCommands", config.paper().replaceOpCommands());
        return GSON.toJson(root);
    }

    private static <S> void applyConfigSnapshot(ClutchPermsCommandEnvironment<S> environment, String snapshotJson) {
        JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
        ClutchPermsBackupScheduleConfig defaultSchedule = ClutchPermsBackupScheduleConfig.defaults();
        ClutchPermsAuditRetentionConfig defaultAuditRetention = ClutchPermsAuditRetentionConfig.defaults();
        ClutchPermsConfig restoredConfig = new ClutchPermsConfig(
                new ClutchPermsBackupConfig(root.get("backups.retentionLimit").getAsInt(),
                        new ClutchPermsBackupScheduleConfig(booleanSnapshotValue(root, "backups.schedule.enabled", defaultSchedule.enabled()),
                                integerSnapshotValue(root, "backups.schedule.intervalMinutes", defaultSchedule.intervalMinutes()),
                                booleanSnapshotValue(root, "backups.schedule.runOnStartup", defaultSchedule.runOnStartup()))),
                new ClutchPermsAuditRetentionConfig(booleanSnapshotValue(root, "audit.retention.enabled", defaultAuditRetention.enabled()),
                        integerSnapshotValue(root, "audit.retention.maxAgeDays", defaultAuditRetention.maxAgeDays()),
                        integerSnapshotValue(root, "audit.retention.maxEntries", defaultAuditRetention.maxEntries())),
                new ClutchPermsCommandConfig(root.get("commands.helpPageSize").getAsInt(), root.get("commands.resultPageSize").getAsInt()),
                new ClutchPermsChatConfig(root.get("chat.enabled").getAsBoolean()), new ClutchPermsPaperConfig(root.get("paper.replaceOpCommands").getAsBoolean()));
        environment.updateConfig(ignored -> restoredConfig);
    }

    private static boolean booleanSnapshotValue(JsonObject root, String key, boolean defaultValue) {
        return root.has(key) ? root.get(key).getAsBoolean() : defaultValue;
    }

    private static int integerSnapshotValue(JsonObject root, String key, int defaultValue) {
        return root.has(key) ? root.get(key).getAsInt() : defaultValue;
    }

    private static JsonObject permissionsJson(Map<String, PermissionValue> permissions) {
        JsonObject object = new JsonObject();
        new TreeMap<>(permissions).forEach((node, value) -> object.addProperty(node, value.name()));
        return object;
    }

    private static String displaySnapshot(DisplayProfile display) {
        JsonObject root = new JsonObject();
        display.prefix().ifPresentOrElse(value -> root.addProperty("prefix", value.rawText()), () -> root.add("prefix", com.google.gson.JsonNull.INSTANCE));
        display.suffix().ifPresentOrElse(value -> root.addProperty("suffix", value.rawText()), () -> root.add("suffix", com.google.gson.JsonNull.INSTANCE));
        return GSON.toJson(root);
    }

    private static JsonArray stringArray(Collection<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    private static JsonArray orderedStringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonArray uuidArray(Collection<UUID> values) {
        JsonArray array = new JsonArray();
        values.stream().map(UUID::toString).sorted().forEach(array::add);
        return array;
    }

    private static Set<String> stringSet(JsonArray array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static List<String> stringList(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static Set<UUID> uuidSet(JsonArray array) {
        Set<UUID> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(UUID.fromString(value.getAsString())));
        return values;
    }

    private static String objectWith(String key, JsonElement value) {
        JsonObject root = new JsonObject();
        root.add(key, value);
        return GSON.toJson(root);
    }

    private static String canonicalJson(String json) {
        return GSON.toJson(JsonParser.parseString(json));
    }

    private static <S> void requireManuallyRegisteredNode(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String normalizedNode)
            throws CommandSyntaxException {
        if (environment.manualPermissionNodeRegistry().getKnownNode(normalizedNode).isPresent()) {
            return;
        }

        List<CommandMessage> messages = new ArrayList<>();
        Optional<KnownPermissionNode> knownNode = environment.permissionNodeRegistry().getKnownNode(normalizedNode);
        if (knownNode.isPresent()) {
            messages.add(CommandLang.nonManualNode(formatNodeSource(knownNode.get()), normalizedNode));
        } else {
            messages.add(CommandLang.unknownManualNode(normalizedNode));
        }
        messages.add(CommandLang.onlyManualNodesRemovable());

        List<KnownPermissionNode> manualNodes = environment.manualPermissionNodeRegistry().getKnownNodes().stream().sorted(Comparator.comparing(KnownPermissionNode::node))
                .toList();
        if (manualNodes.isEmpty()) {
            messages.add(CommandLang.noManualNodes());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "nodes add " + normalizedNode));
        } else {
            List<String> matches = closestMatches(normalizedNode, manualNodes.stream().map(node -> candidate(node.node())).toList());
            if (!matches.isEmpty()) {
                messages.add(CommandLang.closestManualNodes(String.join(", ", matches)));
            } else {
                messages.add(CommandLang.tryHeader());
                messages.add(CommandLang.suggestion(rootLiteral(context), "nodes list"));
            }
        }
        throw feedback(messages);
    }

    private static <S> CommandFeedbackException unknownUserTargetFeedback(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String target) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownUserTarget(target));
        messages.add(CommandLang.userTargetForms());

        List<String> onlineMatches = closestMatches(target, environment.onlineSubjectNames(context.getSource()).stream().map(ClutchPermsCommands::candidate).toList());
        if (!onlineMatches.isEmpty()) {
            messages.add(CommandLang.closestOnlineUsers(String.join(", ", onlineMatches)));
        }

        List<String> knownMatches = closestMatches(target,
                environment.subjectMetadataService().getSubjects().values().stream().map(subject -> candidate(subject.lastKnownName(), formatSubjectMetadata(subject))).toList());
        if (!knownMatches.isEmpty()) {
            messages.add(CommandLang.closestKnownUsers(String.join(", ", knownMatches)));
        }

        if (onlineMatches.isEmpty() && knownMatches.isEmpty()) {
            messages.add(CommandLang.noUserTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "users search " + target));
        }
        return feedback(messages);
    }

    private static CommandFeedbackException ambiguousKnownUserFeedback(String target, List<SubjectMetadata> matches) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.ambiguousKnownUser(target));
        messages.add(CommandLang.ambiguousKnownUserDetail(target));
        matches.stream().map(ClutchPermsCommands::formatSubjectMetadata).forEach(match -> messages.add(CommandLang.targetMatch(match)));
        return feedback(messages);
    }

    private static <S> CommandFeedbackException unknownGroupTargetFeedback(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String groupName,
            boolean parentGroup) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(parentGroup ? CommandLang.unknownParentGroupTarget(groupName) : CommandLang.unknownGroupTarget(groupName));

        Set<String> groups = environment.groupService().getGroups();
        if (groups.isEmpty()) {
            messages.add(CommandLang.noGroupsDefined());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "group " + groupName + " create"));
            return feedback(messages);
        }

        List<String> matches = closestMatches(groupName, groups.stream().map(ClutchPermsCommands::candidate).toList());
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestGroups(String.join(", ", matches)));
        } else {
            messages.add(CommandLang.noGroupTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "group list"));
        }
        return feedback(messages);
    }

    private static <S> CommandFeedbackException unknownTrackTargetFeedback(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String trackName) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownTrackTarget(trackName));

        Set<String> tracks = environment.trackService().getTracks();
        if (tracks.isEmpty()) {
            messages.add(CommandLang.noTracksDefined());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "track " + trackName + " create"));
            return feedback(messages);
        }

        List<String> matches = closestMatches(trackName, tracks.stream().map(ClutchPermsCommands::candidate).toList());
        if (!matches.isEmpty()) {
            messages.add(CommandLang.closestTracks(String.join(", ", matches)));
        } else {
            messages.add(CommandLang.noTrackTargetMatches());
            messages.add(CommandLang.tryHeader());
            messages.add(CommandLang.suggestion(rootLiteral(context), "track list"));
        }
        return feedback(messages);
    }

    private static <S> CommandFeedbackException unknownConfigKeyFeedback(CommandContext<S> context, String key) {
        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownConfigKey(key));
        List<String> matches = closestMatches(key, CONFIG_KEYS.stream().map(ClutchPermsCommands::candidate).toList());
        if (matches.isEmpty()) {
            messages.add(CommandLang.validConfigKeys(String.join(", ", CONFIG_KEYS)));
        } else {
            messages.add(CommandLang.closestConfigKeys(String.join(", ", matches)));
        }
        messages.add(CommandLang.tryHeader());
        messages.add(CommandLang.suggestion(rootLiteral(context), "config get <key>"));
        messages.add(CommandLang.suggestion(rootLiteral(context), "config list"));
        return feedback(messages);
    }

    private static <S> CommandFeedbackException invalidConfigValueFeedback(CommandContext<S> context, ConfigEntry entry, String rawValue) {
        return feedback(List.of(CommandLang.invalidConfigValue(entry.key(), rawValue), CommandLang.configValueRequirement(entry.key(), entry.errorHint()), CommandLang.tryHeader(),
                CommandLang.suggestion(rootLiteral(context), "config set " + entry.key() + " <value>")));
    }

    private static CommandFeedbackException feedback(List<CommandMessage> messages) {
        return new CommandFeedbackException(messages);
    }

    private static TargetCandidate candidate(String text) {
        return candidate(text, text);
    }

    private static TargetCandidate candidate(String matchText, String displayText) {
        return new TargetCandidate(matchText, displayText);
    }

    private static List<String> closestMatches(String target, Collection<TargetCandidate> candidates) {
        String query = target.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }

        return candidates
                .stream().map(candidate -> matchCandidate(query, candidate)).flatMap(Optional::stream).sorted(Comparator.comparingInt(TargetMatch::category)
                        .thenComparingInt(TargetMatch::score).thenComparing(TargetMatch::sortText).thenComparing(TargetMatch::displayText))
                .limit(TARGET_MATCH_LIMIT).map(TargetMatch::displayText).toList();
    }

    private static Optional<TargetMatch> matchCandidate(String query, TargetCandidate candidate) {
        String candidateText = candidate.matchText().trim().toLowerCase(Locale.ROOT);
        if (candidateText.isEmpty()) {
            return Optional.empty();
        }

        if (candidateText.startsWith(query)) {
            return Optional.of(new TargetMatch(candidate.displayText(), 0, candidateText.length() - query.length(), candidateText));
        }

        int substringIndex = candidateText.indexOf(query);
        if (substringIndex >= 0) {
            return Optional.of(new TargetMatch(candidate.displayText(), 1, substringIndex, candidateText));
        }

        int reverseSubstringIndex = query.indexOf(candidateText);
        if (reverseSubstringIndex >= 0) {
            return Optional.of(new TargetMatch(candidate.displayText(), 1, 100 + reverseSubstringIndex, candidateText));
        }

        int maximumDistance = maximumEditDistance(query);
        int distance = editDistance(query, candidateText, maximumDistance);
        if (distance <= maximumDistance) {
            return Optional.of(new TargetMatch(candidate.displayText(), 2, distance, candidateText));
        }

        return Optional.empty();
    }

    private static int maximumEditDistance(String query) {
        if (query.length() <= 3) {
            return 1;
        }
        if (query.length() <= 8) {
            return 2;
        }
        return 3;
    }

    private static int editDistance(String first, String second, int cutoff) {
        if (Math.abs(first.length() - second.length()) > cutoff) {
            return cutoff + 1;
        }

        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            int bestInRow = current[0];
            for (int column = 1; column <= second.length(); column++) {
                int substitutionCost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                int insertion = current[column - 1] + 1;
                int deletion = previous[column] + 1;
                int substitution = previous[column - 1] + substitutionCost;
                current[column] = Math.min(Math.min(insertion, deletion), substitution);
                bestInRow = Math.min(bestInRow, current[column]);
            }
            if (bestInRow > cutoff) {
                return cutoff + 1;
            }

            int[] nextPrevious = previous;
            previous = current;
            current = nextPrevious;
        }

        return previous[second.length()];
    }

    private static <S> CompletableFuture<Suggestions> suggestPermissionAssignment(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int valueStart = lastWhitespaceRunStart(remaining);
        if (valueStart <= 0) {
            return suggestPermissionNodes(environment, context, builder);
        }

        SuggestionsBuilder valueBuilder = builder.createOffset(builder.getStart() + valueStart);
        String partialValue = remaining.substring(valueStart).trim().toLowerCase(Locale.ROOT);
        if ("true".startsWith(partialValue)) {
            valueBuilder.suggest("true");
        }
        if ("false".startsWith(partialValue)) {
            valueBuilder.suggest("false");
        }
        return valueBuilder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestPermissionNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.addAll(PermissionNodes.commandWildcardAssignments());
        environment.permissionNodeRegistry().getKnownNodes().stream().map(KnownPermissionNode::node).forEach(nodes::add);

        try {
            String groupName = StringArgumentType.getString(context, GROUP_ARGUMENT);
            if (environment.groupService().hasGroup(groupName)) {
                nodes.addAll(environment.groupService().getGroupPermissions(groupName).keySet());
            }
        } catch (IllegalArgumentException exception) {
            // The current command may not have a group argument, so group-specific suggestions are best-effort.
        }

        try {
            CommandSubject subject = resolveSubject(environment, context);
            nodes.addAll(environment.permissionResolver().getEffectivePermissions(subject.id()).keySet());
        } catch (CommandSyntaxException | IllegalArgumentException exception) {
            // Target-specific node suggestions are best-effort; keep built-in nodes available when the target is incomplete or invalid.
        }

        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        nodes.stream().sorted(Comparator.naturalOrder()).filter(node -> node.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static String formatSubject(CommandSubject subject) {
        return subject.displayName() + " (" + subject.id() + ")";
    }

    private static <S> String formatSubject(UUID subjectId, ClutchPermsCommandEnvironment<S> environment) {
        String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
        return displayName + " (" + subjectId + ")";
    }

    private static String formatSubjectMetadata(SubjectMetadata subject) {
        return subject.lastKnownName() + " (" + subject.subjectId() + ", last seen " + subject.lastSeen() + ")";
    }

    private static <S> String summarizeSubjectGroups(ClutchPermsCommandEnvironment<S> environment, UUID subjectId) {
        List<String> groups = new ArrayList<>(environment.groupService().getSubjectGroups(subjectId));
        if (environment.groupService().hasGroup(GroupService.DEFAULT_GROUP)) {
            groups.add(GroupService.DEFAULT_GROUP + " (implicit)");
        }
        return summarizeValues(groups);
    }

    private static <S> String summarizeGroupMembers(ClutchPermsCommandEnvironment<S> environment, Set<UUID> members) {
        return summarizeValues(members.stream().map(subjectId -> formatSubject(subjectId, environment)).toList());
    }

    private static <S> List<String> findChildGroups(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        return environment.groupService().getGroups().stream().filter(group -> !group.equals(groupName))
                .filter(group -> environment.groupService().getGroupParents(group).contains(groupName)).toList();
    }

    private static <S> TrackSubjectState subjectTrackState(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, String trackName) {
        List<String> trackGroups = environment.trackService().getTrackGroups(trackName);
        List<String> explicitMatches = trackGroups.stream().filter(environment.groupService().getSubjectGroups(subjectId)::contains).toList();
        boolean implicitDefault = explicitMatches.isEmpty() && !trackGroups.isEmpty() && GroupService.DEFAULT_GROUP.equals(trackGroups.getFirst());
        return new TrackSubjectState(trackName, trackGroups, explicitMatches, implicitDefault);
    }

    private static String formatTrackSubjectState(TrackSubjectState state) {
        if (state.hasConflict()) {
            return "conflict (" + summarizeValues(state.explicitMatches()) + ")";
        }
        if (state.implicitDefault()) {
            return GroupService.DEFAULT_GROUP + " (implicit)";
        }
        if (state.hasExplicitMatch()) {
            return state.currentGroup() + " (#" + (state.currentIndex() + 1) + ")";
        }
        return "unmatched";
    }

    private static <S> String summarizeSubjectTracks(ClutchPermsCommandEnvironment<S> environment, UUID subjectId) {
        List<String> tracks = environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            TrackSubjectState state = subjectTrackState(environment, subjectId, trackName);
            if (!state.hasPosition() && !state.hasConflict()) {
                return null;
            }
            return trackName + "=" + formatTrackSubjectState(state);
        }).filter(Objects::nonNull).toList();
        return summarizeValues(tracks);
    }

    private static <S> List<GroupTrackReference> findGroupTrackReferences(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        return environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            List<String> groups = environment.trackService().getTrackGroups(trackName);
            int index = groups.indexOf(groupName);
            if (index < 0) {
                return null;
            }
            return new GroupTrackReference(trackName, index + 1);
        }).filter(Objects::nonNull).toList();
    }

    private static <S> JsonArray groupTrackReferencesJson(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        JsonArray array = new JsonArray();
        findGroupTrackReferences(environment, groupName).forEach(reference -> {
            JsonObject root = new JsonObject();
            root.addProperty("track", reference.trackName());
            root.addProperty("position", reference.position());
            array.add(root);
        });
        return array;
    }

    private static Map<String, Integer> trackReferencePositions(JsonArray array) {
        Map<String, Integer> references = new LinkedHashMap<>();
        array.forEach(value -> {
            JsonObject root = value.getAsJsonObject();
            references.put(root.get("track").getAsString(), root.get("position").getAsInt());
        });
        return references;
    }

    private static <S> void applyGroupTracks(ClutchPermsCommandEnvironment<S> environment, String groupName, Map<String, Integer> desiredTrackPositions) {
        Map<String, Integer> currentTrackPositions = new LinkedHashMap<>();
        findGroupTrackReferences(environment, groupName).forEach(reference -> currentTrackPositions.put(reference.trackName(), reference.position()));

        currentTrackPositions.keySet().stream().filter(trackName -> !desiredTrackPositions.containsKey(trackName)).toList().forEach(trackName -> {
            List<String> groups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
            groups.remove(groupName);
            environment.trackService().setTrackGroups(trackName, groups);
        });

        desiredTrackPositions.forEach((trackName, position) -> {
            if (!environment.trackService().hasTrack(trackName)) {
                throw new IllegalArgumentException("missing track for group snapshot: " + trackName);
            }
            List<String> groups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
            groups.remove(groupName);
            if (position < 1 || position > groups.size() + 1) {
                throw new IllegalArgumentException("track position out of range for group snapshot: " + trackName + "#" + position);
            }
            groups.add(position - 1, groupName);
            environment.trackService().setTrackGroups(trackName, groups);
        });
    }

    private static <S> String summarizeGroupTracks(ClutchPermsCommandEnvironment<S> environment, String groupName) {
        return summarizeValues(findGroupTrackReferences(environment, groupName).stream().map(reference -> reference.trackName() + "#" + reference.position()).toList());
    }

    private static String summarizeValues(Collection<String> values) {
        List<String> sortedValues = values.stream().sorted(Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()))
                .toList();
        if (sortedValues.isEmpty()) {
            return "none";
        }

        List<String> shownValues = sortedValues.stream().limit(SUMMARY_VALUE_LIMIT).toList();
        String summary = String.join(", ", shownValues);
        int remaining = sortedValues.size() - shownValues.size();
        if (remaining > 0) {
            summary += ", +" + remaining + " more";
        }
        return summary;
    }

    private record TrackSubjectState(String trackName, List<String> trackGroups, List<String> explicitMatches, boolean implicitDefault) {

        private boolean hasConflict() {
            return explicitMatches.size() > 1;
        }

        private boolean hasExplicitMatch() {
            return explicitMatches.size() == 1;
        }

        private boolean hasPosition() {
            return hasExplicitMatch() || implicitDefault;
        }

        private int currentIndex() {
            if (hasExplicitMatch()) {
                return trackGroups.indexOf(explicitMatches.getFirst());
            }
            return implicitDefault ? 0 : -1;
        }

        private String currentGroup() {
            if (hasExplicitMatch()) {
                return explicitMatches.getFirst();
            }
            return implicitDefault ? GroupService.DEFAULT_GROUP : null;
        }
    }

    private record GroupTrackReference(String trackName, int position) {
    }

    private static String formatDisplayValue(Optional<DisplayText> value) {
        return value.map(DisplayText::rawText).orElse("unset");
    }

    private static String formatEffectiveDisplay(DisplayResolution resolution) {
        return resolution.value().map(value -> value.rawText() + " from " + formatDisplaySource(resolution)).orElse("unset");
    }

    private static String formatKnownNode(KnownPermissionNode node) {
        String formattedNode = node.node() + " [" + node.source().name().toLowerCase(Locale.ROOT).replace('_', '-') + "]";
        if (!node.description().isEmpty()) {
            formattedNode += " - " + node.description();
        }
        return formattedNode;
    }

    private static <S> void addSubjectDisplayRows(ClutchPermsCommandEnvironment<S> environment, List<PagedRow> rows, String rootLiteral, CommandSubject subject) {
        addSubjectDisplayRow(environment, rows, rootLiteral, subject, DisplaySlot.PREFIX);
        addSubjectDisplayRow(environment, rows, rootLiteral, subject, DisplaySlot.SUFFIX);
    }

    private static <S> void addSubjectDisplayRow(ClutchPermsCommandEnvironment<S> environment, List<PagedRow> rows, String rootLiteral, CommandSubject subject, DisplaySlot slot) {
        Optional<DisplayText> directValue = subjectDisplayValue(environment, subject.id(), slot);
        if (directValue.isPresent()) {
            rows.add(new PagedRow("direct " + slot.label() + " " + directValue.get().rawText(), fullCommand(rootLiteral, "user " + subject.id() + " " + slot.label() + " get")));
        }

        DisplayResolution resolution = environment.displayResolver().resolve(subject.id(), slot);
        if (resolution.value().isPresent()) {
            String source = formatDisplaySource(resolution);
            rows.add(new PagedRow("effective " + slot.label() + " " + resolution.value().get().rawText() + " from " + source,
                    fullCommand(rootLiteral, "user " + subject.id() + " " + slot.label() + " get")));
        }
    }

    private static <S> void addGroupDisplayRows(ClutchPermsCommandEnvironment<S> environment, List<PagedRow> rows, String rootLiteral, String groupName) {
        addGroupDisplayRow(environment, rows, rootLiteral, groupName, DisplaySlot.PREFIX);
        addGroupDisplayRow(environment, rows, rootLiteral, groupName, DisplaySlot.SUFFIX);
    }

    private static <S> void addGroupDisplayRow(ClutchPermsCommandEnvironment<S> environment, List<PagedRow> rows, String rootLiteral, String groupName, DisplaySlot slot) {
        groupDisplayValue(environment, groupName, slot)
                .ifPresent(value -> rows.add(new PagedRow(slot.label() + " " + value.rawText(), fullCommand(rootLiteral, "group " + groupName + " " + slot.label() + " get"))));
    }

    private static <S> Optional<DisplayText> subjectDisplayValue(ClutchPermsCommandEnvironment<S> environment, UUID subjectId, DisplaySlot slot) {
        return switch (slot) {
            case PREFIX -> environment.subjectMetadataService().getSubjectDisplay(subjectId).prefix();
            case SUFFIX -> environment.subjectMetadataService().getSubjectDisplay(subjectId).suffix();
        };
    }

    private static <S> Optional<DisplayText> groupDisplayValue(ClutchPermsCommandEnvironment<S> environment, String groupName, DisplaySlot slot) {
        return switch (slot) {
            case PREFIX -> environment.groupService().getGroupDisplay(groupName).prefix();
            case SUFFIX -> environment.groupService().getGroupDisplay(groupName).suffix();
        };
    }

    private static PagedRow backupRow(String rootLiteral, StorageBackup backup, String text) {
        return new PagedRow(text, fullCommand(rootLiteral, "backup restore " + backup.fileName()));
    }

    private static String knownNodeCommand(String rootLiteral, KnownPermissionNode node) {
        if (node.source() == PermissionNodeSource.MANUAL) {
            return fullCommand(rootLiteral, "nodes remove " + node.node());
        }
        return fullCommand(rootLiteral, "nodes search " + node.node());
    }

    private static String fullCommand(String rootLiteral, String command) {
        return "/" + rootLiteral + (command.isBlank() ? "" : " " + command);
    }

    private static String formatNodeSource(KnownPermissionNode node) {
        return node.source().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String formatResolutionSource(PermissionResolution resolution) {
        return switch (resolution.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + resolution.groupName();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(resolution.groupName()) ? "default group" : "default group parent " + resolution.groupName();
            case UNSET -> "unset";
        };
    }

    private static String formatDisplaySource(DisplayResolution resolution) {
        return switch (resolution.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + resolution.groupName();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(resolution.groupName()) ? "default group" : "default group parent " + resolution.groupName();
            case UNSET -> "unset";
        };
    }

    private static String formatExplanationSource(PermissionExplanation.Match match) {
        return switch (match.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + match.groupName() + " depth " + match.depth();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(match.groupName())
                    ? "default group depth " + match.depth()
                    : "default group parent " + match.groupName() + " depth " + match.depth();
            case UNSET -> "unset";
        };
    }

    private static String normalizeGroupName(String groupName) {
        return groupName.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTrackName(String trackName) {
        return trackName.trim().toLowerCase(Locale.ROOT);
    }

    private ClutchPermsCommands() {
    }

    @FunctionalInterface
    private interface CommandAction<S> {

        int run(S source) throws CommandSyntaxException;
    }

    private static final class CommandFeedbackException extends CommandSyntaxException {

        private static final long serialVersionUID = 1L;

        private final List<CommandMessage> messages;

        private CommandFeedbackException(List<CommandMessage> messages) {
            super(FEEDBACK_MESSAGES, new LiteralMessage(messages.isEmpty() ? "" : messages.getFirst().plainText()));
            this.messages = List.copyOf(messages);
        }

        private List<CommandMessage> messages() {
            return messages;
        }
    }

    private record TargetCandidate(String matchText, String displayText) {
    }

    private record ConfirmationSource(Object key) {
    }

    private record PendingConfirmation(String operationKey, Instant expiresAt) {
    }

    private record TargetMatch(String displayText, int category, int score, String sortText) {
    }

    private record PermissionAssignment(String node, PermissionValue value) {
    }

    private record CommandHelpEntry(String syntax, String permission, String description) {
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
                config -> Boolean.toString(valueGetter.apply(config)), ClutchPermsCommands::parseBooleanConfigValue,
                (config, value) -> valueSetter.apply(config, Boolean.parseBoolean(value)));
    }

    private static String parseBooleanConfigValue(String rawValue) {
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "enabled" -> "true";
            case "false", "off", "no", "disabled" -> "false";
            default -> throw new IllegalArgumentException("config value must be a boolean");
        };
    }

    private record ConfigEntry(String key, String description, String displayHint, String inputHint, String errorHint, String defaultValue,
            Function<ClutchPermsConfig, String> valueGetter, ConfigValueParser valueParser, ConfigValueSetter valueSetter) {

        private String value(ClutchPermsConfig config) {
            return valueGetter.apply(config);
        }

        private String normalizeValue(String rawValue) {
            return valueParser.parse(rawValue.trim());
        }

        private ClutchPermsConfig withValue(ClutchPermsConfig config, String value) {
            return valueSetter.apply(config, value);
        }
    }

    @FunctionalInterface
    private interface ConfigValueParser {

        String parse(String rawValue);
    }

    @FunctionalInterface
    private interface ConfigValueSetter {

        ClutchPermsConfig apply(ClutchPermsConfig config, String value);
    }

    @FunctionalInterface
    private interface BooleanConfigValueSetter {

        ClutchPermsConfig apply(ClutchPermsConfig config, boolean value);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {

        T get() throws CommandSyntaxException;
    }

    private record ResolvedSubject(CommandSubject subject, boolean recordMetadata) {
    }

    private record PagedRow(String text, String command) {
    }
}
