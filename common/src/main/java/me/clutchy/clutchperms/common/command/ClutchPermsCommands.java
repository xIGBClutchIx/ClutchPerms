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
import com.mojang.brigadier.arguments.BoolArgumentType;
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

import me.clutchy.clutchperms.common.GroupService;
import me.clutchy.clutchperms.common.PermissionNodes;
import me.clutchy.clutchperms.common.PermissionResolution;
import me.clutchy.clutchperms.common.PermissionValue;
import me.clutchy.clutchperms.common.SubjectMetadata;

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

    private static final String VALUE_ARGUMENT = "value";

    private static final String NAME_ARGUMENT = "name";

    private static final String GROUP_ARGUMENT = "group";

    private static final SimpleCommandExceptionType NO_PERMISSION = new SimpleCommandExceptionType(new LiteralMessage(CommandLang.ERROR_NO_PERMISSION));

    private static final SimpleCommandExceptionType OTHER_SOURCE_DENIED = new SimpleCommandExceptionType(new LiteralMessage(CommandLang.ERROR_OTHER_SOURCE_DENIED));

    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(target -> new LiteralMessage(CommandLang.unknownTarget(target)));

    private static final DynamicCommandExceptionType AMBIGUOUS_TARGET = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage(CommandLang.invalidNode(node)));

    private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

    private static final DynamicCommandExceptionType GROUP_OPERATION_FAILED = new DynamicCommandExceptionType(message -> new LiteralMessage(message.toString()));

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

        return LiteralArgumentBuilder.<S>literal(ROOT_LITERAL).executes(context -> executeAuthorized(environment, context, source -> sendCommandList(environment, context)))
                .then(statusCommand(environment)).then(reloadCommand(environment)).then(userCommand(environment)).then(groupRootCommand(environment))
                .then(usersCommand(environment));
    }

    private static <S> LiteralArgumentBuilder<S> statusCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("status").executes(context -> executeAuthorized(environment, context, source -> sendStatus(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> reloadCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("reload").executes(context -> executeAuthorized(environment, context, source -> reloadStorage(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> userCommand(ClutchPermsCommandEnvironment<S> environment) {
        RequiredArgumentBuilder<S, String> target = RequiredArgumentBuilder.<S, String>argument(TARGET_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestOnlineSubjects(environment, context.getSource(), builder));

        return LiteralArgumentBuilder.<S>literal("user").then(target.then(listCommand(environment)).then(getCommand(environment)).then(setCommand(environment))
                .then(clearCommand(environment)).then(userGroupsCommand(environment)).then(userGroupCommand(environment)).then(checkCommand(environment)));
    }

    private static <S> LiteralArgumentBuilder<S> groupRootCommand(ClutchPermsCommandEnvironment<S> environment) {
        RequiredArgumentBuilder<S, String> group = RequiredArgumentBuilder.<S, String>argument(GROUP_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestGroups(environment, builder));

        return LiteralArgumentBuilder.<S>literal("group")
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> executeAuthorized(environment, context, source -> listGroups(environment, context))))
                .then(group
                        .then(LiteralArgumentBuilder.<S>literal("create").executes(context -> executeAuthorized(environment, context, source -> createGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("delete").executes(context -> executeAuthorized(environment, context, source -> deleteGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> executeAuthorized(environment, context, source -> listGroup(environment, context))))
                        .then(LiteralArgumentBuilder.<S>literal("get")
                                .then(ClutchPermsCommands.nodeArgument(environment)
                                        .executes(context -> executeAuthorized(environment, context, source -> getGroupPermission(environment, context)))))
                        .then(LiteralArgumentBuilder.<S>literal("set")
                                .then(ClutchPermsCommands.nodeArgument(environment)
                                        .then(RequiredArgumentBuilder.<S, Boolean>argument(VALUE_ARGUMENT, BoolArgumentType.bool())
                                                .executes(context -> executeAuthorized(environment, context, source -> setGroupPermission(environment, context))))))
                        .then(LiteralArgumentBuilder.<S>literal("clear").then(ClutchPermsCommands.nodeArgument(environment)
                                .executes(context -> executeAuthorized(environment, context, source -> clearGroupPermission(environment, context))))));
    }

    private static <S> LiteralArgumentBuilder<S> usersCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("users")
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> executeAuthorized(environment, context, source -> listSubjects(environment, context))))
                .then(LiteralArgumentBuilder.<S>literal("search").then(RequiredArgumentBuilder.<S, String>argument(NAME_ARGUMENT, StringArgumentType.word())
                        .executes(context -> executeAuthorized(environment, context, source -> searchSubjects(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> listCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("list").executes(context -> executeAuthorized(environment, context, source -> listPermissions(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("get")
                .then(ClutchPermsCommands.nodeArgument(environment).executes(context -> executeAuthorized(environment, context, source -> getPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("set")
                .then(ClutchPermsCommands.nodeArgument(environment).then(RequiredArgumentBuilder.<S, Boolean>argument(VALUE_ARGUMENT, BoolArgumentType.bool())
                        .executes(context -> executeAuthorized(environment, context, source -> setPermission(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("clear")
                .then(ClutchPermsCommands.nodeArgument(environment).executes(context -> executeAuthorized(environment, context, source -> clearPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> userGroupsCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("groups").executes(context -> executeAuthorized(environment, context, source -> listSubjectGroups(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> userGroupCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("group")
                .then(LiteralArgumentBuilder.<S>literal("add")
                        .then(groupArgument(environment).executes(context -> executeAuthorized(environment, context, source -> addSubjectGroup(environment, context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove")
                        .then(groupArgument(environment).executes(context -> executeAuthorized(environment, context, source -> removeSubjectGroup(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> checkCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("check")
                .then(ClutchPermsCommands.nodeArgument(environment).executes(context -> executeAuthorized(environment, context, source -> checkPermission(environment, context))));
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(NODE_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestPermissionNodes(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> groupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(GROUP_ARGUMENT, StringArgumentType.word()).suggests((context, builder) -> suggestGroups(environment, builder));
    }

    private static <S> int executeAuthorized(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, CommandAction<S> action) throws CommandSyntaxException {
        S source = context.getSource();
        if (!canUse(environment, source)) {
            if (environment.sourceKind(source) == CommandSourceKind.OTHER) {
                throw OTHER_SOURCE_DENIED.create();
            }
            throw NO_PERMISSION.create();
        }

        return action.run(source);
    }

    private static <S> int sendCommandList(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        CommandLang.commandList(ROOT_LITERAL).forEach(message -> environment.sendMessage(context.getSource(), message));
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int sendStatus(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        CommandStatusDiagnostics diagnostics = environment.statusDiagnostics();
        environment.sendMessage(context.getSource(), STATUS_MESSAGE);
        environment.sendMessage(context.getSource(), CommandLang.statusPermissionsFile(diagnostics.permissionsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusSubjectsFile(diagnostics.subjectsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusGroupsFile(diagnostics.groupsFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownSubjects(environment.subjectMetadataService().getSubjects().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownGroups(environment.groupService().getGroups().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusRuntimeBridge(diagnostics.runtimeBridgeStatus()));
        return Command.SINGLE_SUCCESS;
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

    private static <S> boolean canUse(ClutchPermsCommandEnvironment<S> environment, S source) {
        CommandSourceKind sourceKind = environment.sourceKind(source);
        if (sourceKind == CommandSourceKind.CONSOLE) {
            return true;
        }
        if (sourceKind != CommandSourceKind.PLAYER) {
            return false;
        }

        Optional<UUID> subjectId = environment.sourceSubjectId(source);
        return subjectId.isPresent() && environment.permissionResolver().hasPermission(subjectId.get(), PermissionNodes.ADMIN);
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
        String node = getNode(context);
        PermissionValue value = BoolArgumentType.getBool(context, VALUE_ARGUMENT) ? PermissionValue.TRUE : PermissionValue.FALSE;

        environment.permissionService().setPermission(subject.id(), node, value);
        environment.sendMessage(context.getSource(), CommandLang.permissionSet(node, formatSubject(subject), value));
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

        environment.sendMessage(context.getSource(), CommandLang.permissionCheck(formatSubject(subject), node, resolution.value(), formatResolutionSource(resolution)));
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
        try {
            permissions = environment.groupService().getGroupPermissions(groupName);
            members = environment.groupService().getGroupMembers(groupName);
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
        String node = getNode(context);
        PermissionValue value = BoolArgumentType.getBool(context, VALUE_ARGUMENT) ? PermissionValue.TRUE : PermissionValue.FALSE;
        try {
            environment.groupService().setGroupPermission(groupName, node, value);
        } catch (RuntimeException exception) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(exception));
        }

        environment.sendMessage(context.getSource(), CommandLang.groupPermissionSet(node, normalizeGroupName(groupName), value));
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

    private static <S> String getNode(CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, NODE_ARGUMENT);
        if (node.trim().isEmpty()) {
            throw INVALID_NODE.create(node);
        }
        return node;
    }

    private static <S> String getGroupName(CommandContext<S> context) throws CommandSyntaxException {
        String groupName = StringArgumentType.getString(context, GROUP_ARGUMENT);
        if (groupName.trim().isEmpty()) {
            throw GROUP_OPERATION_FAILED.create(CommandLang.groupOperationFailed(new IllegalArgumentException("group name must not be blank")));
        }
        return groupName;
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

    private static <S> CompletableFuture<Suggestions> suggestPermissionNodes(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.add(PermissionNodes.ADMIN);

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

    private static String formatResolutionSource(PermissionResolution resolution) {
        return switch (resolution.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + resolution.groupName();
            case DEFAULT -> "default group";
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
}
