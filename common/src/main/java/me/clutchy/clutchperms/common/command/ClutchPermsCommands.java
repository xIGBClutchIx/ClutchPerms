package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
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

import me.clutchy.clutchperms.common.command.subcommand.AuthorizedCommand;
import me.clutchy.clutchperms.common.command.subcommand.BackupSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.ConfigSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.GroupSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.NodesSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.UserSubcommand;
import me.clutchy.clutchperms.common.command.subcommand.UsersSubcommand;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
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

    private static final String PARENT_ARGUMENT = CommandArguments.PARENT;

    private static final String BACKUP_KIND_ARGUMENT = CommandArguments.BACKUP_KIND;

    private static final String BACKUP_FILE_ARGUMENT = CommandArguments.BACKUP_FILE;

    private static final String CONFIG_KEY_ARGUMENT = CommandArguments.CONFIG_KEY;

    private static final String CONFIG_VALUE_ARGUMENT = CommandArguments.CONFIG_VALUE;

    private static final String DISPLAY_VALUE_ARGUMENT = CommandArguments.DISPLAY_VALUE;

    private static final String PAGE_ARGUMENT = CommandArguments.PAGE;

    private static final String UNKNOWN_ARGUMENT = CommandArguments.UNKNOWN;

    private static final int TARGET_MATCH_LIMIT = 5;

    private static final SimpleCommandExceptionType FEEDBACK_MESSAGES = new SimpleCommandExceptionType(new LiteralMessage("command feedback"));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage(CommandLang.invalidNode(node).plainText()));

    private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(value -> new LiteralMessage(CommandLang.invalidValue(value).plainText()));

    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType VALIDATE_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType PERMISSION_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType GROUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType NODE_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType BACKUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType CONFIG_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType DISPLAY_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final List<ConfigEntry> CONFIG_ENTRIES = List.of(
            integerConfig("backups.retentionLimit", "newest backups kept per storage kind", ClutchPermsBackupConfig.MIN_RETENTION_LIMIT,
                    ClutchPermsBackupConfig.MAX_RETENTION_LIMIT, ClutchPermsBackupConfig.DEFAULT_RETENTION_LIMIT, config -> config.backups().retentionLimit(),
                    (config, value) -> new ClutchPermsConfig(new ClutchPermsBackupConfig(value), config.commands(), config.chat())),
            integerConfig("commands.helpPageSize", "command rows shown per help page", ClutchPermsCommandConfig.MIN_PAGE_SIZE, ClutchPermsCommandConfig.MAX_PAGE_SIZE,
                    ClutchPermsCommandConfig.DEFAULT_HELP_PAGE_SIZE, config -> config.commands().helpPageSize(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), new ClutchPermsCommandConfig(value, config.commands().resultPageSize()), config.chat())),
            integerConfig("commands.resultPageSize", "rows shown per list-result page", ClutchPermsCommandConfig.MIN_PAGE_SIZE, ClutchPermsCommandConfig.MAX_PAGE_SIZE,
                    ClutchPermsCommandConfig.DEFAULT_RESULT_PAGE_SIZE, config -> config.commands().resultPageSize(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), new ClutchPermsCommandConfig(config.commands().helpPageSize(), value), config.chat())),
            booleanConfig("chat.enabled", "prefix and suffix chat formatting", ClutchPermsChatConfig.DEFAULT_ENABLED, config -> config.chat().enabled(),
                    (config, value) -> new ClutchPermsConfig(config.backups(), config.commands(), new ClutchPermsChatConfig(value))));

    private static final List<String> CONFIG_KEYS = CONFIG_ENTRIES.stream().map(ConfigEntry::key).toList();

    private static final List<CommandHelpEntry> COMMAND_HELP = List.of(new CommandHelpEntry("help [page]", PermissionNodes.ADMIN_HELP, "Shows paged command help."),
            new CommandHelpEntry("status", PermissionNodes.ADMIN_STATUS, "Shows storage, counts, resolver cache, and bridge status."),
            new CommandHelpEntry("reload", PermissionNodes.ADMIN_RELOAD, "Reloads config and JSON storage, then refreshes runtime permissions."),
            new CommandHelpEntry("validate", PermissionNodes.ADMIN_VALIDATE, "Checks config and JSON storage without applying it."),
            new CommandHelpEntry("config list", PermissionNodes.ADMIN_CONFIG_VIEW, "Lists active runtime config values."),
            new CommandHelpEntry("config get <key>", PermissionNodes.ADMIN_CONFIG_VIEW, "Shows one config value."),
            new CommandHelpEntry("config set <key> <value>", PermissionNodes.ADMIN_CONFIG_SET, "Updates config and reloads runtime."),
            new CommandHelpEntry("config reset <key|all>", PermissionNodes.ADMIN_CONFIG_RESET, "Restores config defaults."),
            new CommandHelpEntry("backup list [kind] [page]", PermissionNodes.ADMIN_BACKUP_LIST, "Lists backups by file kind."),
            new CommandHelpEntry("backup list page <page>", PermissionNodes.ADMIN_BACKUP_LIST, "Lists all backups on a specific page."),
            new CommandHelpEntry("backup restore <kind> <backup-file>", PermissionNodes.ADMIN_BACKUP_RESTORE, "Restores one validated backup file."),
            new CommandHelpEntry("user <target> list [page]", PermissionNodes.ADMIN_USER_LIST, "Lists direct user permissions."),
            new CommandHelpEntry("user <target> get <node>", PermissionNodes.ADMIN_USER_GET, "Shows one direct user permission."),
            new CommandHelpEntry("user <target> set <node> <true|false>", PermissionNodes.ADMIN_USER_SET, "Sets one direct user permission."),
            new CommandHelpEntry("user <target> clear <node>", PermissionNodes.ADMIN_USER_CLEAR, "Clears one direct user permission."),
            new CommandHelpEntry("user <target> check <node>", PermissionNodes.ADMIN_USER_CHECK, "Shows the effective permission result."),
            new CommandHelpEntry("user <target> explain <node>", PermissionNodes.ADMIN_USER_EXPLAIN, "Explains matching assignments and the winner."),
            new CommandHelpEntry("user <target> groups [page]", PermissionNodes.ADMIN_USER_GROUPS, "Lists explicit and implicit groups for a user."),
            new CommandHelpEntry("user <target> group <add|remove> <group>", PermissionNodes.ADMIN_USER_GROUPS, "Changes explicit group membership."),
            new CommandHelpEntry("user <target> <prefix|suffix> get", PermissionNodes.ADMIN_USER_DISPLAY_VIEW, "Shows direct and effective user display values."),
            new CommandHelpEntry("user <target> <prefix|suffix> set <text>", PermissionNodes.ADMIN_USER_DISPLAY_SET, "Sets direct user chat display text."),
            new CommandHelpEntry("user <target> <prefix|suffix> clear", PermissionNodes.ADMIN_USER_DISPLAY_CLEAR, "Clears direct user chat display text."),
            new CommandHelpEntry("group list [page]", PermissionNodes.ADMIN_GROUP_LIST, "Lists groups."),
            new CommandHelpEntry("group <group> <create|delete>", PermissionNodes.ADMIN_GROUP_VIEW, "Creates or deletes a group."),
            new CommandHelpEntry("group <group> list [page]", PermissionNodes.ADMIN_GROUP_VIEW, "Lists group permissions, parents, and members."),
            new CommandHelpEntry("group <group> parents [page]", PermissionNodes.ADMIN_GROUP_PARENTS, "Lists parent groups."),
            new CommandHelpEntry("group <group> <get|clear> <node>", PermissionNodes.ADMIN_GROUP_GET, "Reads or clears one group permission."),
            new CommandHelpEntry("group <group> set <node> <true|false>", PermissionNodes.ADMIN_GROUP_SET, "Sets one group permission."),
            new CommandHelpEntry("group <group> parent <add|remove> <parent>", PermissionNodes.ADMIN_GROUP_PARENTS, "Changes group inheritance."),
            new CommandHelpEntry("group <group> <prefix|suffix> get", PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, "Shows group chat display values."),
            new CommandHelpEntry("group <group> <prefix|suffix> set <text>", PermissionNodes.ADMIN_GROUP_DISPLAY_SET, "Sets group chat display text."),
            new CommandHelpEntry("group <group> <prefix|suffix> clear", PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR, "Clears group chat display text."),
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
        AuthorizedCommand<S> authorized = (context, requiredPermission, command) -> executeAuthorized(environment, context, requiredPermission, source -> command.run(context));

        return LiteralArgumentBuilder.<S>literal(literal)
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context)))
                .then(helpCommand(environment)).then(statusCommand(environment)).then(reloadCommand(environment)).then(validateCommand(environment))
                .then(ConfigSubcommand.builder(authorized, configHandlers(environment), CONFIG_KEYS))
                .then(BackupSubcommand.builder(environment, authorized, backupHandlers(environment)))
                .then(UserSubcommand.builder(environment, authorized, userHandlers(environment), (context, builder) -> suggestPermissionNodes(environment, context, builder),
                        (context, builder) -> suggestPermissionAssignment(environment, context, builder)))
                .then(GroupSubcommand.builder(environment, authorized, groupHandlers(environment), (context, builder) -> suggestPermissionNodes(environment, context, builder),
                        (context, builder) -> suggestPermissionAssignment(environment, context, builder)))
                .then(UsersSubcommand.builder(authorized, usersHandlers(environment))).then(NodesSubcommand.builder(environment, authorized, nodesHandlers(environment)));
    }

    private static String normalizeRootLiteral(String rootLiteral) {
        String literal = Objects.requireNonNull(rootLiteral, "rootLiteral").trim();
        if (literal.isEmpty()) {
            throw new IllegalArgumentException("root literal must not be blank");
        }
        return literal;
    }

    private static <S> LiteralArgumentBuilder<S> statusCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("status")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_STATUS, source -> sendStatus(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> helpCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("help")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context)))
                .then(RequiredArgumentBuilder.<S, String>argument(PAGE_ARGUMENT, StringArgumentType.word())
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> reloadCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("reload")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_RELOAD, source -> reloadStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> validateCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("validate")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_VALIDATE, source -> validateStorage(environment, context)));
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
            public int listPageUsage(CommandContext<S> context) {
                return sendUsage(environment, context, "Missing page number.", "Choose a backup list page.", List.of("backup list page <page>"));
            }

            @Override
            public int restoreKindUsage(CommandContext<S> context) {
                return sendBackupRestoreKindUsage(environment, context);
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
            public int groups(CommandContext<S> context) throws CommandSyntaxException {
                return listSubjectGroups(environment, context);
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
            public int show(CommandContext<S> context) throws CommandSyntaxException {
                return listGroup(environment, context);
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
        environment.sendMessage(context.getSource(), CommandLang.statusPermissionsFile(diagnostics.permissionsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusSubjectsFile(diagnostics.subjectsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusGroupsFile(diagnostics.groupsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusNodesFile(diagnostics.nodesFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusConfigFile(diagnostics.configFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusBackupRetention(environment.config().backups().retentionLimit()));
        environment.sendMessage(context.getSource(),
                CommandLang.statusCommandPageSizes(environment.config().commands().helpPageSize(), environment.config().commands().resultPageSize()));
        environment.sendMessage(context.getSource(), CommandLang.statusChatFormatting(environment.config().chat().enabled()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownSubjects(environment.subjectMetadataService().getSubjects().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownGroups(environment.groupService().getGroups().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownNodes(environment.permissionNodeRegistry().getKnownNodes().size()));
        PermissionResolverCacheStats cacheStats = environment.permissionResolver().cacheStats();
        environment.sendMessage(context.getSource(), CommandLang.statusResolverCache(cacheStats.subjects(), cacheStats.nodeResults(), cacheStats.effectiveSnapshots()));
        environment.sendMessage(context.getSource(), CommandLang.statusRuntimeBridge(diagnostics.runtimeBridgeStatus()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int sendBackupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing backup command.", "Backups can be listed by storage kind or restored by file name.", backupUsages());
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

    private static <S> int sendBackupRestoreKindUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing backup file kind.", "Choose permissions, subjects, groups, or nodes.",
                List.of("backup restore <permissions|subjects|groups|nodes> <backup-file>"));
    }

    private static <S> int sendBackupRestoreFileUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageFileKind kind = getBackupKind(environment, context);
        return sendUsage(environment, context, "Missing backup file.", "Pick a backup file for " + kind.token() + ".",
                List.of("backup restore " + kind.token() + " <backup-file>"));
    }

    private static <S> int sendUnknownBackupUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Unknown backup command: " + StringArgumentType.getString(context, UNKNOWN_ARGUMENT), "Backup supports list and restore commands.",
                backupUsages());
    }

    private static <S> int sendUserRootUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing user target.", "Provide an online name, stored last-known name, or UUID.", userRootUsages());
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
        return List.of("backup list [permissions|subjects|groups|nodes]", "backup restore <permissions|subjects|groups|nodes> <backup-file>");
    }

    private static List<String> configUsages() {
        return List.of("config list", "config get <key>", "config set <key> <value>", "config reset <key|all>");
    }

    private static List<String> userRootUsages() {
        return List.of("user <target> <list|groups>", "user <target> <get|clear|check|explain> <node>", "user <target> set <node> <true|false>",
                "user <target> group <add|remove> <group>", "user <target> <prefix|suffix> get|set|clear");
    }

    private static List<String> userTargetUsages(String target) {
        return List.of("user " + target + " <list|groups>", "user " + target + " <get|clear|check|explain> <node>", "user " + target + " set <node> <true|false>",
                "user " + target + " group <add|remove> <group>", "user " + target + " <prefix|suffix> get|set|clear");
    }

    private static List<String> groupRootUsages() {
        return List.of("group list", "group <group> <create|delete|list|parents>", "group <group> <get|clear> <node>", "group <group> set <node> <true|false>",
                "group <group> parent <add|remove> <parent>", "group <group> <prefix|suffix> get|set|clear");
    }

    private static List<String> groupTargetUsages(String group) {
        return List.of("group " + group + " <create|delete|list|parents>", "group " + group + " <get|clear> <node>", "group " + group + " set <node> <true|false>",
                "group " + group + " parent <add|remove> <parent>", "group " + group + " <prefix|suffix> get|set|clear");
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
        String oldValue = entry.value(environment.config());
        if (oldValue.equals(newValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }

        try {
            environment.updateConfig(config -> entry.withValue(config, newValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }

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

        try {
            environment.updateConfig(config -> entry.withValue(config, defaultValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.configReset(entry.key(), oldValue, defaultValue));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int resetAllConfig(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        if (environment.config().equals(ClutchPermsConfig.defaults())) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadyDefaults());
            return Command.SINGLE_SUCCESS;
        }

        try {
            environment.updateConfig(config -> ClutchPermsConfig.defaults());
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw CONFIG_OPERATION_FAILED.create(CommandLang.configOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.configResetAll());
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listBackups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageBackupService backupService = environment.storageBackupService();
        Optional<StorageFileKind> requestedKind = getOptionalBackupKind(environment, context);
        try {
            if (requestedKind.isPresent()) {
                sendBackupList(environment, context, requestedKind.get(), backupService.listBackups(requestedKind.get()), "backup list " + requestedKind.get().token());
                return Command.SINGLE_SUCCESS;
            }

            Map<StorageFileKind, List<StorageBackup>> backups = backupService.listBackups();
            List<PagedRow> rows = backups.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(StorageFileKind::token))).flatMap(
                    entry -> entry.getValue().stream().map(backup -> backupRow(rootLiteral(context), entry.getKey(), backup, entry.getKey().token() + ": " + backup.fileName())))
                    .toList();
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

    private static <S> int restoreBackup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageFileKind kind = getBackupKind(environment, context);
        String backupFileName = StringArgumentType.getString(context, BACKUP_FILE_ARGUMENT);
        StorageBackupService backupService = environment.storageBackupService();
        List<StorageBackup> backups;
        try {
            backups = backupService.listBackups(kind);
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
        StorageBackup backup = requireKnownBackupFile(context, kind, backupFileName, backups);
        validateBackup(environment, kind, backup);
        try {
            backupService.restoreBackup(kind, backupFileName, () -> {
                environment.reloadStorage();
                environment.refreshRuntimePermissions();
            });
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.backupRestored(kind.token(), backupFileName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> void sendBackupList(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, StorageFileKind kind, List<StorageBackup> backups,
            String pageCommand) throws CommandSyntaxException {
        if (backups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.backupsEmpty(kind.token()));
            return;
        }

        String rootLiteral = rootLiteral(context);
        List<PagedRow> rows = backups.stream().map(backup -> backupRow(rootLiteral, kind, backup, backup.fileName())).toList();
        sendPagedRows(environment, context, "Backups for " + kind.token(), rows, pageCommand);
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

        try {
            environment.permissionService().setPermission(subject.id(), assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(exception));
        }
        environment.sendMessage(context.getSource(), CommandLang.permissionSet(assignment.node(), formatSubject(subject), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);

        try {
            environment.permissionService().clearPermission(subject.id(), node);
        } catch (RuntimeException exception) {
            throw PERMISSION_OPERATION_FAILED.create(CommandLang.permissionOperationFailed(exception));
        }
        environment.sendMessage(context.getSource(), CommandLang.permissionClear(node, formatSubject(subject)));
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
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().setSubjectPrefix(subject.id(), displayText);
            } else {
                environment.subjectMetadataService().setSubjectSuffix(subject.id(), displayText);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        environment.sendMessage(context.getSource(), CommandLang.userDisplaySet(slot.label(), formatSubject(subject), displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearUserDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.subjectMetadataService().clearSubjectPrefix(subject.id());
            } else {
                environment.subjectMetadataService().clearSubjectSuffix(subject.id());
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
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

    private static <S> int addSubjectGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        try {
            environment.groupService().addSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.userGroupAdded(formatSubject(subject), normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int removeSubjectGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        try {
            environment.groupService().removeSubjectGroup(subject.id(), groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.userGroupRemoved(formatSubject(subject), normalizeGroupName(groupName)));
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
        try {
            environment.groupService().createGroup(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupCreated(normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int deleteGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        try {
            environment.groupService().deleteGroup(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupDeleted(normalizeGroupName(groupName)));
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
        try {
            environment.groupService().addGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupParentAdded(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int removeGroupParent(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String parentGroupName = requireExistingParentGroup(environment, context, getParentGroupName(context));
        try {
            environment.groupService().removeGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

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
        try {
            environment.groupService().setGroupPermission(groupName, assignment.node(), assignment.value());
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupPermissionSet(assignment.node(), normalizeGroupName(groupName), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearGroupPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        String node = getNode(context);
        try {
            environment.groupService().clearGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupPermissionClear(node, normalizeGroupName(groupName)));
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
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().setGroupPrefix(groupName, displayText);
            } else {
                environment.groupService().setGroupSuffix(groupName, displayText);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        environment.sendMessage(context.getSource(), CommandLang.groupDisplaySet(slot.label(), groupName, displayText.rawText()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearGroupDisplay(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, DisplaySlot slot) throws CommandSyntaxException {
        String groupName = requireExistingGroup(environment, context, getGroupName(context));
        try {
            if (slot == DisplaySlot.PREFIX) {
                environment.groupService().clearGroupPrefix(groupName);
            } else {
                environment.groupService().clearGroupSuffix(groupName);
            }
        } catch (RuntimeException exception) {
            throw DISPLAY_OPERATION_FAILED.create(CommandLang.displayOperationFailed(exception));
        }
        environment.sendMessage(context.getSource(), CommandLang.groupDisplayClear(slot.label(), groupName));
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
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.get();
        }

        Optional<CommandSubject> knownSubject = resolveKnownSubject(environment, context, target);
        if (knownSubject.isPresent()) {
            return knownSubject.get();
        }

        try {
            UUID subjectId = UUID.fromString(target);
            String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
            return new CommandSubject(subjectId, displayName);
        } catch (IllegalArgumentException exception) {
            throw unknownUserTargetFeedback(environment, context, target);
        }
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

    private static <S> Optional<StorageFileKind> getOptionalBackupKind(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        boolean hasBackupKind = context.getNodes().stream().anyMatch(node -> BACKUP_KIND_ARGUMENT.equals(node.getNode().getName()));
        if (!hasBackupKind) {
            return Optional.empty();
        }
        return Optional.of(getBackupKind(environment, context));
    }

    private static <S> StorageFileKind getBackupKind(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, BACKUP_KIND_ARGUMENT).toLowerCase(Locale.ROOT);
        Optional<StorageFileKind> kind = StorageFileKind.fromToken(token);
        if (kind.isEmpty()) {
            throw unknownBackupKindFeedback(environment, context, token);
        }
        return kind.get();
    }

    private static <S> ConfigEntry getConfigEntry(CommandContext<S> context) throws CommandSyntaxException {
        String key = StringArgumentType.getString(context, CONFIG_KEY_ARGUMENT);
        return findConfigEntry(key).orElseThrow(() -> unknownConfigKeyFeedback(context, key));
    }

    private static Optional<ConfigEntry> findConfigEntry(String key) {
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        return CONFIG_ENTRIES.stream().filter(entry -> entry.key().toLowerCase(Locale.ROOT).equals(normalizedKey)).findFirst();
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

    private static <S> StorageBackup requireKnownBackupFile(CommandContext<S> context, StorageFileKind kind, String backupFileName, List<StorageBackup> backups)
            throws CommandSyntaxException {
        Optional<StorageBackup> knownBackupFile = backups.stream().filter(backup -> backup.fileName().equals(backupFileName)).findFirst();
        if (knownBackupFile.isPresent()) {
            return knownBackupFile.get();
        }

        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownBackupFile(kind.token(), backupFileName));
        if (backups.isEmpty()) {
            messages.add(CommandLang.noBackupsForKind(kind.token()));
        } else {
            List<String> matches = closestMatches(backupFileName, backups.stream().map(backup -> candidate(backup.fileName())).toList());
            if (!matches.isEmpty()) {
                messages.add(CommandLang.closestBackupFiles(String.join(", ", matches)));
            }
        }
        messages.add(CommandLang.tryHeader());
        messages.add(CommandLang.suggestion(rootLiteral(context), "backup list " + kind.token()));
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

    private static <S> CommandFeedbackException unknownBackupKindFeedback(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, String token) {
        List<String> validKinds;
        try {
            validKinds = environment.storageBackupService().fileKinds().stream().map(StorageFileKind::token).sorted().toList();
        } catch (RuntimeException exception) {
            validKinds = Arrays.stream(StorageFileKind.values()).map(StorageFileKind::token).sorted().toList();
        }
        if (validKinds.isEmpty()) {
            validKinds = Arrays.stream(StorageFileKind.values()).map(StorageFileKind::token).sorted().toList();
        }

        List<CommandMessage> messages = new ArrayList<>();
        messages.add(CommandLang.unknownBackupKind(token));
        List<String> matches = closestMatches(token, validKinds.stream().map(ClutchPermsCommands::candidate).toList());
        if (matches.isEmpty()) {
            messages.add(CommandLang.validBackupKinds(String.join(", ", validKinds)));
        } else {
            messages.add(CommandLang.closestBackupKinds(String.join(", ", matches)));
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

    private static PagedRow backupRow(String rootLiteral, StorageFileKind kind, StorageBackup backup, String text) {
        return new PagedRow(text, fullCommand(rootLiteral, "backup restore " + kind.token() + " " + backup.fileName()));
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

    private record PagedRow(String text, String command) {
    }
}
