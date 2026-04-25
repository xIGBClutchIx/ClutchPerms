package me.clutchy.clutchperms.common.command;

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
import java.util.stream.Collectors;

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

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.permission.PermissionExplanation;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.permission.PermissionResolverCacheStats;
import me.clutchy.clutchperms.common.permission.PermissionValue;
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
     * Health line returned by the status command.
     */
    public static final String STATUS_MESSAGE = CommandLang.STATUS;

    private static final String TARGET_ARGUMENT = "target";

    private static final String NODE_ARGUMENT = "node";

    private static final String ASSIGNMENT_ARGUMENT = "assignment";

    private static final String NAME_ARGUMENT = "name";

    private static final String QUERY_ARGUMENT = "query";

    private static final String GROUP_ARGUMENT = "group";

    private static final String PARENT_ARGUMENT = "parent";

    private static final String BACKUP_KIND_ARGUMENT = "backupKind";

    private static final String BACKUP_FILE_ARGUMENT = "backupFile";

    private static final String UNKNOWN_ARGUMENT = "unknown";

    private static final SimpleCommandExceptionType NO_PERMISSION = new SimpleCommandExceptionType(new LiteralMessage(CommandLang.ERROR_NO_PERMISSION));

    private static final SimpleCommandExceptionType OTHER_SOURCE_DENIED = new SimpleCommandExceptionType(new LiteralMessage(CommandLang.ERROR_OTHER_SOURCE_DENIED));

    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(target -> new LiteralMessage(CommandLang.unknownTarget(target).plainText()));

    private static final DynamicCommandExceptionType AMBIGUOUS_TARGET = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage(CommandLang.invalidNode(node).plainText()));

    private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(value -> new LiteralMessage(CommandLang.invalidValue(value).plainText()));

    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType VALIDATE_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType GROUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType NODE_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType BACKUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    /**
     * Creates the root ClutchPerms command node for a platform source type.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment) {
        return builder(environment).build();
    }

    /**
     * Creates the root ClutchPerms command builder for platform dispatchers that register builders directly.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment) {
        Objects.requireNonNull(environment, "environment");

        return LiteralArgumentBuilder.<S>literal(ROOT_LITERAL)
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_HELP, source -> sendCommandList(environment, context)))
                .then(statusCommand(environment)).then(reloadCommand(environment)).then(validateCommand(environment)).then(backupCommand(environment))
                .then(userCommand(environment)).then(groupRootCommand(environment)).then(usersCommand(environment)).then(nodesCommand(environment));
    }

    private static <S> LiteralArgumentBuilder<S> statusCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("status")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_STATUS, source -> sendStatus(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> reloadCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("reload")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_RELOAD, source -> reloadStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> validateCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("validate")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_VALIDATE, source -> validateStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> backupCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("backup")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_LIST, source -> sendBackupUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_LIST, source -> listBackups(environment, context)))
                        .then(backupKindArgument(environment)
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_LIST, source -> listBackups(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("restore").executes(
                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_RESTORE, source -> sendBackupRestoreKindUsage(environment, context)))
                        .then(backupKindArgument(environment)
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_RESTORE,
                                        source -> sendBackupRestoreFileUsage(environment, context)))
                                .then(backupFileArgument(environment).executes(
                                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_RESTORE, source -> restoreBackup(environment, context))))))
                .then(ClutchPermsCommands.<S>unknownArgument()
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_BACKUP_LIST, source -> sendUnknownBackupUsage(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> userCommand(ClutchPermsCommandEnvironment<S> environment) {
        RequiredArgumentBuilder<S, String> target = RequiredArgumentBuilder.<S, String>argument(TARGET_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestOnlineSubjects(environment, context.getSource(), builder));

        return LiteralArgumentBuilder.<S>literal("user")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_LIST, source -> sendUserRootUsage(environment, context)))
                .then(target.then(listCommand(environment)).then(getCommand(environment)).then(setCommand(environment)).then(clearCommand(environment))
                        .then(userGroupsCommand(environment)).then(userGroupCommand(environment)).then(checkCommand(environment)).then(explainCommand(environment))
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_LIST, source -> sendUserTargetUsage(environment, context)))
                        .then(ClutchPermsCommands.<S>unknownArgument().executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_LIST, source -> sendUnknownUserTargetUsage(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> groupRootCommand(ClutchPermsCommandEnvironment<S> environment) {
        RequiredArgumentBuilder<S, String> group = RequiredArgumentBuilder.<S, String>argument(GROUP_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestGroups(environment, builder));

        return LiteralArgumentBuilder.<S>literal("group")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_LIST, source -> sendGroupRootUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_LIST, source -> listGroups(environment, context))))
                .then(group.executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_VIEW, source -> sendGroupTargetUsage(environment, context)))
                        .then(LiteralArgumentBuilder.<S>literal("create")
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_CREATE, source -> createGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("delete")
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_DELETE, source -> deleteGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("list")
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_VIEW, source -> listGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("parents").executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENTS, source -> listGroupParents(environment, context))))
                        .then(groupParentCommand(environment))
                        .then(LiteralArgumentBuilder.<S>literal("get")
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_GET, source -> sendGroupGetUsage(environment, context)))
                                .then(ClutchPermsCommands.nodeArgument(environment).executes(
                                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_GET, source -> getGroupPermission(environment, context)))))
                        .then(LiteralArgumentBuilder.<S>literal("set")
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_SET, source -> sendGroupSetUsage(environment, context)))
                                .then(ClutchPermsCommands.assignmentArgument(environment).executes(
                                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_SET, source -> setGroupPermission(environment, context)))))
                        .then(LiteralArgumentBuilder.<S>literal("clear")
                                .executes(
                                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_CLEAR, source -> sendGroupClearUsage(environment, context)))
                                .then(ClutchPermsCommands.nodeArgument(environment)
                                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_CLEAR,
                                                source -> clearGroupPermission(environment, context)))))
                        .then(ClutchPermsCommands.<S>unknownArgument().executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_VIEW,
                                source -> sendUnknownGroupTargetUsage(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> usersCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("users")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USERS_LIST, source -> sendUsersUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USERS_LIST, source -> listSubjects(environment, context))))
                .then(LiteralArgumentBuilder.<S>literal("search")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USERS_SEARCH, source -> sendUsersSearchUsage(environment, context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(NAME_ARGUMENT, StringArgumentType.word())
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USERS_SEARCH, source -> searchSubjects(environment, context)))))
                .then(ClutchPermsCommands.<S>unknownArgument()
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USERS_LIST, source -> sendUnknownUsersUsage(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> nodesCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("nodes")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_LIST, source -> sendNodesUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("list")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_LIST, source -> listKnownNodes(environment, context))))
                .then(LiteralArgumentBuilder.<S>literal("search")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_SEARCH, source -> sendNodesSearchUsage(environment, context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(QUERY_ARGUMENT, StringArgumentType.word()).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_SEARCH, source -> searchKnownNodes(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("add")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_ADD, source -> sendNodesAddUsage(environment, context)))
                        .then(ClutchPermsCommands.nodeArgument(environment)
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_ADD, source -> addKnownNode(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_REMOVE, source -> sendNodesRemoveUsage(environment, context)))
                        .then(ClutchPermsCommands.nodeArgument(environment)
                                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_REMOVE, source -> removeKnownNode(environment, context)))))
                .then(ClutchPermsCommands.<S>unknownArgument()
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_NODES_LIST, source -> sendUnknownNodesUsage(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> listCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("list")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_LIST, source -> listPermissions(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("get")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GET, source -> sendUserGetUsage(environment, context)))
                .then(ClutchPermsCommands.nodeArgument(environment)
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GET, source -> getPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("set")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_SET, source -> sendUserSetUsage(environment, context)))
                .then(ClutchPermsCommands.assignmentArgument(environment)
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_SET, source -> setPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("clear")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_CLEAR, source -> sendUserClearUsage(environment, context)))
                .then(ClutchPermsCommands.nodeArgument(environment)
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_CLEAR, source -> clearPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> userGroupsCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("groups")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUPS, source -> listSubjectGroups(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> userGroupCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("group")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUPS, source -> sendUserGroupUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("add")
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUP_ADD, source -> sendUserGroupAddUsage(environment, context)))
                        .then(groupArgument(environment).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUP_ADD, source -> addSubjectGroup(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove").executes(
                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, source -> sendUserGroupRemoveUsage(environment, context)))
                        .then(groupArgument(environment).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, source -> removeSubjectGroup(environment, context)))))
                .then(ClutchPermsCommands.<S>unknownArgument().executes(
                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_GROUPS, source -> sendUnknownUserGroupUsage(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> groupParentCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("parent")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENTS, source -> sendGroupParentUsage(environment, context)))
                .then(LiteralArgumentBuilder.<S>literal("add")
                        .executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, source -> sendGroupParentAddUsage(environment, context)))
                        .then(parentGroupArgument(environment).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, source -> addGroupParent(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove").executes(
                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, source -> sendGroupParentRemoveUsage(environment, context)))
                        .then(parentGroupArgument(environment).executes(
                                context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, source -> removeGroupParent(environment, context)))))
                .then(ClutchPermsCommands.<S>unknownArgument().executes(
                        context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_GROUP_PARENTS, source -> sendUnknownGroupParentUsage(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> checkCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("check")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_CHECK, source -> sendUserCheckUsage(environment, context)))
                .then(ClutchPermsCommands.nodeArgument(environment)
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_CHECK, source -> checkPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> explainCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("explain")
                .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_EXPLAIN, source -> sendUserExplainUsage(environment, context)))
                .then(ClutchPermsCommands.nodeArgument(environment)
                        .executes(context -> executeAuthorized(environment, context, PermissionNodes.ADMIN_USER_EXPLAIN, source -> explainPermission(environment, context))));
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(NODE_ARGUMENT, StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestPermissionNodes(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> assignmentArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(ASSIGNMENT_ARGUMENT, StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestPermissionAssignment(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> groupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(GROUP_ARGUMENT, StringArgumentType.word()).suggests((context, builder) -> suggestGroups(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> parentGroupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(PARENT_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestParentGroups(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> backupKindArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(BACKUP_KIND_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestBackupKinds(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> backupFileArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(BACKUP_FILE_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestBackupFiles(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> unknownArgument() {
        return RequiredArgumentBuilder.argument(UNKNOWN_ARGUMENT, StringArgumentType.greedyString());
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
        } catch (CommandSyntaxException exception) {
            environment.sendMessage(source, CommandLang.error(exception.getRawMessage().getString()));
            return 0;
        }
    }

    private static <S> int sendCommandList(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        CommandLang.commandList(ROOT_LITERAL).forEach(message -> environment.sendMessage(context.getSource(), message));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int sendStatus(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        CommandStatusDiagnostics diagnostics = environment.statusDiagnostics();
        environment.sendMessage(context.getSource(), CommandLang.status());
        environment.sendMessage(context.getSource(), CommandLang.statusPermissionsFile(diagnostics.permissionsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusSubjectsFile(diagnostics.subjectsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusGroupsFile(diagnostics.groupsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusNodesFile(diagnostics.nodesFile()));
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

    private static <S> int sendBackupRestoreKindUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        return sendUsage(environment, context, "Missing backup file kind.", "Choose permissions, subjects, groups, or nodes.",
                List.of("backup restore <permissions|subjects|groups|nodes> <backup-file>"));
    }

    private static <S> int sendBackupRestoreFileUsage(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageFileKind kind = getBackupKind(context);
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
                List.of("nodes add <node>", "nodes add <node> <description>"));
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
        usages.forEach(usage -> environment.sendMessage(source, CommandLang.usage(ROOT_LITERAL, usage)));
        return 0;
    }

    private static List<String> backupUsages() {
        return List.of("backup list", "backup list <permissions|subjects|groups|nodes>", "backup restore <permissions|subjects|groups|nodes> <backup-file>");
    }

    private static List<String> userRootUsages() {
        return List.of("user <target> list", "user <target> get <node>", "user <target> set <node> <true|false>", "user <target> clear <node>", "user <target> groups",
                "user <target> group add <group>", "user <target> group remove <group>", "user <target> check <node>", "user <target> explain <node>");
    }

    private static List<String> userTargetUsages(String target) {
        return List.of("user " + target + " list", "user " + target + " get <node>", "user " + target + " set <node> <true|false>", "user " + target + " clear <node>",
                "user " + target + " groups", "user " + target + " group add <group>", "user " + target + " group remove <group>", "user " + target + " check <node>",
                "user " + target + " explain <node>");
    }

    private static List<String> groupRootUsages() {
        return List.of("group list", "group <group> create", "group <group> delete", "group <group> list", "group <group> get <node>", "group <group> set <node> <true|false>",
                "group <group> clear <node>", "group <group> parents", "group <group> parent add <parent>", "group <group> parent remove <parent>");
    }

    private static List<String> groupTargetUsages(String group) {
        return List.of("group " + group + " create", "group " + group + " delete", "group " + group + " list", "group " + group + " get <node>",
                "group " + group + " set <node> <true|false>", "group " + group + " clear <node>", "group " + group + " parents", "group " + group + " parent add <parent>",
                "group " + group + " parent remove <parent>");
    }

    private static List<String> usersUsages() {
        return List.of("users list", "users search <name>");
    }

    private static List<String> nodesUsages() {
        return List.of("nodes list", "nodes search <query>", "nodes add <node>", "nodes add <node> <description>", "nodes remove <node>");
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

    private static <S> int listBackups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageBackupService backupService = environment.storageBackupService();
        Optional<StorageFileKind> requestedKind = getOptionalBackupKind(context);
        try {
            if (requestedKind.isPresent()) {
                sendBackupList(environment, context, requestedKind.get(), backupService.listBackups(requestedKind.get()));
                return Command.SINGLE_SUCCESS;
            }

            Map<StorageFileKind, List<StorageBackup>> backups = backupService.listBackups();
            boolean hasBackups = backups.values().stream().anyMatch(list -> !list.isEmpty());
            if (!hasBackups) {
                environment.sendMessage(context.getSource(), CommandLang.backupsEmpty());
                return Command.SINGLE_SUCCESS;
            }
            backups.forEach((kind, kindBackups) -> {
                if (!kindBackups.isEmpty()) {
                    sendBackupList(environment, context, kind, kindBackups);
                }
            });
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int restoreBackup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        StorageFileKind kind = getBackupKind(context);
        String backupFileName = StringArgumentType.getString(context, BACKUP_FILE_ARGUMENT);
        try {
            environment.storageBackupService().restoreBackup(kind, backupFileName, () -> {
                environment.reloadStorage();
                environment.refreshRuntimePermissions();
            });
        } catch (RuntimeException exception) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.backupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.backupRestored(kind.token(), backupFileName));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> void sendBackupList(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, StorageFileKind kind, List<StorageBackup> backups) {
        if (backups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.backupsEmpty(kind.token()));
            return;
        }

        String backupFiles = backups.stream().map(StorageBackup::fileName).collect(Collectors.joining(", "));
        environment.sendMessage(context.getSource(), CommandLang.backupsList(kind.token(), backupFiles));
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
        if (permissions.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.permissionsEmpty(formatSubject(subject)));
            return Command.SINGLE_SUCCESS;
        }

        String assignments = permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue().name())
                .collect(Collectors.joining(", "));
        environment.sendMessage(context.getSource(), CommandLang.permissionsList(formatSubject(subject), assignments));
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

        environment.permissionService().setPermission(subject.id(), assignment.node(), assignment.value());
        environment.sendMessage(context.getSource(), CommandLang.permissionSet(assignment.node(), formatSubject(subject), assignment.value()));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);

        environment.permissionService().clearPermission(subject.id(), node);
        environment.sendMessage(context.getSource(), CommandLang.permissionClear(node, formatSubject(subject)));
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

        environment.sendMessage(context.getSource(), CommandLang.userGroupsList(formatSubject(subject), String.join(", ", groups)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int addSubjectGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String groupName = getGroupName(context);
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
        String groupName = getGroupName(context);
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

    private static <S> int listGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        Set<String> groups = environment.groupService().getGroups();
        if (groups.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupsEmpty());
            return Command.SINGLE_SUCCESS;
        }

        environment.sendMessage(context.getSource(), CommandLang.groupsList(String.join(", ", groups)));
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
        String groupName = getGroupName(context);
        try {
            environment.groupService().deleteGroup(groupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupDeleted(normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroup(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
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

        if (permissions.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsEmpty(normalizedGroupName));
        } else {
            String assignments = permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue().name())
                    .collect(Collectors.joining(", "));
            environment.sendMessage(context.getSource(), CommandLang.groupPermissionsList(normalizedGroupName, assignments));
        }

        if (!parents.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupParentsList(normalizedGroupName, String.join(", ", parents)));
        }

        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            environment.sendMessage(context.getSource(), CommandLang.groupDefaultImplicit());
        } else if (members.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.groupMembersEmpty(normalizedGroupName));
        } else {
            String memberList = members.stream().map(subjectId -> formatSubject(subjectId, environment)).collect(Collectors.joining(", "));
            environment.sendMessage(context.getSource(), CommandLang.groupMembersList(normalizedGroupName, memberList));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listGroupParents(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
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
            environment.sendMessage(context.getSource(), CommandLang.groupParentsList(normalizedGroupName, String.join(", ", parents)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int addGroupParent(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
        String parentGroupName = getParentGroupName(context);
        try {
            environment.groupService().addGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupParentAdded(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int removeGroupParent(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
        String parentGroupName = getParentGroupName(context);
        try {
            environment.groupService().removeGroupParent(groupName, parentGroupName);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupParentRemoved(normalizeGroupName(groupName), normalizeGroupName(parentGroupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getGroupPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String groupName = getGroupName(context);
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
        String groupName = getGroupName(context);
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
        String groupName = getGroupName(context);
        String node = getNode(context);
        try {
            environment.groupService().clearGroupPermission(groupName, node);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupPermissionClear(node, normalizeGroupName(groupName)));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listSubjects(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        Map<UUID, SubjectMetadata> subjects = environment.subjectMetadataService().getSubjects();
        if (subjects.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String subjectList = subjects.values().stream()
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(ClutchPermsCommands::formatSubjectMetadata).collect(Collectors.joining(", "));
        environment.sendMessage(context.getSource(), CommandLang.usersList(subjectList));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int searchSubjects(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String query = StringArgumentType.getString(context, NAME_ARGUMENT).trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        String matches = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(ClutchPermsCommands::formatSubjectMetadata).collect(Collectors.joining(", "));

        if (matches.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        environment.sendMessage(context.getSource(), CommandLang.usersSearchMatches(matches));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int listKnownNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        List<KnownPermissionNode> nodes = environment.permissionNodeRegistry().getKnownNodes().stream().sorted(Comparator.comparing(KnownPermissionNode::node)).toList();
        if (nodes.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesEmpty());
            return Command.SINGLE_SUCCESS;
        }

        environment.sendMessage(context.getSource(), CommandLang.nodesList(nodes.stream().map(ClutchPermsCommands::formatKnownNode).collect(Collectors.joining(", "))));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int searchKnownNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
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

        environment.sendMessage(context.getSource(), CommandLang.nodesSearchMatches(nodes.stream().map(ClutchPermsCommands::formatKnownNode).collect(Collectors.joining(", "))));
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
            environment.manualPermissionNodeRegistry().removeNode(normalizedNode);
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
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

        Optional<CommandSubject> knownSubject = resolveKnownSubject(environment, target);
        if (knownSubject.isPresent()) {
            return knownSubject.get();
        }

        try {
            UUID subjectId = UUID.fromString(target);
            String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
            return new CommandSubject(subjectId, displayName);
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_TARGET.create(target);
        }
    }

    private static <S> Optional<CommandSubject> resolveKnownSubject(ClutchPermsCommandEnvironment<S> environment, String target) throws CommandSyntaxException {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        List<SubjectMetadata> matches = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).equals(normalizedTarget))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId)).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            String matchedSubjects = matches.stream().map(ClutchPermsCommands::formatSubjectMetadata).collect(Collectors.joining(", "));
            throw AMBIGUOUS_TARGET.create(CommandLang.ambiguousKnownUser(target, matchedSubjects));
        }

        SubjectMetadata subject = matches.getFirst();
        return Optional.of(new CommandSubject(subject.subjectId(), subject.lastKnownName()));
    }

    private static <S> Optional<StorageFileKind> getOptionalBackupKind(CommandContext<S> context) throws CommandSyntaxException {
        boolean hasBackupKind = context.getNodes().stream().anyMatch(node -> BACKUP_KIND_ARGUMENT.equals(node.getNode().getName()));
        if (!hasBackupKind) {
            return Optional.empty();
        }
        return Optional.of(getBackupKind(context));
    }

    private static <S> StorageFileKind getBackupKind(CommandContext<S> context) throws CommandSyntaxException {
        String token = StringArgumentType.getString(context, BACKUP_KIND_ARGUMENT).toLowerCase(Locale.ROOT);
        Optional<StorageFileKind> kind = StorageFileKind.fromToken(token);
        if (kind.isEmpty()) {
            throw BACKUP_OPERATION_FAILED.create(CommandLang.unknownBackupKind(token));
        }
        return kind.get();
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

    private static <S> CompletableFuture<Suggestions> suggestOnlineSubjects(ClutchPermsCommandEnvironment<S> environment, S source, SuggestionsBuilder builder) {
        environment.onlineSubjectNames(source).stream().sorted(Comparator.naturalOrder()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestGroups(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestParentGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String normalizedGroupName;
        Set<String> existingParents;
        try {
            normalizedGroupName = normalizeGroupName(StringArgumentType.getString(context, GROUP_ARGUMENT));
            existingParents = environment.groupService().getGroupParents(normalizedGroupName);
        } catch (IllegalArgumentException exception) {
            normalizedGroupName = "";
            existingParents = Set.of();
        }

        String currentGroupName = normalizedGroupName;
        Set<String> currentParents = existingParents;
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> !group.equals(currentGroupName))
                .filter(group -> !currentParents.contains(group)).filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestBackupKinds(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        try {
            environment.storageBackupService().fileKinds().stream().map(StorageFileKind::token).sorted(Comparator.naturalOrder()).filter(token -> token.startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (RuntimeException exception) {
            // Backup suggestions are best-effort when a platform cannot expose storage paths yet.
        }
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestBackupFiles(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        try {
            StorageFileKind kind = getBackupKind(context);
            environment.storageBackupService().listBackups(kind).stream().map(StorageBackup::fileName).filter(fileName -> fileName.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (CommandSyntaxException | RuntimeException exception) {
            // Backup filename suggestions depend on a valid file kind and readable backup directory.
        }
        return builder.buildFuture();
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

    private static String formatResolutionSource(PermissionResolution resolution) {
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

    private record PermissionAssignment(String node, PermissionValue value) {
    }
}
